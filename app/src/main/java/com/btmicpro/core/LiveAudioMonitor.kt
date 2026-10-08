package com.btmicpro.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * LiveAudioMonitor — Motor de escuta e monitoramento em tempo real (Pass-Through / Hear-Through).
 * Tecnologia inspirada no Noise Uncanceller / Safe Headphones:
 * Captura o microfone do capacete/fone, passa pelo CleanVoiceDsp em baixíssima latência
 * e reproduz imediatamente nos fones de ouvido para que o motociclista verifique a clareza
 * da sua voz e a eficácia da redução do ruído de vento em tempo real.
 *
 * IMPORTANTE (BUG-01): Usa AudioSource.MIC em vez de VOICE_COMMUNICATION para
 * ter prioridade menor que o WhatsApp na política de captura concorrente do Android.
 * Assim, o WhatsApp consegue capturar o microfone mesmo com o monitor ativo.
 */
class LiveAudioMonitor(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var monitorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var activeSampleRate = 16000
    private val cleanVoiceDsp = CleanVoiceDsp(16000)
    private val audioEffectController = AudioEffectController()

    // BUG-02: ID de sessão monotônico para proteger o cleanup contra race conditions.
    // Cada chamada a startMonitoring() incrementa o sessionId.
    // O finally da coroutine só limpa recursos se o sessionId bater com o da sessão que iniciou.
    private val sessionIdGenerator = AtomicInteger(0)

    @Volatile
    private var returnVolume: Float = 0.0f

    @Volatile
    private var currentAudioModeProfile: AudioModeProfile = AudioModeProfile.STANDARD

    // Número máximo de erros consecutivos de leitura antes de parar automaticamente (BUG-09)
    private val maxConsecutiveReadErrors = 10

    @Synchronized
    fun setAudioModeProfile(profile: AudioModeProfile) {
        currentAudioModeProfile = profile
        if (_isMonitoring.value && returnVolume > 0.0f) {
            releaseAudioTrack()
            initAudioTrack()
        }
    }

    fun getAudioModeProfile(): AudioModeProfile = currentAudioModeProfile

    @Synchronized
    fun setReturnVolume(volume: Float) {
        returnVolume = volume.coerceIn(0.0f, 1.0f)
        if (returnVolume > 0.0f) {
            if (_isMonitoring.value && audioTrack == null) {
                initAudioTrack()
            }
            try {
                audioTrack?.setVolume(returnVolume)
            } catch (ignored: Exception) {}
        } else {
            // Volume 0% = Mudo total: Libera o AudioTrack para NUNCA ocupar a saída de áudio dos fones
            releaseAudioTrack()
        }
    }

    fun getReturnVolume(): Float = returnVolume

    @Synchronized
    private fun initAudioTrack() {
        if (audioTrack != null) return
        try {
            val sampleRate = activeSampleRate
            val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioEncoding)
            check(minBufOut > 0) { "Saída PCM de $sampleRate Hz indisponível" }
            val bufferSize = max(sampleRate / 50 * 2, minBufOut)

            val attributesBuilder = AudioAttributes.Builder()
            when (currentAudioModeProfile) {
                AudioModeProfile.STANDARD -> {
                    attributesBuilder
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                }
                AudioModeProfile.X_PRO_TEST, AudioModeProfile.MODE_2,
                AudioModeProfile.MODE_6, AudioModeProfile.MODE_7, AudioModeProfile.MODE_8,
                AudioModeProfile.MODE_9, AudioModeProfile.MODE_10 -> {
                    attributesBuilder
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                }
                AudioModeProfile.MODE_3 -> {
                    attributesBuilder
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                }
                AudioModeProfile.MODE_4 -> {
                    attributesBuilder
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                }
                AudioModeProfile.MODE_5 -> {
                    attributesBuilder
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                }
            }
            val attributes = attributesBuilder.build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfigOut)
                .setEncoding(audioEncoding)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("AudioTrack não inicializou")
            }
            audioTrack = track

            if (currentAudioModeProfile != AudioModeProfile.STANDARD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val btOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.communicationDevice?.takeIf(CommunicationDeviceManager::isVoiceBluetooth) ?: outputs.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                } else {
                    outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                }
                if (btOutput != null) {
                    val accepted = track.setPreferredDevice(btOutput)
                    if (currentAudioModeProfile == AudioModeProfile.X_PRO_TEST) {
                        check(accepted) { "Android recusou a saída Bluetooth do teste X Pro" }
                    }
                    AppLogger.i(TAG, "Saída Bluetooth [${currentAudioModeProfile.title}] roteada para: ${btOutput.productName}; aceito=$accepted")
                } else if (currentAudioModeProfile == AudioModeProfile.X_PRO_TEST) {
                    error("Conecte o intercomunicador e aguarde a rota Bluetooth antes do teste")
                }
            }

            track.setVolume(returnVolume)
            track.play()
            audioTrack = track
            AppLogger.d(TAG, "AudioTrack de retorno iniciado sob demanda [${currentAudioModeProfile.title}] (Volume: ${(returnVolume * 100).toInt()}%)")
        } catch (e: Exception) {
            releaseAudioTrack()
            _lastError.value = "Falha ao abrir a saída de áudio: ${e.message ?: "erro desconhecido"}"
            AppLogger.e(TAG, "Falha ao inicializar AudioTrack de retorno", e)
        }
    }

    @Synchronized
    private fun releaseAudioTrack() {
        val track = audioTrack
        audioTrack = null
        try {
            if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        } catch (ignored: Exception) {}
        try { track?.release() } catch (ignored: Exception) {}
        AppLogger.d(TAG, "AudioTrack de retorno liberado (Fones 100% livres para WhatsApp/chamadas)")
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(
        denoiseIntensity: Float = 0.85f,
        bypassDsp: Boolean = false,
        initialVolume: Float = 0.0f,
        preset: RiderAudioPreset = RiderAudioPreset.NORMAL,
        audioModeProfile: AudioModeProfile = currentAudioModeProfile
    ) {
        if (_isMonitoring.value) return
        _lastError.value = null
        returnVolume = initialVolume.coerceIn(0.0f, 1.0f)
        currentAudioModeProfile = audioModeProfile
        cleanVoiceDsp.setPreset(preset)

        // BUG-02: Gera um novo ID de sessão ANTES de alocar recursos.
        // O finally da coroutine usará este ID para decidir se deve limpar.
        val currentSessionId = sessionIdGenerator.incrementAndGet()

        val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        try {
            // BUG-01: Por padrão usa AudioSource.MIC em vez de VOICE_COMMUNICATION.
            // VOICE_COMMUNICATION tem prioridade alta na política de captura concorrente do Android.
            // Se o BT Mic Pro capturar com VOICE_COMMUNICATION, o WhatsApp pode perder o microfone
            // ou receber silêncio. AudioSource.MIC tem prioridade mais baixa, permitindo que o
            // WhatsApp (que usa VOICE_COMMUNICATION) capture o microfone sem conflito.
            // No Modo 5 experimental, permite VOICE_COMMUNICATION para testar amarração total no AOSP.
            val audioSource = when (currentAudioModeProfile) {
                AudioModeProfile.MODE_5 -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else -> MediaRecorder.AudioSource.MIC
            }

            val selected = AudioCaptureCompatibility.select { rate ->
                val minimum = AudioRecord.getMinBufferSize(rate, channelConfigIn, audioEncoding)
                if (minimum <= 0) return@select null
                if (AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, audioEncoding) <= 0) {
                    return@select null
                }
                var candidate: AudioRecord? = null
                try {
                    candidate = AudioRecord(audioSource, rate, channelConfigIn, audioEncoding,
                        max(rate / 50 * 4, minimum * 2))
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) candidate else {
                        candidate.release()
                        null
                    }
                } catch (e: SecurityException) {
                    candidate?.release()
                    throw e
                } catch (e: RuntimeException) {
                    candidate?.release()
                    AppLogger.w(TAG, "Captura de $rate Hz indisponível: ${e.message}")
                    null
                }
            } ?: throw IllegalStateException("Nenhum formato PCM de captura disponível neste aparelho")
            val (sampleRate, recorder) = selected
            audioRecord = recorder
            activeSampleRate = sampleRate
            val frameSize = sampleRate / 50

            // Conecta ao microfone Bluetooth se disponível
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btInput = if (currentAudioModeProfile == AudioModeProfile.X_PRO_TEST) {
                    val manager = CommunicationDeviceManager(context)
                    manager.findBestBluetoothCommunicationDevice()?.let(manager::findMatchingInput)
                } else inputs.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
                if (btInput != null) {
                    val accepted = recorder.setPreferredDevice(btInput)
                    if (currentAudioModeProfile == AudioModeProfile.X_PRO_TEST) {
                        check(accepted) { "Android recusou o microfone Bluetooth do teste X Pro" }
                    }
                    AppLogger.i(
                        TAG,
                        "Microfone Bluetooth preferido: ${btInput.productName}; aceito=$accepted"
                    )
                } else if (currentAudioModeProfile == AudioModeProfile.X_PRO_TEST) {
                    error("Microfone do intercomunicador indisponível; aguarde a rota Bluetooth")
                }
            }

            cleanVoiceDsp.configureFilters(sampleRate)
            if (!bypassDsp) {
                audioEffectController.attachToSession(recorder.audioSessionId)
            }

            // Se o volume de retorno estiver ativo (>0%), inicia o AudioTrack.
            // Se estiver em 0% (Mudo), NÃO cria nem inicia o AudioTrack, deixando a saída 100% livre para o WhatsApp!
            if (returnVolume > 0.0f) {
                initAudioTrack()
            } else {
                releaseAudioTrack()
            }

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Android não iniciou a captura do microfone")
            }
            _isMonitoring.value = true
            AppLogger.i(TAG, "LiveAudioMonitor iniciado com sucesso a ${sampleRate}Hz (Retorno: ${(returnVolume * 100).toInt()}%, Preset: ${preset.name}, Denoise: ${denoiseIntensity})")

            monitorJob = coroutineScope.launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val pcmBuffer = ShortArray(frameSize)
                var frameCount = 0
                var windowPeak = 0
                var windowSumSquares = 0.0
                var windowSamples = 0
                var windowClipping = 0
                var consecutiveSilenceFrames = 0
                var consecutiveReadErrors = 0  // BUG-09: Contador de erros consecutivos
                var lastLogTime = System.currentTimeMillis()

                try {
                    while (isActive && _isMonitoring.value) {
                        val readSamples = recorder.read(pcmBuffer, 0, frameSize)
                        if (readSamples > 0) {
                            // BUG-09: Reset do contador de erros após leitura válida
                            consecutiveReadErrors = 0

                            // Medição acústica bruta pré-DSP (Item 10)
                            for (i in 0 until readSamples) {
                                val s = pcmBuffer[i].toInt()
                                val absVal = if (s < 0) -s else s
                                if (absVal > windowPeak) windowPeak = absVal
                                if (absVal >= 32700) windowClipping++
                                windowSumSquares += (s.toDouble() * s.toDouble())
                            }
                            windowSamples += readSamples
                            frameCount++

                            // Aplica o CleanVoice DSP
                            cleanVoiceDsp.process(pcmBuffer, readSamples, denoiseIntensity, bypassDsp)

                            // BUG-07 + BUG-08: Usa returnVolume real em vez de 0.01f hardcoded.
                            // Se o volume for 0 e o AudioTrack for null, pula o write completamente
                            // para não desperdiçar CPU com processamento sem saída.
                            val currentVol = returnVolume
                            val track = audioTrack
                            if (track != null && currentVol > 0.0f) {
                                val written = track.write(pcmBuffer, 0, readSamples)
                                if (written < 0) {
                                    throw IllegalStateException("AudioTrack.write falhou: $written")
                                }
                            }
                            // Se track == null ou volume == 0, o AudioRecord continua capturando
                            // para fins de telemetria, mas não gasta CPU com multiplicação/write.

                            // Telemetria periódica a cada 2 segundos (~100 frames)
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime >= 2000L && windowSamples > 0) {
                                val meanSquare = windowSumSquares / windowSamples
                                val rms = kotlin.math.sqrt(meanSquare)
                                val rmsDb = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
                                val peakDb = if (windowPeak > 0) 20.0 * kotlin.math.log10(windowPeak / 32768.0) else -96.0

                                val status = when {
                                    windowClipping > 0 -> "⚠️ CLIPPING (Saturando microfone)"
                                    rmsDb < -55.0 -> "ℹ️ SILÊNCIO (Sem voz detectada)"
                                    rmsDb in -35.0..-12.0 -> "✅ VOZ CLARA (Nível ideal)"
                                    else -> "OK (Captando sinal)"
                                }

                                AppLogger.audio(
                                    TAG,
                                    "🎤 Áudio Captado: RMS=${"%.1f".format(rmsDb)} dBFS | Pico=${"%.1f".format(peakDb)} dBFS | Clipes=$windowClipping | $status"
                                )

                                if (windowClipping > 5) {
                                    AppLogger.w(TAG, "⚠️ ALERTA DE CORTE/DISTORÇÃO: $windowClipping amostras saturadas detectadas no microfone do capacete!")
                                }

                                if (rmsDb < -60.0) {
                                    consecutiveSilenceFrames++
                                    if (consecutiveSilenceFrames >= 2) {
                                        AppLogger.w(TAG, "⚠️ ALERTA DE MICROFONE MUDO: Sinal extremamente baixo ou microfone não respondendo.")
                                    }
                                } else {
                                    consecutiveSilenceFrames = 0
                                }

                                // Reinicia acumuladores de janela
                                windowPeak = 0
                                windowSumSquares = 0.0
                                windowSamples = 0
                                windowClipping = 0
                                lastLogTime = now
                            }
                        } else if (readSamples < 0) {
                            // BUG-09: Conta erros consecutivos de leitura.
                            // Após maxConsecutiveReadErrors (10) erros seguidos sem leitura válida,
                            // para o monitor para evitar loop infinito de 100% CPU que trava o dispositivo.
                            consecutiveReadErrors++
                            AppLogger.e(TAG, "ERRO DE CAPTURA: AudioRecord.read retornou código de erro $readSamples (erro consecutivo #$consecutiveReadErrors)")
                            if (consecutiveReadErrors >= maxConsecutiveReadErrors) {
                                AppLogger.e(TAG, "LIMITE DE ERROS ATINGIDO ($maxConsecutiveReadErrors erros consecutivos). Parando monitor para proteger CPU e bateria.")
                                break  // Sai do loop; o finally irá chamar stopMonitoringInternal
                            }
                            // Pequena pausa antes de tentar novamente para não queimar CPU
                            delay(50)
                        } else {
                            delay(5)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Erro no loop de áudio do LiveAudioMonitor", e)
                } finally {
                    // BUG-02: Só limpa recursos se esta sessão ainda for a sessão ativa.
                    // Se o usuário chamou startMonitoring() de novo (gerando novo sessionId),
                    // o finally da sessão antiga NÃO deve destruir os recursos da nova sessão.
                    if (sessionIdGenerator.get() == currentSessionId) {
                        stopMonitoringInternal()
                    } else {
                        AppLogger.d(TAG, "Sessão #$currentSessionId encerrada, mas outra sessão já está ativa. Pulando cleanup.")
                    }
                }
            }
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Falha ao iniciar o teste de microfone"
            AppLogger.e(TAG, "Falha ao inicializar AudioRecord/AudioTrack no LiveAudioMonitor", e)
            // BUG-02: Em caso de falha na inicialização, limpa recursos parciais diretamente
            // (não depende do flag _isMonitoring que pode não ter sido setado)
            cleanupResources()
            _isMonitoring.value = false
        }
    }

    fun stopMonitoring() {
        if (!_isMonitoring.value) return
        // Incrementa sessionId para invalidar o finally da coroutine ativa
        sessionIdGenerator.incrementAndGet()
        stopMonitoringInternal()
    }

    /**
     * Método interno que realmente libera os recursos de áudio.
     * Separado do público para que o finally da coroutine possa chamar sem
     * colidir com a verificação de _isMonitoring no stopMonitoring() público.
     */
    private fun stopMonitoringInternal() {
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null
        cleanupResources()
        AppLogger.i(TAG, "LiveAudioMonitor interrompido.")
    }

    /**
     * Libera AudioRecord, AudioTrack e efeitos de forma segura.
     * Pode ser chamado múltiplas vezes sem efeito colateral.
     */
    private fun cleanupResources() {
        val recorder = audioRecord
        audioRecord = null
        try { recorder?.stop() } catch (ignored: Exception) {}
        try { recorder?.release() } catch (ignored: Exception) {}

        releaseAudioTrack()

        try {
            audioEffectController.release()
        } catch (ignored: Exception) {}
    }

    companion object {
        private const val TAG = "LiveAudioMonitor"
    }
}
