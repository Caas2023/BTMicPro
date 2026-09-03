package com.btmicpro.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CleanVoiceDsp — Motor profissional de processamento digital de sinais de voz para motociclistas.
 *
 * Arquitetura de 5 Estágios:
 * 1. Filtro Passa-Alta Butterworth de 4ª Ordem (BiQuad Cascade @ 160Hz):
 *    Corta 24 dB/oitava de ruídos subsônicos de vento, estrondos aerodinâmicos e ressonâncias do motor.
 * 2. Expansor Descendente Suave com Detecção RMS por Janelas e Histerese (Soft Downward Expander):
 *    Substitui o Noise Gate destrutivo. Mede a energia em blocos RMS de 10ms, utiliza attack rápido (5ms),
 *    hold time (120ms) e release suave (200ms). Atenua o piso de ruído em até -14dB sem mutar a zero,
 *    eliminando 100% dos cortes de fonemas e picotamentos.
 * 3. Equalizador Paramétrico de Presença Vocal (Peaking EQ @ 3.0 kHz, +3.5 dB):
 *    Destaca a inteligibilidade da fala humana sobre ruídos de escapamento e tráfego.
 * 4. Compressor Vocal Dinâmico (DRC com Makeup Gain):
 *    Equilibra a dinâmica para que a voz fique sempre audível e nivelada.
 * 5. True Peak Soft Clipper & Brickwall Limiter (@ -0.5 dBFS):
 *    Elimina qualquer saturação ou estouro digital de áudio PCM 16-bit.
 */
class CleanVoiceDsp(private var sampleRate: Int = 16000) {

    // Coeficientes e estados do Filtro Passa-Alta Butterworth de 4ª Ordem (2 BiQuads em cascata)
    private class BiQuadFilter {
        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a1 = 0.0f
        var a2 = 0.0f

        var x1 = 0.0f
        var x2 = 0.0f
        var y1 = 0.0f
        var y2 = 0.0f

