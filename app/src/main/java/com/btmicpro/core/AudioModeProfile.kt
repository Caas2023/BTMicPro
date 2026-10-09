package com.btmicpro.core

import android.media.AudioManager

enum class KeepAliveStrategy { NONE, STREAM_VOICE, STREAM_SONIFICATION, STATIC_VOICE }

/**
 * Perfis comparativos de compatibilidade. Os Modos 1–3 preservam, sem mudanças,
 * os parâmetros dos antigos Modos 8–10 validados no KingKong X Pro.
 * Os demais são candidatos experimentais e precisam de validação física.
 */
enum class AudioModeProfile(
    val code: String,
    val title: String,
    val subtitle: String,
    val details: String,
    val targetAudioMode: Int = AudioManager.MODE_NORMAL,
    val keepAliveStrategy: KeepAliveStrategy = KeepAliveStrategy.STREAM_VOICE,
    val releaseForMedia: Boolean = true,
    val keepAliveSampleRate: Int = 16000,
    val keepAliveBufferMs: Int = 200,
    val mediaResumeDelayMs: Long = 1500L,
    val ecoPolling: Boolean = false,
    val routeControlStableMs: Long = 1500L,
    val routeControlUnstableMs: Long = 500L,
    val watchdogIntervalMs: Long = 15000L,
    val transientRecheckMs: Long = 300L,
    val selectionTimeoutMs: Long = 10000L,
    val reassertOnTransient: Boolean = false
) {
    MODE_1_KINGKONG_STABLE(
        "kingkong_stable", "Modo 1 — KingKong estável", "Antigo Modo 8 • recomendado",
        "Perfil aprovado no KingKong X Pro + KT-1. Preserva exatamente o antigo Modo 8: MODE_NORMAL, silêncio de voz 16 kHz, buffer 500 ms, liberação para mídia e retomada após 2,5 s.",
        keepAliveBufferMs = 500,
        mediaResumeDelayMs = 2500L
    ),
    MODE_2_KINGKONG_ECO(
        "kingkong_eco", "Modo 2 — KingKong Eco", "Antigo Modo 9 • bateria",
        "Preserva exatamente o antigo Modo 9: áudio do perfil estável, verificações a cada 3 s com rota pronta e diagnóstico reduzido para economizar bateria.",
        keepAliveBufferMs = 500,
        mediaResumeDelayMs = 2500L,
        ecoPolling = true,
        routeControlStableMs = 3000L,
        routeControlUnstableMs = 1000L
    ),
    MODE_3_KINGKONG_FAST(
        "kingkong_fast", "Modo 3 — KingKong rápido", "Antigo Modo 10 • escuta rápida",
        "Preserva exatamente o antigo Modo 10: base do perfil estável, verificação de mídia a cada 250 ms e retomada do microfone após 1,2 s sem mídia.",
        keepAliveBufferMs = 500,
        mediaResumeDelayMs = 1200L,
        routeControlStableMs = 250L,
        routeControlUnstableMs = 250L
    ),
    MODE_4_SAMSUNG_SAFE(
        "samsung_safe", "Modo 4 — Samsung Safe", "One UI • estabilidade conservadora",
        "Candidato para Samsung/One UI: MODE_NORMAL, voz 16 kHz, buffer 400 ms, retomada após 2 s e seleção sem reafirmação agressiva. Evita simular chamada VoIP.",
        keepAliveBufferMs = 400,
        mediaResumeDelayMs = 2000L,
        routeControlStableMs = 1000L,
        transientRecheckMs = 500L,
        selectionTimeoutMs = 12000L
    ),
    MODE_5_XIAOMI_PERSISTENT(
        "xiaomi_persistent", "Modo 5 — Xiaomi persistente", "MIUI/HyperOS • recuperação ativa",
        "Candidato para Xiaomi/Redmi/Poco: MODE_NORMAL, buffer 600 ms, watchdog de 8 s e reafirmação somente quando uma oscilação real é observada. Pode consumir mais bateria.",
        keepAliveBufferMs = 600,
        mediaResumeDelayMs = 2500L,
        routeControlStableMs = 750L,
        routeControlUnstableMs = 400L,
        watchdogIntervalMs = 8000L,
        transientRecheckMs = 250L,
        selectionTimeoutMs = 12000L,
        reassertOnTransient = true
    ),
    MODE_6_MOTOROLA_BALANCED(
        "motorola_balanced", "Modo 6 — Motorola equilibrado", "My UX • resposta e estabilidade",
        "Candidato para Motorola: MODE_NORMAL, buffer 350 ms, retomada após 1,8 s e verificações moderadas. Mantém o WhatsApp como dono da captura.",
        keepAliveBufferMs = 350,
        mediaResumeDelayMs = 1800L,
        routeControlStableMs = 1000L,
        transientRecheckMs = 400L
    ),
    MODE_7_ANDROID_LEGACY(
        "android_legacy", "Modo 7 — Android 8–11", "SCO legado + modo comunicação",
        "Candidato para API 26–30: usa o caminho SCO legado do Android e solicita MODE_IN_COMMUNICATION uma vez. Pode fazer o WhatsApp interpretar que há chamada; use apenas em Android antigo.",
        targetAudioMode = AudioManager.MODE_IN_COMMUNICATION,
        keepAliveBufferMs = 400,
        mediaResumeDelayMs = 2200L,
        routeControlStableMs = 750L,
        routeControlUnstableMs = 400L,
        selectionTimeoutMs = 15000L
    ),
    MODE_8_ANDROID_12_UNIVERSAL(
        "android_12_universal", "Modo 8 — Android 12+ universal", "CommunicationDevice • perfil genérico",
        "Candidato universal para Android 12+: prioriza setCommunicationDevice, MODE_NORMAL, silêncio de voz 16 kHz, buffer 300 ms e retomada após 1,8 s.",
        keepAliveBufferMs = 300,
        mediaResumeDelayMs = 1800L,
        routeControlStableMs = 1000L,
        selectionTimeoutMs = 10000L
    ),
    MODE_9_WEAK_RADIO(
        "weak_radio", "Modo 9 — Rádio fraco", "Buffer e tolerância maiores",
        "Candidato para intercom ou sinal instável: buffer 800 ms, tolerância de 1 s para oscilações e retomada após 3,5 s. Prioriza estabilidade sobre velocidade.",
        keepAliveBufferMs = 800,
        mediaResumeDelayMs = 3500L,
        routeControlStableMs = 750L,
        routeControlUnstableMs = 400L,
        transientRecheckMs = 1000L,
        selectionTimeoutMs = 15000L
    ),
    MODE_10_LOW_LATENCY(
        "low_latency", "Modo 10 — Baixa latência", "Escuta genérica mais rápida",
        "Candidato rápido para outros aparelhos: buffer 200 ms, verificação a cada 200 ms e retomada após 750 ms. Pode oscilar mais e gastar mais bateria.",
        keepAliveBufferMs = 200,
        mediaResumeDelayMs = 750L,
        routeControlStableMs = 200L,
        routeControlUnstableMs = 200L,
        watchdogIntervalMs = 10000L,
        transientRecheckMs = 200L
    ),
    MODE_11_NO_KEEPALIVE(
        "no_keepalive", "Modo 11 — Sem sustentação", "Diagnóstico de Android permissivo",
        "Controle diagnóstico: seleciona a rota em MODE_NORMAL sem AudioTrack silencioso. Economiza energia, mas a rota pode expirar em aparelhos que exigem atividade do UID.",
        keepAliveStrategy = KeepAliveStrategy.NONE,
        mediaResumeDelayMs = 1500L,
        routeControlStableMs = 1000L
    ),
    MODE_12_STATIC_LOOP(
        "static_loop", "Modo 12 — Loop estático", "Sustentação sem produtor PCM contínuo",
        "Candidato para aparelhos sensíveis à thread PCM: AudioTrack MODE_STATIC com um segundo de silêncio em loop, MODE_NORMAL e retomada após 2 s.",
        keepAliveStrategy = KeepAliveStrategy.STATIC_VOICE,
        keepAliveBufferMs = 1000,
        mediaResumeDelayMs = 2000L,
        routeControlStableMs = 1000L
    ),
    MODE_13_VOIP_FALLBACK(
        "voip_fallback", "Modo 13 — VoIP fallback", "Último recurso • modo comunicação",
        "Último recurso para stacks que só abrem SCO em modo VoIP: MODE_IN_COMMUNICATION + silêncio de voz. Pode bloquear notas do WhatsApp como se houvesse chamada; não use se os demais funcionarem.",
        targetAudioMode = AudioManager.MODE_IN_COMMUNICATION,
        keepAliveBufferMs = 500,
        mediaResumeDelayMs = 2500L,
        routeControlStableMs = 750L,
        selectionTimeoutMs = 15000L
    );

    val useSilenceKeepAlive: Boolean get() = keepAliveStrategy != KeepAliveStrategy.NONE

    companion object {
        val defaultProfile: AudioModeProfile = MODE_1_KINGKONG_STABLE

        /** Migra códigos das versões 1.5.17 e anteriores sem alterar o comportamento aprovado. */
        fun fromCode(code: String?): AudioModeProfile = entries.find { it.code == code } ?: when (code) {
            "mode_8" -> MODE_1_KINGKONG_STABLE
            "mode_9" -> MODE_2_KINGKONG_ECO
            "mode_10" -> MODE_3_KINGKONG_FAST
            "x_pro_test" -> MODE_8_ANDROID_12_UNIVERSAL
            "standard" -> MODE_12_STATIC_LOOP
            "mode_2" -> MODE_13_VOIP_FALLBACK
            "mode_3" -> MODE_11_NO_KEEPALIVE
            "mode_4" -> MODE_8_ANDROID_12_UNIVERSAL
            "mode_5" -> MODE_13_VOIP_FALLBACK
            "mode_6" -> MODE_12_STATIC_LOOP
            "mode_7" -> MODE_7_ANDROID_LEGACY
            else -> defaultProfile
        }
    }
}
