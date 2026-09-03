package com.btmicpro.core

/**
 * Informações do dispositivo de áudio Bluetooth conectado.
 *
 * @property name Nome legível do dispositivo (ex: "JBL Tune 520", "Intercomunicador Y10").
 * @property address Endereço MAC ou identificador único do dispositivo.
 * @property isScoConnected Indica se o canal SCO (voz bidirecional) está ativo.
 * @property sampleRate Taxa de amostragem em Hz (ex: 16000 mSBC ou 8000 CVSD).
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String = "",
    val isScoConnected: Boolean = false,
    val sampleRate: Int = 16000
)

/**
 * Máquina de Estados de Roteamento de Áudio Bluetooth V4 (8 Estágios).
 * 
 * Permite diagnóstico inequívoco em tempo real de cada estágio da conexão com o fone/intercomunicador.
 */
sealed class RouterState {

    /**
     * 1. O roteamento está desligado ou o fone está desconectado.
     */
    data object Disconnected : RouterState()

    /**
     * 2. O fone Bluetooth foi pareado e conectado via camada ACL.
     */
    data class BluetoothConnected(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 3. O dispositivo de áudio foi detectado pelo AudioManager do sistema operacional.
     */
    data class AudioDeviceAvailable(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 4. O dispositivo foi registrado com sucesso como Dispositivo de Comunicação preferencial (setCommunicationDevice).
     */
    data class CommunicationDeviceSelected(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 5. O canal de áudio bidirecional SCO (mSBC ou CVSD) está aberto e ativo no hardware.
     */
    data class ScoActive(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * 6. Roteamento integralmente validado: microfone Bluetooth ativo, verificado com keep-alive e pronto para WhatsApp.
     */
    data class RoutingVerified(
        val device: BluetoothDeviceInfo,
        val sampleRate: Int = 16000,
        val estimatedLatencyMs: Int = 20
    ) : RouterState()

    /**
     * 7. Ocorreu perda de conexão ou desconexão do dispositivo Bluetooth.
     */
    data class RoutingLost(val reason: String) : RouterState()

    /**
     * 8. O sistema está executando a rotina de autorrecuperação e reconexão do canal de comunicação.
     */
    data class Recovering(val device: BluetoothDeviceInfo?, val attempt: Int) : RouterState()

    // Aliases e wrappers para retrocompatibilidade com UI e componentes legados
    data object Inactive : RouterState()
    data object WaitingDevice : RouterState()
    data class RoutingActive(val device: BluetoothDeviceInfo) : RouterState()
    data class Error(val message: String) : RouterState()
}
