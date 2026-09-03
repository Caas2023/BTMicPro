package com.btmicpro.core

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

/**
 * Aumentador de volume de mídia para ouvir em bar/ruído extremo.
 * Usa APIs nativas do Android (sem root) para ganhar até +15dB percebido.
 *
 * - LoudnessEnhancer: compressor inteligente que aumenta voz sem estourar (0 a +15dB)
 * - Equalizer: empurra frequências de voz 1-3kHz para estourar no capacete
 * - AudioManager: garante STREAM_MUSIC no máximo
 *
 * Funciona em WhatsApp, YouTube, Telegram, mídia toda.
 * Sessão 0 = efeito global no mix de saída.
 */
class MediaBooster(private val context: Context) {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var audioManager: AudioManager? = null

    private var isBarModeActive = false
    private var currentBoostLevel = 0 // 0 a 100

    companion object {
        private const val TAG = "MediaBooster"
        // Session 0 = mix global de saída (afeta toda mídia)
        private const val GLOBAL_SESSION = 0
    }

    /**
     * Verifica se o hardware suporta LoudnessEnhancer
     */
    fun isSupported(): Boolean {
        return try {
            // Tenta criar temporariamente na sessão 0 para testar
            val test = LoudnessEnhancer(GLOBAL_SESSION)
            test.release()
            true
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer não suportado neste device: ${e.message}")
            false
        }
    }

    /**
     * Ativa o MODO BAR completo: volume max + Loudness + Equalizer de voz
     */
    fun enableBarMode(boostLevel: Int = 80) {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 1. Volume de mídia no máximo (hardware)
            maximizeMediaVolume()
            
            // 2. LoudnessEnhancer - compressor que aumenta voz até +15dB
            enableLoudnessEnhancer(boostLevel)
            
            // 3. Equalizer - empurra voz 1-3kHz
            enableVoiceEqualizer()

            isBarModeActive = true
            currentBoostLevel = boostLevel
            Log.d(TAG, "MODO BAR ativado: boost=$boostLevel% loudness+EQ")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ativar MODO BAR", e)
        }
    }

    /**
     * Ajusta apenas o nível de boost (0-100) sem recriar tudo
     */
    fun setBoostLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        currentBoostLevel = clamped
        
        if (!isBarModeActive && clamped > 0) {
            enableBarMode(clamped)
            return
        }
        
        if (clamped == 0) {
            // Mantém EQ mas zera loudness
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (e: Exception) { Log.e(TAG, "Erro setTargetGain 0", e) }
            return
        }

        try {
            // Loudness: 0 a 15000 mB (0 a +15dB) - 100% = 8000mB (~+8dB é seguro sem distorcer)
            // Curva: 0%=0mB, 50%=4000mB, 100%=8000-10000mB
            val targetGainMb = (clamped * 80).coerceIn(0, 8000) // até +8dB seguro
            loudnessEnhancer?.setTargetGain(targetGainMb)
            Log.d(TAG, "Boost ajustado para $clamped% -> ${targetGainMb}mB")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ajustar boost", e)
        }
    }

    /**
     * Desativa tudo e volta ao normal
     */
    fun disableBarMode() {
        try {
            loudnessEnhancer?.apply {
                enabled = false
                release()
            }
            loudnessEnhancer = null

            equalizer?.apply {
                enabled = false
                release()
            }
            equalizer = null

            isBarModeActive = false
            currentBoostLevel = 0
            Log.d(TAG, "MODO BAR desativado - áudio normal")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desativar MODO BAR", e)
        }
    }

    private fun maximizeMediaVolume() {
        try {
            val am = audioManager ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            // Também garante STREAM_VOICE_CALL no max para quando SCO está ativo
            val maxVoice = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
            Log.d(TAG, "Volume mídia/voz maximizado: music=$maxMusic voice=$maxVoice")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao maximizar volume", e)
        }
    }

    private fun enableLoudnessEnhancer(boostLevel: Int) {
        try {
            // Libera anterior se existir
            loudnessEnhancer?.release()
            
            loudnessEnhancer = LoudnessEnhancer(GLOBAL_SESSION).apply {
                // Converte 0-100% para 0-8000 mB (mili-Bel, 100mB = 1dB)
                val targetGain = (boostLevel.coerceIn(0, 100) * 80).coerceIn(0, 8000)
                setTargetGain(targetGain)
                enabled = true
                Log.d(TAG, "LoudnessEnhancer ativado: targetGain=${targetGain}mB (${targetGain/100}dB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LoudnessEnhancer falhou (dispositivo não suporta sessão 0?)", e)
            // Fallback: tenta com sessão do AudioManager se global falhar
            tryAlternativeLoudness(boostLevel)
        }
    }

    private fun tryAlternativeLoudness(boostLevel: Int) {
        // Em alguns MediaTek, sessão 0 exige permissão extra.
        // Não há fallback fácil sem capturar sessão de um MediaPlayer,
        // então apenas maximizamos volume e EQ.
        Log.w(TAG, "Sem LoudnessEnhancer, usando apenas Equalizer + volume max")
    }

    private fun enableVoiceEqualizer() {
        try {
            equalizer?.release()
            
            equalizer = Equalizer(0, GLOBAL_SESSION).apply {
                enabled = true
                
                val numBands = numberOfBands
                val bandLevelRange = bandLevelRange // ex: -1500 a +1500 (mB)
                Log.d(TAG, "Equalizer: $numBands bandas, range=${bandLevelRange[0]}..${bandLevelRange[1]} mB")
                
                // Estratégia para voz em bar: corta grave (ruído), empurra médios 1-3kHz
                for (i in 0 until numBands) {
                    val freq = getCenterFreq(i.toShort()) / 1000 // Hz
                    val level: Short = when {
                        freq < 300 -> -300 // corta grave de bar (-3dB)
                        freq in 300..800 -> 150 // leve boost voz grave
                        freq in 800..3000 -> 400 // +4dB na voz (inteligibilidade)
                        freq in 3000..8000 -> 200 // +2dB brilho
                        else -> 0
                    }
                    // Clampa no range do hardware
                    val clamped = level.coerceIn(bandLevelRange[0], bandLevelRange[1])
                    setBandLevel(i.toShort(), clamped)
                }
                Log.d(TAG, "Equalizer de voz ativado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Equalizer falhou", e)
            equalizer = null
        }
    }

    fun isActive(): Boolean = isBarModeActive

    fun getBoostLevel(): Int = currentBoostLevel

    fun release() {
        disableBarMode()
    }
}
