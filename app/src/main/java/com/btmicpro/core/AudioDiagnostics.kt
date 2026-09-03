package com.btmicpro.core

/**
 * Modelo canônico de Diagnóstico de Áudio V5 do BT Mic Pro.
 * Contém telemetria real, contadores de quedas e tempos cronometrados (sem valores fictícios).
 */
data class AudioDiagnostics(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val build: String,

    // Bluetooth & HFP
    val bluetoothDevice: String,
    val bluetoothProfile: String,
    val hfpAudioState: String,
    val scoCodec: String = "NOT_EXPOSED",

    // Rota de Comunicação
    val communicationDevice: String,
    val audioMode: String,
    val routeState: String,
    val inputAvailable: Boolean,
    val outputAvailable: Boolean,
    val isBidirectionalReady: Boolean,

    // Métricas de Tempo e Latência (Item 12 do Prompt Master)
    val routePreparationTimeMs: Long = 0L,
    val audioBufferEstimateMs: Long = 0L,
    val processingTimeMs: Long = 0L,
    val endToEndLatency: String = "NOT_MEASURED",

    // Contadores de Quedas e Estabilidade (Item 58 do Prompt Master)
    val routeLossCount: Int = 0,
    val recoveryCount: Int = 0,
    val scoDisconnectCount: Int = 0,
    val communicationDeviceChangeCount: Int = 0,
    val lastRecoveryDurationMs: Long = 0L,

    // Keep-alive experimental
    val scoKeepAliveState: String = "DISABLED",
    val audioFocusState: String = "LIVRE",

    // WhatsApp Status (Item 9)
    val whatsappStatus: WhatsAppRouteStatus = WhatsAppRouteStatus.UNKNOWN,

    // Listas de dispositivos
    val inputDevices: List<String> = emptyList(),
    val outputDevices: List<String> = emptyList(),
    val recentEvents: List<RouteEvent> = emptyList(),

    val hardwareProfileName: String = "Universal"
) {

    /**
     * Exporta o diagnóstico completo em formato texto claro (.txt) para o usuário ou auditoria.
     */
    fun exportAsText(): String {
        return buildString {
            appendLine("=== BT MIC PRO V5 — RELATÓRIO DE TELEMETRIA E ROTA ===")
            appendLine("GERADO EM: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("--------------------------------------------------")
            appendLine("[DISPOSITIVO & HARDWARE]")
            appendLine("Fabricante: $manufacturer")
            appendLine("Modelo: $model")
            appendLine("Android: $androidVersion (SDK $sdk)")
            appendLine("Build: $build")
            appendLine("Perfil de Compatibilidade: $hardwareProfileName")
            appendLine()
            appendLine("[BLUETOOTH & HFP/SCO]")
            appendLine("Dispositivo Físico: $bluetoothDevice")
            appendLine("Perfil Conectado: $bluetoothProfile")
            appendLine("Estado de Áudio HFP: $hfpAudioState")
            appendLine("Codec Reportado: $scoCodec (Nunca inferido por taxa)")
            appendLine()
            appendLine("[ROTA DE COMUNICAÇÃO ANDROID]")
            appendLine("Communication Device: $communicationDevice")
            appendLine("Modo de Áudio SO: $audioMode")
            appendLine("Estado Central: $routeState")
            appendLine("Microfone Fone (Input): ${if (inputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}")
            appendLine("Alto-falante Fone (Output): ${if (outputAvailable) "DISPONÍVEL" else "NÃO DISPONÍVEL"}")
            appendLine("Bidirecionalidade: ${if (isBidirectionalReady) "CONFIRMADA" else "AGUARDANDO"}")
            appendLine("Audio Focus: $audioFocusState")
            appendLine("Keep-Alive Experimental: $scoKeepAliveState")
            appendLine()
            appendLine("[LATÊNCIA & TEMPOS (SEM VALORES FICTÍCIOS)]")
            appendLine("Tempo de Preparação da Rota: ${routePreparationTimeMs}ms")
            appendLine("Estimativa de Buffer de Áudio: ${audioBufferEstimateMs}ms")
            appendLine("Tempo de Processamento Interno: ${processingTimeMs}ms")
            appendLine("Latência Ponta-a-Ponta: $endToEndLatency")
            appendLine()
            appendLine("[CONTADORES DE ESTABILIDADE & QUEDAS]")
            appendLine("Quedas de Rota (routeLossCount): $routeLossCount")
            appendLine("Recuperações Executadas (recoveryCount): $recoveryCount")
            appendLine("Desconexões de SCO (scoDisconnectCount): $scoDisconnectCount")
            appendLine("Trocas de Communication Device: $communicationDeviceChangeCount")
            appendLine("Duração da Última Recuperação: ${lastRecoveryDurationMs}ms")
            appendLine()
            appendLine("[STATUS WHATSAPP (SEPARADO E REAL)]")
            appendLine("Status: ${whatsappStatus.name} (${whatsappStatus.label})")
            appendLine()
            appendLine("[ENTRADAS DETECTADAS (INPUTS)]")
            if (inputDevices.isEmpty()) appendLine("  (Nenhum dispositivo)")
            else inputDevices.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("[SAÍDAS DETECTADAS (OUTPUTS)]")
            if (outputDevices.isEmpty()) appendLine("  (Nenhum dispositivo)")
            else outputDevices.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("[HISTÓRICO RECENTE DE EVENTOS (ÚLTIMOS ${recentEvents.size})]")
            if (recentEvents.isEmpty()) appendLine("  (Nenhum evento registrado)")
            else {
                recentEvents.takeLast(15).forEach { ev ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ev.timestamp))
                    appendLine("  [$time] ${ev.event} | ${ev.previousState} -> ${ev.newState}${ev.reason?.let { " ($it)" } ?: ""}")
                }
            }
            appendLine("==================================================")
        }
    }

    /**
     * Exporta a telemetria em formato JSON puro sem dependências externas.
     */
    fun exportAsJson(): String {
        fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val inputsJson = inputDevices.joinToString(separator = ", ") { "\"${escape(it)}\"" }
        val outputsJson = outputDevices.joinToString(separator = ", ") { "\"${escape(it)}\"" }
        val eventsJson = recentEvents.takeLast(20).joinToString(separator = ",\n") { ev ->
            """    { "timestamp": ${ev.timestamp}, "event": "${escape(ev.event)}", "previous": "${escape(ev.previousState)}", "new": "${escape(ev.newState)}", "reason": "${escape(ev.reason ?: "")}" }"""
        }

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
            appendLine("    \"hfpAudioState\": \"${escape(hfpAudioState)}\",")
            appendLine("    \"scoCodec\": \"${escape(scoCodec)}\"")
            appendLine("  },")
            appendLine("  \"route\": {")
            appendLine("    \"communicationDevice\": \"${escape(communicationDevice)}\",")
            appendLine("    \"audioMode\": \"${escape(audioMode)}\",")
            appendLine("    \"state\": \"${escape(routeState)}\",")
            appendLine("    \"inputAvailable\": $inputAvailable,")
            appendLine("    \"outputAvailable\": $outputAvailable,")
            appendLine("    \"isBidirectionalReady\": $isBidirectionalReady,")
            appendLine("    \"scoKeepAliveState\": \"${escape(scoKeepAliveState)}\",")
            appendLine("    \"audioFocusState\": \"${escape(audioFocusState)}\"")
            appendLine("  },")
            appendLine("  \"latencyAndTimings\": {")
            appendLine("    \"routePreparationTimeMs\": $routePreparationTimeMs,")
            appendLine("    \"audioBufferEstimateMs\": $audioBufferEstimateMs,")
            appendLine("    \"processingTimeMs\": $processingTimeMs,")
            appendLine("    \"endToEndLatency\": \"${escape(endToEndLatency)}\"")
            appendLine("  },")
            appendLine("  \"stabilityMetrics\": {")
            appendLine("    \"routeLossCount\": $routeLossCount,")
            appendLine("    \"recoveryCount\": $recoveryCount,")
            appendLine("    \"scoDisconnectCount\": $scoDisconnectCount,")
            appendLine("    \"communicationDeviceChangeCount\": $communicationDeviceChangeCount,")
            appendLine("    \"lastRecoveryDurationMs\": $lastRecoveryDurationMs")
            appendLine("  },")
            appendLine("  \"whatsapp\": {")
            appendLine("    \"status\": \"${whatsappStatus.name}\",")
            appendLine("    \"label\": \"${escape(whatsappStatus.label)}\"")
            appendLine("  },")
            appendLine("  \"inputDevices\": [$inputsJson],")
            appendLine("  \"outputDevices\": [$outputsJson],")
            appendLine("  \"recentEvents\": [\n$eventsJson\n  ]")
            appendLine("}")
        }
    }
}
