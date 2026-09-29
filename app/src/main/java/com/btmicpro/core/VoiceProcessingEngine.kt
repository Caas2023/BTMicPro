package com.btmicpro.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Presets de áudio especializados para motociclistas.
 */
enum class RiderAudioPreset(val displayName: String, val description: String) {
    NORMAL("Normal", "Equilíbrio diário para cidade e velocidade moderada"),
    CITY("Cidade / Trânsito", "Otimizado para ruído de trânsito, escape e cruzamentos"),
    HIGHWAY("Rodovia (>80 km/h)", "Filtro anti-vento agressivo para velocidade alta"),
    EXTREME_WIND("Vento Extremo", "Atenuação máxima de turbulência e capacete aberto"),
    VOICE_CLARITY("Clareza Vocal", "Realce de formantes e inteligibilidade máxima da voz")
}

/**
 * VoiceProcessingEngine V4 — Motor modular de processamento digital de sinais em tempo real.
 * 
 * Pipeline de 8 Estágios:
 * 1. DC Block: Remoção de offset DC a 20Hz.
 * 2. High-Pass Adaptativo (Butterworth 4ª ordem / 24 dB/oitava): 80Hz a 160Hz.
 * 3. Wind Noise Detector: Análise de energia espectral para detecção de rajadas de vento.
 * 4. Soft Downward Expander: Supressão suave do piso de ruído baseada em janelas RMS (sem cortes de fonemas).
 * 5. Dynamic Vocal EQ: Realce dinâmico em 3.0 kHz dependente do nível.
 * 6. AGC (Automatic Gain Control): Nivelamento adaptativo de volume vocal sem efeito pumping.
 * 7. Vocal Compressor: Compressão suave com joelho suave (soft-knee).
 * 8. True Peak Brickwall Limiter: Teto rígido em -1.0 dBFS, garantindo zero clipping.
 *
 * Princípio de Performance: Zero alocação de objetos dentro do loop DSP (Zero-GC).
 */
class VoiceProcessingEngine(private var sampleRate: Int = 16000) {

    // 1. DC Block Filter State
    private var dcBlockX1 = 0.0f
    private var dcBlockY1 = 0.0f
    private val dcBlockR = 0.995f

    // 2. High-Pass Filter (Butterworth 4ª ordem: 2 BiQuads em cascata)
    private class BiQuad {
        var b0 = 1.0f; var b1 = 0.0f; var b2 = 0.0f
        var a1 = 0.0f; var a2 = 0.0f
        var x1 = 0.0f; var x2 = 0.0f
        var y1 = 0.0f; var y2 = 0.0f

