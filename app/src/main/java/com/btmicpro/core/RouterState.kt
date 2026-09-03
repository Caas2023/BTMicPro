package com.btmicpro.core

import android.media.AudioDeviceInfo

/**
 * Informações sobre o dispositivo de áudio Bluetooth conectado.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String = "",
    val isScoConnected: Boolean = false,
    val sampleRate: Int = 16000
)

/**
 * Estado observável do canal de áudio Bluetooth HFP/SCO.
 * Mapeamento direto de BluetoothHeadset.STATE_AUDIO_*.
 */
enum class HfpAudioState(val label: String) {
    AUDIO_DISCONNECTED("Canal HFP/SCO Desconectado"),
    AUDIO_CONNECTING("Negociando Canal HFP/SCO..."),
    AUDIO_CONNECTED("Canal HFP/SCO Conectado e Ativo")
}

/**
 * Rota de comunicação de áudio bidirecional completa (Entrada + Saída).
 */
data class CommunicationRoute(
    val inputDevice: AudioDeviceInfo? = null,
    val outputDevice: AudioDeviceInfo? = null,
    val communicationDevice: AudioDeviceInfo? = null,
    val bluetoothDeviceName: String? = null,
    val bluetoothProfile: String? = null,
    val isBidirectionalReady: Boolean = false
)

/**
 * Status observável da rota de comunicação em relação ao WhatsApp (Item 9 do Prompt Master).
 * Não finge que o processo interno do WhatsApp foi acessado via API de terceiros.
 */
enum class WhatsAppRouteStatus(val label: String) {
    UNKNOWN("Desconhecido"),
    ROUTE_PREPARED("Rota de Comunicação Pronta no Android"),
    USER_VALIDATED("Validado Fisicamente pelo Usuário"),
    NOT_DIRECTLY_VERIFIABLE("Não Diretamente Verificável (Sandbox de App)"),
    FAILED("Falha na Rota de Comunicação")
}

/**
 * Snapshot do estado de áudio do sistema para cálculo de diffs e prevenção de chamadas redundantes (Item 70, 71).
 */
data class AudioRouteSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val communicationDeviceId: Int? = null,
    val communicationDeviceName: String? = null,
    val inputDeviceIds: Set<Int> = emptySet(),
    val outputDeviceIds: Set<Int> = emptySet(),
    val bluetoothConnectedName: String? = null,
    val hfpAudioState: HfpAudioState = HfpAudioState.AUDIO_DISCONNECTED,
    val audioMode: Int = 0
)

/**
 * Diferenças detectadas entre dois snapshots de rota.
 */
enum class RouteDiffType {
    NO_CHANGE,
    DEVICE_CHANGED,
    INPUT_CHANGED,
    OUTPUT_CHANGED,
    COMMUNICATION_CHANGED,
    SCO_CHANGED,
    AUDIO_MODE_CHANGED
}

/**
 * Registro de evento para o histórico em memória de até 100 eventos (Item 61 do Prompt Master).
 */
data class RouteEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val previousState: String,
    val newState: String,
    val device: String? = null,
    val reason: String? = null
)

/**
 * Máquina de Estados Central Canônica do BT Mic Pro V5 (13 Estágios Estritos — Item 7 do Prompt Master).
 */
sealed class RouterState {

    /** 1. DISCONNECTED — Bluetooth desconectado ou serviço desativado */
    data object Disconnected : RouterState()

    /** 2. BLUETOOTH_CONNECTED — Intercom conectado via Bluetooth ACL/HFP */
    data class BluetoothConnected(val device: BluetoothDeviceInfo) : RouterState()

    /** 3. COMMUNICATION_DEVICE_AVAILABLE — Dispositivo de comunicação Bluetooth identificado pelo sistema */
    data class CommunicationDeviceAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /** 4. COMMUNICATION_DEVICE_SELECTED — Solicitação de setCommunicationDevice executada */
    data class CommunicationDeviceSelected(val device: BluetoothDeviceInfo) : RouterState()

    /** 5. AUDIO_CONNECTING — Negociação de áudio HFP/SCO em andamento */
    data class AudioConnecting(val device: BluetoothDeviceInfo) : RouterState()

    /** 6. AUDIO_CONNECTED — Canal de áudio HFP/SCO confirmado como ativo */
    data class AudioConnected(val device: BluetoothDeviceInfo) : RouterState()

    /** 7. INPUT_AVAILABLE — Microfone Bluetooth identificado e pronto nos inputs */
    data class InputAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /** 8. OUTPUT_AVAILABLE — Alto-falante Bluetooth identificado e pronto nos outputs */
    data class OutputAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 9. ROUTE_READY — Rota bidirecional completa e estável:
     * Bluetooth conectado + Communication Device ativo + Entrada pronta + Saída pronta + Áudio HFP ativo.
     * (NÃO significa verificação de processo interno do WhatsApp).
     */
    data class RouteReady(
        val device: BluetoothDeviceInfo,
        val sampleRate: Int = 16000,
        val routePreparationTimeMs: Long = 0L,
        val audioBufferEstimateMs: Long = 0L,
        val processingTimeMs: Long = 0L,
        val endToEndLatency: String = "NOT_MEASURED",
        val route: CommunicationRoute? = null
    ) : RouterState()

    /** 10. ROUTE_DEGRADED — Rota parcialmente funcional (ex: saída ativa mas entrada pendente) */
    data class RouteDegraded(val device: BluetoothDeviceInfo, val reason: String) : RouterState()

    /** 11. ROUTE_LOST — Perda de canal de áudio ou desconexão do intercom */
    data class RouteLost(val reason: String) : RouterState()

    /** 12. RECOVERING — Tentativa de restabelecimento automático com backoff exponencial */
    data class Recovering(val device: BluetoothDeviceInfo?, val attempt: Int) : RouterState()

    /** 13. ERROR — Falha crítica ou erro na rota */
    data class Error(val message: String) : RouterState()

    // Aliases para compatibilidade retroativa e transição suave
    data object Inactive : RouterState()
    data object WaitingDevice : RouterState()
    data class RoutingActive(val device: BluetoothDeviceInfo) : RouterState()
    data class AudioDeviceAvailable(val device: BluetoothDeviceInfo) : RouterState()
    data class ScoActive(val device: BluetoothDeviceInfo) : RouterState()
    data class RoutingVerified(
        val device: BluetoothDeviceInfo,
        val sampleRate: Int = 16000,
        val estimatedLatencyMs: Int = 20
    ) : RouterState()
    data class RoutingLost(val reason: String) : RouterState()
}
