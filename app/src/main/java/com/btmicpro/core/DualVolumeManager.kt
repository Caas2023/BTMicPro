package com.btmicpro.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * DualVolumeManager — Gerenciador Unificado e Sincronizador de Volume Duplo.
 *
 * Resolve a limitação do Android onde a barra de mídia desaparece durante o MODE_IN_COMMUNICATION.
 * Permite que o motociclista ajuste tanto o volume de Mídia (WhatsApp, GPS, Música) quanto o volume
 * de Chamada (voz do intercomunicador), individualmente ou de forma sincronizada pelas teclas físicas.
 */
class DualVolumeManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val maxMediaVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    val maxCallVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)

    private val _mediaVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private val _callVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL))
    val callVolume: StateFlow<Int> = _callVolume.asStateFlow()

    // Sincronização automática desativada por padrão: permite ajuste independente dos canais
    private val _isSyncEnabled = MutableStateFlow(false)
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    @Volatile
    private var lastProgrammaticChangeTime = 0L
    private var isReceiverRegistered = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_VOLUME_CHANGED) return

            val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
            val streamValue = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)

            // Suprime ecos de alterações programáticas da UI para evitar loops de oscilação ("mexendo sozinho")
            val isEcho = (System.currentTimeMillis() - lastProgrammaticChangeTime) < 800L
            if (isEcho) {
                if (streamType == AudioManager.STREAM_MUSIC && streamValue >= 0) {
                    _mediaVolume.value = streamValue
                } else if (streamType == AudioManager.STREAM_VOICE_CALL && streamValue >= 0) {
                    _callVolume.value = streamValue
                }
                return
            }

            // Teclas físicas de hardware acionadas pelo usuário
            when (streamType) {
                AudioManager.STREAM_VOICE_CALL -> {
                    val newCall = if (streamValue >= 0) streamValue else audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    _callVolume.value = newCall
                    if (_isSyncEnabled.value) {
                        syncMediaFromCall(newCall)
                    }
                }
                AudioManager.STREAM_MUSIC -> {
                    val newMedia = if (streamValue >= 0) streamValue else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    _mediaVolume.value = newMedia
                    if (_isSyncEnabled.value) {
                        syncCallFromMedia(newMedia)
                    }
                }
            }
        }
    }

    /**
     * Inicia o monitoramento de volume.
     */
    fun startMonitoring() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter(ACTION_VOLUME_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(volumeReceiver, filter)
            }
            isReceiverRegistered = true

            // Padrão Moto/Capacete: Volumes de Mídia e Chamada vêm no MÁXIMO por padrão (a pessoa abaixa se quiser)
            val prefs = context.getSharedPreferences("dual_volume_prefs", Context.MODE_PRIVATE)
            val userCustomized = prefs.getBoolean("user_customized_volumes", false)
            if (!userCustomized) {
                maximizeVolumes(showUi = false)
            } else {
                refreshVolumes()
            }
            Log.i(TAG, "DualVolumeManager iniciado: Mídia=${_mediaVolume.value}/$maxMediaVolume, Chamada=${_callVolume.value}/$maxCallVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao registrar volumeReceiver", e)
        }
    }

    /**
     * Define ambos os volumes (Mídia e Chamada) para o nível MÁXIMO (100%).
     */
    fun maximizeVolumes(showUi: Boolean = false) {
        setMediaVolume(maxMediaVolume, showUi)
        setCallVolume(maxCallVolume, showUi)
    }

    /**
     * Encerra o monitoramento de volume.
     */
    fun stopMonitoring() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(volumeReceiver)
            isReceiverRegistered = false
            Log.i(TAG, "DualVolumeManager encerrado com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desregistrar volumeReceiver", e)
        }
    }

    /**
     * Atualiza os volumes atuais a partir do hardware do sistema.
     */
    fun refreshVolumes() {
        try {
            _mediaVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            _callVolume.value = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao ler volumes do sistema", e)
        }
    }

    /**
     * Define o volume de Mídia (STREAM_MUSIC).
     */
    fun setMediaVolume(level: Int, showUi: Boolean = true) {
        val clamped = level.coerceIn(0, maxMediaVolume)
        if (_mediaVolume.value == clamped && !showUi) return
        _mediaVolume.value = clamped
        lastProgrammaticChangeTime = System.currentTimeMillis()

        try {
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao definir volume de mídia", e)
        }

        if (showUi) {
            context.getSharedPreferences("dual_volume_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("user_customized_volumes", true).apply()
        }

        if (_isSyncEnabled.value) {
            syncCallFromMedia(clamped, showUi = false)
        }
    }

    /**
     * Define o volume de Chamada / Voz (STREAM_VOICE_CALL).
     */
    fun setCallVolume(level: Int, showUi: Boolean = true) {
        val clamped = level.coerceIn(0, maxCallVolume)
        if (_callVolume.value == clamped && !showUi) return
        _callVolume.value = clamped
        lastProgrammaticChangeTime = System.currentTimeMillis()

        try {
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, clamped, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao definir volume de chamada", e)
        }

        if (showUi) {
            context.getSharedPreferences("dual_volume_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("user_customized_volumes", true).apply()
        }

        if (_isSyncEnabled.value) {
            syncMediaFromCall(clamped, showUi = false)
        }
    }

    /**
     * Aumenta ou diminui o volume de mídia em 1 passo.
     */
    fun stepMedia(up: Boolean) {
        val current = _mediaVolume.value
        val step = if (up) 1 else -1
        setMediaVolume(current + step, showUi = true)
    }

    /**
     * Aumenta ou diminui o volume de chamada em 1 passo.
     */
    fun stepCall(up: Boolean) {
        val current = _callVolume.value
        val step = if (up) 1 else -1
        setCallVolume(current + step, showUi = true)
    }

    /**
     * Ativa ou desativa a sincronização automática dos volumes.
     */
    fun setSyncEnabled(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        if (enabled) {
            // Sincroniza a mídia para igualar a proporção da chamada
            syncMediaFromCall(_callVolume.value, showUi = false)
        }
    }

    private fun syncMediaFromCall(callVal: Int, showUi: Boolean = false) {
        val ratio = callVal.toFloat() / maxCallVolume.toFloat()
        val targetMedia = (ratio * maxMediaVolume.toFloat()).roundToInt().coerceIn(0, maxMediaVolume)
        if (targetMedia != _mediaVolume.value) {
            _mediaVolume.value = targetMedia
            lastProgrammaticChangeTime = System.currentTimeMillis()
            try {
                val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetMedia, flags)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na sincronização mídia <- chamada", e)
            }
        }
    }

    private fun syncCallFromMedia(mediaVal: Int, showUi: Boolean = false) {
        val ratio = mediaVal.toFloat() / maxMediaVolume.toFloat()
        val targetCall = (ratio * maxCallVolume.toFloat()).roundToInt().coerceIn(0, maxCallVolume)
        if (targetCall != _callVolume.value) {
            _callVolume.value = targetCall
            lastProgrammaticChangeTime = System.currentTimeMillis()
            try {
                val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetCall, flags)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na sincronização chamada <- mídia", e)
            }
        }
    }

    companion object {
        private const val TAG = "DualVolumeManager"
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"

        @Volatile
        private var instance: DualVolumeManager? = null

        fun getInstance(context: Context): DualVolumeManager {
            return instance ?: synchronized(this) {
                instance ?: DualVolumeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
