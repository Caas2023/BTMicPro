package com.btmicpro.core

import android.media.AudioDeviceInfo

/**
 * Informações do dispositivo de áudio Bluetooth conectado.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String = "",
    val isScoConnected: Boolean = false,
    val sampleRate: Int = 16000
)

/**
 * Representa a rota de comunicação bidirecional completa (Entrada + Saída).
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
 * Status observável da rota de comunicação para o WhatsApp.
 */
enum class WhatsAppRouteStatus(val label: String) {
    UNKNOWN("Desconhecido"),
    ROUTE_PREPARED("Rota de Comunicação Pronta no Android"),
    USER_VALIDATED("Validado Fisicamente no Aparelho"),
    FAILED("Falha na Rota de Comunicação"),
    NOT_DIRECTLY_VERIFIABLE("Não Diretamente Verificável (Sandbox)")
}

/**
 * Máquina de Estados de Roteamento de Áudio V4 (10 Estados Canônicos).
 * 
 * Ordem do Ciclo de Vida:
 * 1. DISCONNECTED
 * 2. BLUETOOTH_CONNECTED
 * 3. COMMUNICATION_DEVICE_AVAILABLE
 * 4. COMMUNICATION_DEVICE_SELECTED
 * 5. INPUT_AVAILABLE
 * 6. OUTPUT_AVAILABLE
 * 7. ROUTE_READY
 * 8. ROUTE_LOST
 * 9. RECOVERING
 * 10. ERROR
 */
sealed class RouterState {

    /** 1. Intercom desconectado ou serviço inativo */
    data object Disconnected : RouterState()

    /** 2. Dispositivo Bluetooth conectado via ACL/HFP */
    data class BluetoothConnected(val device: BluetoothDeviceInfo) : RouterState()

    /** 3. Dispositivo de comunicação Bluetooth identificado pelo sistema operacional */
    data class CommunicationDeviceAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /** 4. Communication Device selecionado via AudioManager (setCommunicationDevice) */
    data class CommunicationDeviceSelected(val device: BluetoothDeviceInfo) : RouterState()

    /** 5. Entrada de microfone Bluetooth SCO/BLE detectada e confirmada */
    data class InputAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /** 6. Saída de áudio Bluetooth SCO/BLE detectada e confirmada */
    data class OutputAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 7. Rota bidirecional pronta e confirmada pelo sistema:
     * Entrada Bluetooth + Saída Bluetooth + Communication Device Ativo.
     */
    data class RouteReady(
        val device: BluetoothDeviceInfo,
        val sampleRate: Int = 16000,
        val estimatedLatencyMs: Int = 20,
        val route: CommunicationRoute? = null
    ) : RouterState()

    /** 8. Perda inesperada de rota ou desconexão do intercom */
    data class RouteLost(val reason: String) : RouterState()

    /** 9. Processo de recuperação com retries e backoff exponencial */
    data class Recovering(val device: BluetoothDeviceInfo?, val attempt: Int) : RouterState()

    /** 10. Erro irrecuperável */
    data class Error(val message: String) : RouterState()

    // Aliases e retrocompatibilidade com UI e componentes
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