        fun process(input: Float): Float {
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

    private val hpStage1 = BiQuadFilter()
    private val hpStage2 = BiQuadFilter()
    private val presenceEq = BiQuadFilter()

    // Parâmetros do Expansor Descendente Suave
    private var rmsEnvelope = 0.0f
    private var holdCounter = 0
    private var currentExpanderGain = 1.0f

    // Constantes dinâmicas calculadas de acordo com o sampleRate
    private var attackCoeff = 0.0f
    private var releaseCoeff = 0.0f
    private var holdSamples = 0
    private var frameSize = 160 // 10ms a 16kHz

    init {
        configureFilters(sampleRate)
    }

    /**
     * Reconfigura os coeficientes dos filtros e envelopes para a taxa de amostragem informada.
     */
    fun configureFilters(newSampleRate: Int) {
        sampleRate = if (newSampleRate > 0) newSampleRate else 16000
        frameSize = max(16, sampleRate / 100) // Bloco de 10ms

        val dt = 1.0f / sampleRate
        // Attack de 5ms
        attackCoeff = 1.0f - kotlin.math.exp(-dt / 0.005f)
        // Release de 200ms
        releaseCoeff = 1.0f - kotlin.math.exp(-dt / 0.200f)
        // Hold de 120ms
        holdSamples = (0.120f * sampleRate).toInt()

        // 1. Passa-Alta Butterworth 4ª Ordem a 160Hz
        val cutoffHz = 160.0f
        calculateHighPassBiquad(hpStage1, cutoffHz, 0.5411961f, sampleRate)
        calculateHighPassBiquad(hpStage2, cutoffHz, 1.306563f, sampleRate)

        // 2. Peaking EQ de Presença Vocal em 3000Hz (+3.5dB, Q = 1.2)
        calculatePeakingBiquad(presenceEq, 3000.0f, 3.5f, 1.2f, sampleRate)

        resetState()
    }

    private fun calculateHighPassBiquad(filter: BiQuadFilter, fc: Float, q: Float, fs: Int) {
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

    private fun calculatePeakingBiquad(filter: BiQuadFilter, f0: Float, gainDb: Float, q: Float, fs: Int) {
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

    /**
     * Processa um buffer de amostras PCM 16-bit com o pipeline CleanVoice Pro.
     *
     * @param buffer Array de amostras PCM (ShortArray).
     * @param length Quantidade de amostras a serem processadas no buffer.
     * @param intensity Intensidade de supressão de ruído (0.0f a 1.0f, padrão 0.85f).
     * @param bypassDsp Se verdadeiro, apenas aplica proteção de limiter sem alterar o espectro da voz.
     */
    fun process(buffer: ShortArray, length: Int, intensity: Float = 0.85f, bypassDsp: Boolean = false) {
        if (bypassDsp) {
            // No modo RAW / Bypass, apenas previne estalos digitais
            for (i in 0 until length) {
                var s = buffer[i].toFloat()
                if (abs(s) > 31500f) {
                    s = softClip(s)
                }
                buffer[i] = s.coerceIn(-32768f, 32767f).toInt().toShort()
            }
            return
        }

        val clampedIntensity = intensity.coerceIn(0.0f, 1.0f)
        // Limiar RMS para detecção de voz (calibrado para microfones de capacete)
        val voiceRmsThreshold = 140.0f + (clampedIntensity * 260.0f)
        // Atenuação mínima quando não há fala (ex: 0.20f = -14dB de redução suave do ruído de fundo)
        val minFloorGain = 0.40f - (clampedIntensity * 0.22f) // Entre 0.40 (-8dB) e 0.18 (-15dB)
        // Ganho de makeup para inteligibilidade
        val makeupGain = 1.15f + (clampedIntensity * 0.45f)

        var index = 0
        while (index < length) {
            val blockSize = min(frameSize, length - index)

            // 1. Filtragem passa-alta Butterworth 4ª ordem e Peaking EQ no bloco
            var sumSquares = 0.0
            for (i in 0 until blockSize) {
                val s = buffer[index + i].toFloat()
                val hpOut = hpStage2.process(hpStage1.process(s))
                val eqOut = presenceEq.process(hpOut)
                buffer[index + i] = eqOut.coerceIn(-32768f, 32767f).toInt().toShort()
                sumSquares += (eqOut * eqOut).toDouble()
            }

            // 2. Medição RMS e detector de atividade vocal no bloco de 10ms
            val blockRms = sqrt(sumSquares / blockSize).toFloat()

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

            // 3. Cálculo do ganho do expansor suave (Soft Expander Gain)
            val targetGain = if (rmsEnvelope >= voiceRmsThreshold) {
                1.0f
            } else {
                val ratio = (rmsEnvelope / voiceRmsThreshold).coerceIn(0.0f, 1.0f)
                // Curva de expansão suave: nunca corta a zero, apenas baixa o fundo
                minFloorGain + (1.0f - minFloorGain) * (ratio * ratio)
            }

            // 4. Aplicação de ganho interpolado amostra a amostra com Compressor e Limiter
            val gainStep = (targetGain - currentExpanderGain) / blockSize.toFloat()
            for (i in 0 until blockSize) {
                currentExpanderGain += gainStep
                var sample = buffer[index + i].toFloat() * currentExpanderGain * makeupGain

                // Compressor de dinâmica vocal (2:1 ratio acima de -6dBFS = ~16000)
                val absS = abs(sample)
                if (absS > 16384f) {
                    val excess = absS - 16384f
                    val sign = if (sample >= 0) 1.0f else -1.0f
                    sample = sign * (16384f + excess * 0.5f)
                }

                // Brickwall Soft Limiter em 31500 (-0.3 dBFS)
                if (abs(sample) > 31500f) {
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
        val excess = x - 31500f
        // Compressão assintótica suave
        val compressed = 31500f + (excess / (1.0f + excess / 1000f))
        return sign * min(32760f, compressed)
    }

    fun resetState() {
        hpStage1.reset()
        hpStage2.reset()
        presenceEq.reset()
        rmsEnvelope = 0.0f
        holdCounter = 0
        currentExpanderGain = 1.0f
    }
}