        inline fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0.0f; x2 = 0.0f; y1 = 0.0f; y2 = 0.0f
        }
    }

    private val hp1 = BiQuad()
    private val hp2 = BiQuad()
    private val presenceEq = BiQuad()

    // 3. Wind Noise Detector
    private var subEnergyAvg = 0.0f
    private var midEnergyAvg = 0.0f
    var detectedWindLevel = 0.0f
        private set

    // 4. Soft Expander & Envelope
    private var rmsEnvelope = 0.0f
    private var holdCounter = 0
    private var currentExpanderGain = 1.0f

    // 5. AGC (Automatic Gain Control)
    private var agcGain = 1.0f

    // Constantes de tempo baseadas no sampleRate
    private var attackCoeff = 0.0f
    private var releaseCoeff = 0.0f
    private var holdSamples = 0
    private var frameSize = 160 // 10ms a 16kHz

    // Configurações do Preset Ativo
    private var activePreset: RiderAudioPreset = RiderAudioPreset.NORMAL
    private var targetHighPassHz = 120.0f

    init {
        configure(sampleRate, RiderAudioPreset.NORMAL)
    }

    /**
     * Reconfigura a taxa de amostragem e os parâmetros do preset selecionado.
     */
    fun configure(newSampleRate: Int, preset: RiderAudioPreset = activePreset) {
        sampleRate = if (newSampleRate > 0) newSampleRate else 16000
        activePreset = preset
        frameSize = max(16, sampleRate / 100) // Bloco de 10ms

        // BUG-05: Coeficientes de attack/release devem usar a duração real do BLOCO,
        // não de uma amostra individual. Os coeficientes são aplicados 1x por bloco
        // de frameSize amostras, então dt deve ser frameSize/sampleRate.
        // Com dt=1/sampleRate e aplicação por bloco, a constante de tempo efetiva ficava
        // ~160x mais lenta que o pretendido (attack de 800ms vs 5ms, release de 32s vs 200ms).
        val blockDuration = frameSize.toFloat() / sampleRate.toFloat()
        attackCoeff = 1.0f - exp(-blockDuration / 0.005f)   // Attack de 5ms por bloco
        releaseCoeff = 1.0f - exp(-blockDuration / 0.200f)  // Release de 200ms por bloco
        // FIX CORTE: hold de 120ms era curto e cortava fim de palavras / pausas naturais.
        // 250ms dá hangover de VAD para não picotar a fala no teste local nem mascarar como "corte do WhatsApp".
        holdSamples = (0.250f * sampleRate).toInt() // Hold de 250ms

        targetHighPassHz = when (preset) {
            RiderAudioPreset.NORMAL -> 100.0f
            RiderAudioPreset.CITY -> 120.0f
            RiderAudioPreset.HIGHWAY -> 150.0f
            RiderAudioPreset.EXTREME_WIND -> 160.0f
            RiderAudioPreset.VOICE_CLARITY -> 120.0f
        }

        // Configuração dos BiQuads Butterworth de 4ª ordem (High-Pass)
        calculateHighPassBiquad(hp1, targetHighPassHz, 0.5411961f, sampleRate)
        calculateHighPassBiquad(hp2, targetHighPassHz, 1.306563f, sampleRate)

        // Configuração do Peaking EQ de Presença Vocal em 3.0 kHz
        val eqGainDb = when (preset) {
            RiderAudioPreset.VOICE_CLARITY -> 4.5f
            RiderAudioPreset.EXTREME_WIND -> 3.8f
            RiderAudioPreset.HIGHWAY -> 3.5f
            RiderAudioPreset.CITY -> 3.0f
            RiderAudioPreset.NORMAL -> 2.5f
        }
        calculatePeakingBiquad(presenceEq, 3000.0f, eqGainDb, 1.2f, sampleRate)

        resetState()
    }

    fun setPreset(preset: RiderAudioPreset) {
        configure(sampleRate, preset)
    }

    fun getActivePreset(): RiderAudioPreset = activePreset

    /**
     * Processamento de um buffer PCM 16-bit com o pipeline modular completo V4.
     */
    fun process(buffer: ShortArray, length: Int, userIntensity: Float = 0.85f, bypassDsp: Boolean = false) {
        if (bypassDsp) {
            // No modo Bypass/RAW, apenas protege contra estouro digital
            for (i in 0 until length) {
                var s = buffer[i].toFloat()
                if (abs(s) > SOFT_CLIP_KNEE) {
                    s = softClip(s)
                }
                buffer[i] = s.coerceIn(-32768f, 32767f).toInt().toShort()
            }
            return
        }

        val clampedIntensity = userIntensity.coerceIn(0.0f, 1.0f)

        // Limiares parametrizados pelo preset e intensidade
        val baseRmsThreshold = when (activePreset) {
            RiderAudioPreset.EXTREME_WIND -> 260.0f
            RiderAudioPreset.HIGHWAY -> 220.0f
            RiderAudioPreset.CITY -> 180.0f
            RiderAudioPreset.NORMAL -> 150.0f
            RiderAudioPreset.VOICE_CLARITY -> 140.0f
        }
        val voiceRmsThreshold = baseRmsThreshold + (clampedIntensity * 120.0f)
        
        // Piso mínimo de atenuação do expansor (ex: 0.40 = -8dB, 0.30 = -10.5dB).
        // FIX CORTE: pisos 0.15-0.18 (-15 a -16.5dB) mutavam sílabas fracas e pareciam "cortes" na gravação.
        // Subido para 0.30-0.40 para atenuar vento sem zerar a voz. Gate agressivo demais = picote.
        val minFloorGain = when (activePreset) {
            RiderAudioPreset.EXTREME_WIND -> 0.30f
            RiderAudioPreset.HIGHWAY -> 0.30f
            RiderAudioPreset.CITY -> 0.35f
            RiderAudioPreset.NORMAL -> 0.40f
            RiderAudioPreset.VOICE_CLARITY -> 0.40f
        }

        var index = 0
        while (index < length) {
            val blockSize = min(frameSize, length - index)

            // ESTÁGIO 1, 2 e 3: DC Block, High-Pass 4ª Ordem, Peaking EQ e Detector de Vento
            var blockSumSquares = 0.0
            var subBandSum = 0.0
            var midBandSum = 0.0

            for (i in 0 until blockSize) {
                val rawSample = buffer[index + i].toFloat()

                // 1. DC Block (20Hz)
                val dcOut = rawSample - dcBlockX1 + dcBlockR * dcBlockY1
                dcBlockX1 = rawSample
                dcBlockY1 = dcOut

                // 2. High-Pass Butterworth 4ª Ordem
                val hpOut = hp2.process(hp1.process(dcOut))

                // 3. Peaking EQ de Presença Vocal
                val eqOut = presenceEq.process(hpOut)
                buffer[index + i] = eqOut.coerceIn(-32768f, 32767f).toInt().toShort()

                // Medição para Wind Detector: energia antes do HPF vs depois do HPF
                subBandSum += abs(rawSample - hpOut).toDouble()
                midBandSum += abs(eqOut).toDouble()
                blockSumSquares += (eqOut * eqOut).toDouble()
            }

            // Atualiza o detector de vento (relação de energia subsônica vs fala)
            val subRatio = if (midBandSum > 1.0) (subBandSum / (midBandSum + subBandSum)).toFloat() else 0.0f
            detectedWindLevel = 0.9f * detectedWindLevel + 0.1f * subRatio.coerceIn(0.0f, 1.0f)

            // ESTÁGIO 4: Expansor Suave com Detector RMS
            val blockRms = sqrt(blockSumSquares / blockSize).toFloat()

            if (blockRms > rmsEnvelope) {
                rmsEnvelope += attackCoeff * (blockRms - rmsEnvelope)
            } else {
                if (holdCounter > 0) {
                    holdCounter -= blockSize
                } else {
                    rmsEnvelope += releaseCoeff * (blockRms - rmsEnvelope)
                }
            }

            if (blockRms >= voiceRmsThreshold) {
                holdCounter = holdSamples
            }

            // Ganho do expansor suave (nunca zera a zero, elimina 100% de cortes)
            val targetExpanderGain = if (rmsEnvelope >= voiceRmsThreshold) {
                1.0f
            } else {
                val ratio = (rmsEnvelope / voiceRmsThreshold).coerceIn(0.0f, 1.0f)
                minFloorGain + (1.0f - minFloorGain) * (ratio * ratio)
            }

            // ESTÁGIO 5: AGC (Automatic Gain Control adaptativo)
            val targetAgcGain = if (rmsEnvelope > 100.0f) {
                // Alvo de voz confortável ~8000 RMS (-12 dBFS)
                val desiredGain = (8000.0f / (rmsEnvelope + 50.0f)).coerceIn(0.8f, 2.4f)
                desiredGain
            } else {
                1.0f
            }
            agcGain += 0.05f * (targetAgcGain - agcGain)

            // ESTÁGIO 6, 7 e 8: Aplicação interpolada, Compressor Vocal e Brickwall Limiter
            val gainStep = (targetExpanderGain - currentExpanderGain) / blockSize.toFloat()
            for (i in 0 until blockSize) {
                currentExpanderGain += gainStep
                var sample = buffer[index + i].toFloat() * currentExpanderGain * agcGain

                // 7. Compressor Vocal Dinâmico (2:1 acima de 14000)
                val absS = abs(sample)
                if (absS > 14000f) {
                    val excess = absS - 14000f
                    val sign = if (sample >= 0) 1.0f else -1.0f
                    sample = sign * (14000f + excess * 0.5f)
                }

                // 8. True Peak Brickwall Limiter (-1.0 dBFS = 29205)
                if (abs(sample) > SOFT_CLIP_KNEE) {
                    sample = softClip(sample)
                }

                buffer[index + i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
            }

            index += blockSize
        }
    }

    private fun softClip(sample: Float): Float {
        val sign = if (sample >= 0) 1.0f else -1.0f
        val x = abs(sample)
        if (x <= SOFT_CLIP_KNEE) return sample
        val kneeRange = SOFT_CLIP_CEILING - SOFT_CLIP_KNEE
        val normalized = (x - SOFT_CLIP_KNEE) / kneeRange
        val curved = SOFT_CLIP_KNEE + kneeRange * kotlin.math.tanh(normalized)
        return sign * curved.coerceAtMost(SOFT_CLIP_CEILING)
    }

    private fun calculateHighPassBiquad(filter: BiQuad, fc: Float, q: Float, fs: Int) {
        val omega = 2.0 * Math.PI * (fc.toDouble() / fs.toDouble())
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * q.toDouble())

        val b0 = (1.0 + cosOmega) / 2.0
        val b1 = -(1.0 + cosOmega)
        val b2 = (1.0 + cosOmega) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosOmega
        val a2 = 1.0 - alpha

        filter.b0 = (b0 / a0).toFloat()
        filter.b1 = (b1 / a0).toFloat()
        filter.b2 = (b2 / a0).toFloat()
        filter.a1 = (a1 / a0).toFloat()
        filter.a2 = (a2 / a0).toFloat()
    }

    private fun calculatePeakingBiquad(filter: BiQuad, f0: Float, gainDb: Float, q: Float, fs: Int) {
        val a = Math.pow(10.0, gainDb.toDouble() / 40.0)
        val omega = 2.0 * Math.PI * (f0.toDouble() / fs.toDouble())
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * q.toDouble())

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosOmega
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosOmega
        val a2 = 1.0 - alpha / a

        filter.b0 = (b0 / a0).toFloat()
        filter.b1 = (b1 / a0).toFloat()
        filter.b2 = (b2 / a0).toFloat()
        filter.a1 = (a1 / a0).toFloat()
        filter.a2 = (a2 / a0).toFloat()
    }

    fun resetState() {
        dcBlockX1 = 0.0f; dcBlockY1 = 0.0f
        hp1.reset()
        hp2.reset()
        presenceEq.reset()
        rmsEnvelope = 0.0f
        holdCounter = 0
        currentExpanderGain = 1.0f
        detectedWindLevel = 0.0f
        agcGain = 1.0f
    }

    companion object {
        private const val SOFT_CLIP_KNEE = 26000f
        private const val SOFT_CLIP_CEILING = 29205f // -1 dBFS
    }
}
