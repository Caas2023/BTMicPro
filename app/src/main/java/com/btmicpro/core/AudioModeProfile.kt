package com.btmicpro.core

import android.media.AudioManager

enum class KeepAliveStrategy { NONE, STREAM_VOICE, STREAM_SONIFICATION, STATIC_VOICE }

/** Experimentos selecionáveis: os parâmetros de roteamento também são registrados nos logs. */
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
    val mediaResumeDelayMs: Long = 1500L
) {
    X_PRO_TEST(
        "x_pro_test", "KingKong X Pro (Experimental)", "Voz sustentada + escuta automática",
        "Teste A: silêncio de voz em fluxo contínuo, 16 kHz e buffer de 200 ms. Mantém MODE_NORMAL; libera SCO para mídia sem captura ativa e retoma após 1,5 s. Compare com os modos 6 e 8."
    ),
    STANDARD(
        "standard", "Standard (Referência)", "Voz sustentada sem liberação para mídia",
        "Referência: mantém a solicitação Bluetooth e o silêncio de voz mesmo durante mídia. MODE_NORMAL. Serve para comparar o efeito da liberação automática; retorno local usa atributos de notificação.",
        releaseForMedia = false
    ),
    MODE_2(
        "mode_2", "Modo 2 (Comunicação + sustentação)", "Modo VoIP solicitado uma vez + silêncio",
        "Testa MODE_IN_COMMUNICATION junto do silêncio de voz. Não reafirma o modo a cada queda. Libera durante mídia sem captura ativa. O Android/WhatsApp pode tratar este experimento como chamada.",
        targetAudioMode = AudioManager.MODE_IN_COMMUNICATION
    ),
    MODE_3(
        "mode_3", "Modo 3 (Rota sem sustentação)", "Controle: MODE_NORMAL sem AudioTrack",
        "Solicita somente a rota, sem gerar silêncio. Compara a política de inatividade do Android com os modos sustentados. O canal pode expirar em cerca de seis segundos no X Pro.",
        keepAliveStrategy = KeepAliveStrategy.NONE
    ),
    MODE_4(
        "mode_4", "Modo 4 (Sonificação)", "Silêncio com atributos de sonificação",
        "Testa USAGE_ASSISTANCE_SONIFICATION em vez de voz, com saída Bluetooth preferida. Mantém MODE_NORMAL e liberação automática para mídia. Retorno local também usa sonificação.",
        keepAliveStrategy = KeepAliveStrategy.STREAM_SONIFICATION
    ),
    MODE_5(
        "mode_5", "Modo 5 (Comunicação sem silêncio)", "Modo VoIP solicitado uma vez; sem AudioTrack",
        "Compara a estratégia antiga do modo 5: MODE_IN_COMMUNICATION e nenhum silêncio de sustentação. Solicita modo uma vez por ativação, sem disputa periódica. Retorno local usa fonte VOICE_COMMUNICATION.",
        targetAudioMode = AudioManager.MODE_IN_COMMUNICATION,
        keepAliveStrategy = KeepAliveStrategy.NONE
    ),
    MODE_6(
        "mode_6", "Modo 6 (Silêncio em loop)", "Buffer estático: sem thread de escrita contínua",
        "Testa AudioTrack MODE_STATIC com silêncio em loop de um segundo. Elimina pausas do produtor PCM como variável. Mantém MODE_NORMAL e libera para mídia sem captura ativa.",
        keepAliveStrategy = KeepAliveStrategy.STATIC_VOICE
    ),
    MODE_7(
        "mode_7", "Modo 7 (PCM 8 kHz)", "Sustentação de voz a 8 kHz",
        "Compara o formato PCM do AudioTrack em 8 kHz com os 16 kHz do X Pro experimental. Não força codec HFP. MODE_NORMAL, buffer de 200 ms e liberação automática para mídia.",
        keepAliveSampleRate = 8000
    ),
    MODE_8(
        "mode_8", "Modo 8 (Maior margem)", "Buffer de 500 ms + retomada após 2,5 s",
        "Testa tolerância ao escalonamento com silêncio de voz em buffer maior e espera mais longa entre áudios recebidos. MODE_NORMAL; a captura ativa visível tem prioridade sobre a liberação de mídia.",
        keepAliveBufferMs = 500,
        mediaResumeDelayMs = 2500L
    );

    val useSilenceKeepAlive: Boolean get() = keepAliveStrategy != KeepAliveStrategy.NONE

    companion object {
        fun fromCode(code: String?): AudioModeProfile = entries.find { it.code == code } ?: STANDARD
    }
}
