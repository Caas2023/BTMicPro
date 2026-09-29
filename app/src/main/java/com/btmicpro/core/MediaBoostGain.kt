package com.btmicpro.core

/** Converte o controle de 0–100% para o ganho alvo de 0–8 dB do LoudnessEnhancer. */
internal fun targetGainMillibels(level: Int): Int = level.coerceIn(0, 100) * 8
