package com.btmicpro.core

/**
 * Informações do dispositivo de áudio Bluetooth conectado.
 *
 * @property name Nome legível do dispositivo (ex: "JBL Tune 520", "Fone Bluetooth").
 * @property address Endereço MAC ou identificador único do dispositivo.
 * @property isScoConnected Indica se o canal SCO (voz bidirecional) está ativo.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String = "",
    val isScoConnected: Boolean = false
)

/**
 * Representação dos estados do ciclo de vida do Roteador de Microfone Bluetooth.
 */
sealed class RouterState {
    /**
     * O serviço de roteamento está desligado. O áudio do sistema opera em modo padrão.
     */
    data object Inactive : RouterState()

    /**
     * O serviço está ativo e monitorando conexões, mas nenhum fone Bluetooth compatível foi detectado.
     */
    data object WaitingDevice : RouterState()

    /**
     * O canal de microfone Bluetooth foi roteado com sucesso e está operante.
     *
     * @property device Informações do fone Bluetooth conectado.
     */
    data class RoutingActive(val device: BluetoothDeviceInfo) : RouterState()

    /**
     * Ocorreu uma falha durante o roteamento do canal de comunicação.
     *
     * @property message Mensagem explicativa em Português do Brasil.
     */
    data class Error(val message: String) : RouterState()
}

/**
 * Representação do estado do Gravador de Áudio com IA / DSP.
 */
sealed class RecordingState {
    /**
     * Gravador inativo e pronto para iniciar nova captura.
     */
    data object Idle : RecordingState()

    /**
     * Gravação em andamento.
     *
     * @property durationMs Duração acumulada da gravação em milissegundos.
     * @property amplitude Nível atual de amplitude/volume (0.0f a 1.0f) para o medidor VU.
     * @property deviceName Nome do microfone utilizado (ex: fone Bluetooth ou interno).
     */
    data class Recording(
        val durationMs: Long,
        val amplitude: Float,
        val deviceName: String
    ) : RecordingState()

    /**
     * Gravação finalizada com sucesso.
     *
     * @property filePath Caminho absoluto do arquivo de áudio gerado.
     * @property durationMs Duração total do áudio em milissegundos.
     */
    data class Finished(
        val filePath: String,
        val durationMs: Long
    ) : RecordingState()

    /**
     * Erro durante a gravação.
     *
     * @property message Detalhes do erro em PT-BR.
     */
    data class Error(val message: String) : RecordingState()
}

/**
 * Modelo de item para o histórico de gravações salvas.
 *
 * @property id Identificador único do registro.
 * @property fileName Nome do arquivo salvo.
 * @property filePath Caminho absoluto do arquivo.
 * @property timestamp Data/hora da criação em milissegundos.
 * @property durationMs Duração em milissegundos.
 * @property isProcessedWithAi Indica se o áudio recebeu processamento avançado de ruído.
 */
data class RecordingItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val isProcessedWithAi: Boolean = true
)
