package com.btmicpro.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Motor de captura de áudio com processamento digital de sinal (DSP) em tempo real,
 * projetado para isolar a voz humana e eliminar ruídos extremos (vento na moto, ventilador, trânsito).
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

    // Configurações de Áudio
    private val sampleRate = 48000 // 48 kHz para máxima fidelidade
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Estado do Filtro Passa-Alta Anti-Vento (120 Hz)
    private var hpFilterPrevX = 0f
    private var hpFilterPrevY = 0f

    /**
     * Inicia a gravação de áudio com cancelamento e tratamento de sinal ativo.
     *
     * @param deviceName Nome do microfone em uso (ex: "Fone Bluetooth").
     * @param noiseDenoiseLevel Nível de agressividade da redução de ruído (0.0f a 1.0f).
     */
    @SuppressLint("MissingPermission")
    fun startRecording(deviceName: String = "Fone Bluetooth", noiseDenoiseLevel: Float = 0.85f, rawAudioMode: Boolean = false) {
        if (_recordingState.value is RecordingState.Recording) {
            Log.w(TAG, "Gravação já está em andamento.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, 4096)

        try {
            val audioSource = if (rawAudioMode) {
                // API 24+ allows UNPROCESSED to bypass native hardware DSP.
                // Using 9 directly which is MediaRecorder.AudioSource.UNPROCESSED
                9 
            } else {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
            
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Não foi possível inicializar o gravador de áudio.")
                return
            }

            // Anexa efeitos de hardware (NoiseSuppressor e AGC nativos) APENAS se o raw mode não estiver ativo
            if (!rawAudioMode) {
                val sessionId = audioRecord?.audioSessionId ?: 0
                audioEffectController.attachToSession(sessionId)
            } else {
                Log.d(TAG, "RAW AUDIO MODE ativado: efeitos de hardware do sistema desabilitados.")
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Captura de áudio iniciada a $sampleRate Hz.")

            val startTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Recording(0, 0f, deviceName)

            // Inicia o loop assíncrono de leitura e processamento DSP
            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val pcmBuffer = ShortArray(bufferSize / 2)
                val rawAudioStream = ByteArrayOutputStream()

                // Reset dos estados dos filtros DSP
                hpFilterPrevX = 0f
                hpFilterPrevY = 0f

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                    if (readCount > 0) {
                        // 1. Aplicação de DSP em tempo real no buffer PCM
                        processDspBuffer(pcmBuffer, readCount, noiseDenoiseLevel)

                        // 2. Cálculo da amplitude de pico para o medidor visual (VU Meter)
                        var maxPeak = 0
                        for (i in 0 until readCount) {
                            val sample = abs(pcmBuffer[i].toInt())
                            if (sample > maxPeak) maxPeak = sample
                        }
                        val normalizedPeak = (maxPeak / 32767f).coerceIn(0f, 1f)

                        // 3. Conversão de ShortArray para bytes em Little Endian
                        val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until readCount) {
                            byteBuffer.putShort(pcmBuffer[i])
                        }
                        rawAudioStream.write(byteBuffer.array())

                        // 4. Atualização periódica do estado da gravação
                        val elapsedMs = System.currentTimeMillis() - startTime
                        _recordingState.value = RecordingState.Recording(
                            durationMs = elapsedMs,
                            amplitude = normalizedPeak,
                            deviceName = deviceName
                        )
                    }
                }

                // Ao encerrar o loop, salva o arquivo de áudio tratado
                try {
                    val pcmBytes = rawAudioStream.toByteArray()
                    if (pcmBytes.isNotEmpty()) {
                        val durationMs = System.currentTimeMillis() - startTime
                        val savedFile = audioFileManager.savePcmAsWav(
                            pcmData = pcmBytes,
                            sampleRate = sampleRate,
                            channels = 1,
                            durationMs = durationMs
                        )
                        _recordingState.value = RecordingState.Finished(
                            filePath = savedFile.absolutePath,
                            durationMs = durationMs
                        )
                        Log.d(TAG, "Áudio gravado e processado com sucesso: ${savedFile.absolutePath}")
                    } else {
                        _recordingState.value = RecordingState.Idle
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao salvar arquivo de áudio", e)
                    _recordingState.value = RecordingState.Error("Falha ao salvar áudio: ${e.localizedMessage}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar motor de captura", e)
            _recordingState.value = RecordingState.Error("Erro ao iniciar gravação: ${e.localizedMessage}")
        }
    }

    /**
     * Aplica cadeia de processamento digital de sinal (DSP) no buffer de áudio:
     * - Filtro Passa-Alta IIR (120 Hz) para remoção de vento e rumble de moto.
     * - Noise Gate dinâmico para silenciar ruído residual em pausas.
     * - Compressor suave com ganho compensado para inteligibilidade da voz.
     */
    private fun processDspBuffer(buffer: ShortArray, length: Int, denoiseIntensity: Float) {
        // Coeficiente do filtro passa-alta em 48kHz para corte em ~120Hz
        // RC = 1 / (2 * pi * 120), alpha = RC / (RC + dt)
        val alpha = 0.9845f

        // Limiar do Noise Gate proporcional à intensidade escolhida pelo usuário
        val noiseGateThreshold = (180 + (denoiseIntensity * 400)).toInt() // Faixa ajustável
        val gainMultiplier = 1.0f + (denoiseIntensity * 0.4f) // Compensação de ganho para voz

        for (i in 0 until length) {
            val inputSample = buffer[i].toFloat()

            // 1. Filtro Passa-Alta IIR (remove ruído de vento abaixo de 120 Hz)
            val hpOutput = alpha * (hpFilterPrevY + inputSample - hpFilterPrevX)
            hpFilterPrevX = inputSample
            hpFilterPrevY = hpOutput

            var sample = hpOutput

            // 2. Noise Gate Suave (atenua ruídos de fundo abaixo do limiar)
            val absSample = abs(sample)
            if (absSample < noiseGateThreshold) {
                val reductionFactor = (absSample / noiseGateThreshold) * (1f - denoiseIntensity * 0.7f)
                sample *= reductionFactor
            } else {
                // 3. Aplicação de ganho compensatório para manter a voz clara
                sample *= gainMultiplier
            }

            // 4. Clamping para evitar saturação/distorção do áudio 16-bit
            val clamped = sample.coerceIn(-32768f, 32767f).toInt().toShort()
            buffer[i] = clamped
        }
    }

    /**
     * Interrompe a gravação e finaliza o processamento do arquivo.
     */
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
            Log.e(TAG, "Erro ao parar captura de áudio", e)
        }
    }

    /**
     * Reseta o estado para Idle.
     */
    fun resetState() {
        _recordingState.value = RecordingState.Idle
    }

    companion object {
        private const val TAG = "AudioCaptureEngine"
    }
}
