package com.btmicpro.core

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Controlador responsável por ativar e gerenciar efeitos de áudio em nível de hardware/sistema,
 * tais como Supressão de Ruído (NoiseSuppressor), Controle Automático de Ganho (AGC)
 * e Cancelamento de Eco Acústico (AEC).
 */
class AudioEffectController {

    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    /**
     * Aplica os efeitos de áudio nativos do sistema operacional em uma sessão de áudio específica.
     *
     * @param audioSessionId Identificador da sessão de áudio fornecido pelo AudioRecord.
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId == 0) {
            Log.w(TAG, "ID de sessão de áudio inválido (0). Ignorando aplicação de efeitos.")
            return
        }

        // 1. Supressor de Ruído Nativo (NoiseSuppressor)
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "NoiseSuppressor ativado com sucesso para a sessão $audioSessionId.")
                    AppLogger.i(TAG, "🛡️ Hardware DSP: NoiseSuppressor (Redutor de Ruído/Vento) ATIVADO!")
                }
            } else {
                Log.i(TAG, "NoiseSuppressor nativo não suportado pelo hardware deste dispositivo.")
                AppLogger.d(TAG, "Hardware DSP: NoiseSuppressor nativo não disponível neste chipset.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar NoiseSuppressor", e)
            AppLogger.e(TAG, "Falha ao ativar NoiseSuppressor de hardware", e)
        }

        // 2. Controle Automático de Ganho (AutomaticGainControl)
        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AutomaticGainControl ativado com sucesso para a sessão $audioSessionId.")
                    AppLogger.i(TAG, "🎚️ Hardware DSP: AGC (Nivelador de Ganho Vocal) ATIVADO!")
                }
            } else {
                Log.i(TAG, "AutomaticGainControl nativo não suportado pelo hardware deste dispositivo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar AutomaticGainControl", e)
        }

        // 3. Cancelador de Eco Acústico (AcousticEchoCanceler)
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AcousticEchoCanceler ativado com sucesso para a sessão $audioSessionId.")
                    AppLogger.i(TAG, "🔇 Hardware DSP: AcousticEchoCanceler (Anti-Eco/Vazamento) ATIVADO!")
                }
            } else {
                Log.i(TAG, "AcousticEchoCanceler nativo não suportado pelo hardware deste dispositivo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar AcousticEchoCanceler", e)
        }
    }

    /**
     * Libera todos os recursos alocados para os efeitos de áudio.
     */
    fun release() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null

            automaticGainControl?.release()
            automaticGainControl = null

            acousticEchoCanceler?.release()
            acousticEchoCanceler = null

            Log.d(TAG, "Todos os efeitos de áudio do sistema foram liberados.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar efeitos de áudio", e)
        }
    }

    companion object {
        private const val TAG = "AudioEffectController"
    }
}
