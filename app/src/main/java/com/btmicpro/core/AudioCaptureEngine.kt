package com.btmicpro.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Motor de captura de áudio com CleanVoice DSP multicamada.
 * Utiliza o pipeline profissional Butterworth 4ª ordem + Expansor suave RMS + DRC + Limiter,
 * garantindo gravação com zero cortes e máxima redução de vento para motos.
 */
class AudioCaptureEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val audioFileManager: AudioFileManager
) {
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val audioEffectController = AudioEffectController()
    private val cleanVoiceDsp = CleanVoiceDsp(16000)

    private var effectiveSampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private fun isScoActive(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.isBluetoothScoOn) return true
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                 it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) { false }
    }

    private fun getBluetoothInputDevice(): AudioDeviceInfo? {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                 it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) { null }
    }

    fun setPreset(preset: RiderAudioPreset) {
        cleanVoiceDsp.setPreset(preset)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        deviceName: String = "Fone Bluetooth",
        noiseDenoiseLevel: Float = 0.85f,
        rawAudioMode: Boolean = false,
        forceScoRate: Boolean = false,
        preset: RiderAudioPreset = RiderAudioPreset.NORMAL
    ) {
        if (_recordingState.value is RecordingState.Recording) {
            Log.w(TAG, "Gravação já está em andamento.")
            return
        }
        cleanVoiceDsp.setPreset(preset)

        val scoActive = forceScoRate || isScoActive()
        effectiveSampleRate = if (scoActive) 16000 else 48000
        Log.d(TAG, "Iniciando gravação: SCO=$scoActive Taxa=${effectiveSampleRate}Hz RawMode=$rawAudioMode")

        val sampleRate = effectiveSampleRate
        cleanVoiceDsp.configureFilters(sampleRate)

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, if (scoActive) 2048 else 4096)

        try {
            // No Android, VOICE_RECOGNITION oferece sinal limpo sem compressões destrutivas de chamada
            val audioSource = if (rawAudioMode) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            
            audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Não foi possível inicializar o gravador.")
                return
            }

            // Direciona explicitamente para o fone Bluetooth se disponível
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val btDevice = getBluetoothInputDevice()
                if (btDevice != null) {
                    audioRecord?.preferredDevice = btDevice
                    Log.d(TAG, "Captura vinculada ao dispositivo: ${btDevice.productName}")
                }
            }

            if (!rawAudioMode) {
                val sessionId = audioRecord?.audioSessionId ?: 0
                audioEffectController.attachToSession(sessionId)
            } else {
                Log.d(TAG, "Modo RAW puro ativado (Bypass DSP)")
            }

            audioRecord?.startRecording()
            Log.d(TAG, "AudioRecord iniciado a $sampleRate Hz")

            val startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Recording(0, 0f, deviceName)

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                // Buffer de leitura (bloco de 10ms a 20ms)
                val pcmBuffer = ShortArray(bufferSize / 2)
                val tempPcmFile = audioFileManager.createTempPcmFile()

                try {
                    java.io.FileOutputStream(tempPcmFile).use { fos ->
                        while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                            if (readCount > 0) {
                                // Processamento profissional CleanVoice DSP (sem gating destrutivo)
                                cleanVoiceDsp.process(pcmBuffer, readCount, noiseDenoiseLevel, bypassDsp = rawAudioMode)

                                var maxPeak = 0
                                for (i in 0 until readCount) {
                                    val sample = abs(pcmBuffer[i].toInt())
                                    if (sample > maxPeak) maxPeak = sample
                                }
                                val normalizedPeak = (maxPeak / 32767f).coerceIn(0f, 1f)

                                val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                                for (i in 0 until readCount) byteBuffer.putShort(pcmBuffer[i])
                                fos.write(byteBuffer.array())

                                val elapsedMs = System.currentTimeMillis() - startTime
                                _recordingState.value = RecordingState.Recording(elapsedMs, normalizedPeak, deviceName)
                            }
                        }
                    }

                    if (tempPcmFile.exists() && tempPcmFile.length() > 0) {
                        val durationMs = System.currentTimeMillis() - startTime
                        val savedFile = audioFileManager.savePcmFileAsWav(tempPcmFile, sampleRate, 1, durationMs)
                        _recordingState.value = RecordingState.Finished(savedFile.absolutePath, durationMs)
                        Log.d(TAG, "Áudio gravado e processado com sucesso: ${savedFile.absolutePath}")
                    } else {
                        try { tempPcmFile.delete() } catch (ignored: Exception) {}
                        _recordingState.value = RecordingState.Idle
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao gravar ou processar áudio", e)
                    try { tempPcmFile.delete() } catch (ignored: Exception) {}
                    _recordingState.value = RecordingState.Error("Falha ao salvar: ${e.localizedMessage}")
                } finally {
                    try {
                        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord?.stop()
                        }
                        audioRecord?.release()
                        audioRecord = null
                        audioEffectController.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao liberar recursos de áudio", e)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar captura", e)
            _recordingState.value = RecordingState.Error("Erro: ${e.localizedMessage}")
            try {
                audioRecord?.release()
                audioRecord = null
                audioEffectController.release()
            } catch (ignored: Exception) {}
        }
    }

    fun stopRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioEffectController.release()
            recordingJob?.cancel()
            recordingJob = null
            Log.d(TAG, "Captura de áudio interrompida.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar captura", e)
        }
    }

    fun resetState() {
        _recordingState.value = RecordingState.Idle
    }

    companion object {
        private const val TAG = "AudioCaptureEngine"
    }
}
