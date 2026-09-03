package com.btmicpro.core

/**
 * Modelo canônico de Diagnóstico de Áudio V4 do BT Mic Pro.
 * Contém informações completas de telemetria do sistema de áudio para validação de rota e compatibilidade.
 */
data class AudioDiagnostics(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val build: String,

    val bluetoothDevice: String,
    val bluetoothProfile: String,

    val communicationDevice: String,

    val inputDevices: List<String>,
    val outputDevices: List<String>,

    val audioMode: String,

    val scoState: String,
    val scoCodec: String,

    val routeState: String,

    val inputAvailable: Boolean,
    val outputAvailable: Boolean,

    val silentAudioKeeper: Boolean,

    val audioFocusState: String,

    val whatsappStatus: WhatsAppRouteStatus,

    val estimatedLatency: String = "NOT MEASURED",
    val hardwareProfileName: String = "Universal"
) {

    /**
     * Exporta o diagnóstico completo em formato texto claro (.txt), sem expor áudios ou dados pessoais.
     */
    fun exportAsText(): String {
        return buildString {
            appendLine("=== BT MIC PRO — RELATÓRIO DE DIAGNÓSTICO DE ÁUDIO ===")
            appendLine("GERADO EM: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("--------------------------------------------------")
            appendLine("[DISPOSITIVO]")
            appendLine("Fabricante: $manufacturer")
            appendLine("Modelo: $model")
            appendLine("Android: $androidVersion (SDK $sdk)")
            appendLine("Build: $build")
            appendLine("Perfil de Hardware: $hardwareProfileName")
            appendLine()
            appendLine("[BLUETOOTH & INTERCOM]")
            appendLine("Dispositivo Conectado: $bluetoothDevice")
            appendLine("Perfil Bluetooth: $bluetoothProfile")
            appendLine("SCO State: $scoState")
            appendLine("Codec SCO: $scoCodec")
            appendLine()
            appendLine("[ROTA DE COMUNICAÇÃO]")
            appendLine("Communication Device: $communicationDevice")
            appendLine("Modo de Áudio: $audioMode")
            appendLine("Estado da Rota: $routeState")
            appendLine("Entrada Bluetooth Disponível: ${if (inputAvailable) "SIM" else "NÃO"}")
            appendLine("Saída Bluetooth Disponível: ${if (outputAvailable) "SIM" else "NÃO"}")
            appendLine("SilentAudioKeeper (Experimental): ${if (silentAudioKeeper) "ATIVO" else "DESATIVADO"}")
            appendLine("Audio Focus: $audioFocusState")
            appendLine("Latência de Rota Estimada: $estimatedLatency")
            appendLine()
            appendLine("[STATUS WHATSAPP]")
            appendLine("Status: ${whatsappStatus.label}")
            appendLine()
            appendLine("[DISPOSITIVOS DE ENTRADA (MIC)]")
            if (inputDevices.isEmpty()) appendLine("  (Nenhum dispositivo)")
            else inputDevices.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("[DISPOSITIVOS DE SAÍDA (FONE/SPEAKER)]")
            if (outputDevices.isEmpty()) appendLine("  (Nenhum dispositivo)")
            else outputDevices.forEach { appendLine("  • $it") }
            appendLine("==================================================")
        }
    }

    /**
     * Exporta o relatório completo em formato JSON para fácil envio e auditoria programática.
     * Gera JSON canônico puro sem depender de android.jar em tempo de teste.
     */
    fun exportAsJson(): String {
        fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val inputsJson = inputDevices.joinToString(separator = ", ") { "\"${escape(it)}\"" }
        val outputsJson = outputDevices.joinToString(separator = ", ") { "\"${escape(it)}\"" }

        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${System.currentTimeMillis()},")
            appendLine("  \"device\": {")
            appendLine("    \"manufacturer\": \"${escape(manufacturer)}\",")
            appendLine("    \"model\": \"${escape(model)}\",")
            appendLine("    \"androidVersion\": \"${escape(androidVersion)}\",")
            appendLine("    \"sdk\": $sdk,")
            appendLine("    \"build\": \"${escape(build)}\",")
            appendLine("    \"hardwareProfile\": \"${escape(hardwareProfileName)}\"")
            appendLine("  },")
            appendLine("  \"bluetooth\": {")
            appendLine("    \"device\": \"${escape(bluetoothDevice)}\",")
            appendLine("    \"profile\": \"${escape(bluetoothProfile)}\",")
            appendLine("    \"scoState\": \"${escape(scoState)}\",")
            appendLine("    \"scoCodec\": \"${escape(scoCodec)}\"")
            appendLine("  },")
            appendLine("  \"route\": {")
            appendLine("    \"communicationDevice\": \"${escape(communicationDevice)}\",")
            appendLine("    \"audioMode\": \"${escape(audioMode)}\",")
            appendLine("    \"state\": \"${escape(routeState)}\",")
            appendLine("    \"inputAvailable\": $inputAvailable,")
            appendLine("    \"outputAvailable\": $outputAvailable,")
            appendLine("    \"silentAudioKeeper\": $silentAudioKeeper,")
            appendLine("    \"audioFocusState\": \"${escape(audioFocusState)}\",")
            appendLine("    \"estimatedLatency\": \"${escape(estimatedLatency)}\"")
            appendLine("  },")
            appendLine("  \"whatsapp\": {")
            appendLine("    \"status\": \"${whatsappStatus.name}\",")
            appendLine("    \"description\": \"${escape(whatsappStatus.label)}\"")
            appendLine("  },")
            appendLine("  \"inputDevices\": [$inputsJson],")
            appendLine("  \"outputDevices\": [$outputsJson]")
            appendLine("}")
        }
    }
}
