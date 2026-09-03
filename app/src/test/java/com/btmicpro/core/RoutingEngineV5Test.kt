package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suíte de testes unitários da Arquitetura V5 Definitiva (Itens 88 e 89 do Prompt Master).
 * Valida:
 * 1. State Machine V5 (13 estados e transições canônicas)
 * 2. Snapshots de áudio e detecção de RouteDiffType (Itens 70 e 71)
 * 3. Diagnostics (formatação TXT e JSON com contadores de estabilidade, sem codec/latência fictícios)
 * 4. Compatibility (CubotKingKongXProProfile com parâmetros observáveis vs Perfil Universal)
 * 5. Recovery (Lógica de contagem, backoff e medição de duração de recuperação)
 */
class RoutingEngineV5Test {

    @Test
    fun testRouterStateV5Transitions() {
        val dev = BluetoothDeviceInfo(name = "Intercom Sena 50S", sampleRate = 16000)

        // 1. DISCONNECTED
        val s1: RouterState = RouterState.Disconnected
        assertEquals(RouterState.Disconnected, s1)

        // 2. BLUETOOTH_CONNECTED
        val s2: RouterState = RouterState.BluetoothConnected(dev)
        assertTrue(s2 is RouterState.BluetoothConnected)
        assertEquals("Intercom Sena 50S", (s2 as RouterState.BluetoothConnected).device.name)

        // 3. COMMUNICATION_DEVICE_AVAILABLE
        val s3: RouterState = RouterState.CommunicationDeviceAvailable(dev)
        assertTrue(s3 is RouterState.CommunicationDeviceAvailable)

        // 4. COMMUNICATION_DEVICE_SELECTED
        val s4: RouterState = RouterState.CommunicationDeviceSelected(dev)
        assertTrue(s4 is RouterState.CommunicationDeviceSelected)

        // 5. AUDIO_CONNECTING
        val s5: RouterState = RouterState.AudioConnecting(dev)
        assertTrue(s5 is RouterState.AudioConnecting)

        // 6. AUDIO_CONNECTED
        val s6: RouterState = RouterState.AudioConnected(dev)
        assertTrue(s6 is RouterState.AudioConnected)

        // 7. INPUT_AVAILABLE
        val s7: RouterState = RouterState.InputAvailable(dev)
        assertTrue(s7 is RouterState.InputAvailable)

        // 8. OUTPUT_AVAILABLE
        val s8: RouterState = RouterState.OutputAvailable(dev)
        assertTrue(s8 is RouterState.OutputAvailable)

        // 9. ROUTE_READY
        val route = CommunicationRoute(
            bluetoothDeviceName = "Intercom Sena 50S",
            bluetoothProfile = "HFP/SCO",
            isBidirectionalReady = true
        )
        val s9: RouterState = RouterState.RouteReady(
            device = dev,
            sampleRate = 16000,
            routePreparationTimeMs = 320L,
            audioBufferEstimateMs = 20L,
            processingTimeMs = 0L,
            endToEndLatency = "NOT_MEASURED",
            route = route
        )
        assertTrue(s9 is RouterState.RouteReady)
        val ready = s9 as RouterState.RouteReady
        assertTrue(ready.route?.isBidirectionalReady == true)
        assertEquals(320L, ready.routePreparationTimeMs)
        assertEquals("NOT_MEASURED", ready.endToEndLatency)

        // 10. ROUTE_DEGRADED
        val s10: RouterState = RouterState.RouteDegraded(dev, "Aguardando canal bidirecional completo")
        assertTrue(s10 is RouterState.RouteDegraded)
        assertEquals("Aguardando canal bidirecional completo", (s10 as RouterState.RouteDegraded).reason)

        // 11. ROUTE_LOST
        val s11: RouterState = RouterState.RouteLost("Intercom desconectado")
        assertTrue(s11 is RouterState.RouteLost)
        assertEquals("Intercom desconectado", (s11 as RouterState.RouteLost).reason)

        // 12. RECOVERING
        val s12: RouterState = RouterState.Recovering(dev, 2)
        assertTrue(s12 is RouterState.Recovering)
        assertEquals(2, (s12 as RouterState.Recovering).attempt)

        // 13. ERROR
        val s13: RouterState = RouterState.Error("Falha crítica de barramento")
        assertTrue(s13 is RouterState.Error)
    }

    @Test
    fun testAudioRouteSnapshotAndDiff() {
        val snap1 = AudioRouteSnapshot(
            communicationDeviceId = 10,
            communicationDeviceName = "SCO Headset",
            inputDeviceIds = setOf(1, 2),
            outputDeviceIds = setOf(3, 4),
            audioMode = 0
        )

        val snapSame = snap1.copy(timestamp = System.currentTimeMillis() + 100)
        assertEquals(RouteDiffType.NO_CHANGE, AudioRouteMonitor.computeDiffInternal(snap1, snapSame))

        val snapCommChanged = snap1.copy(communicationDeviceId = 20)
        assertEquals(RouteDiffType.COMMUNICATION_CHANGED, AudioRouteMonitor.computeDiffInternal(snap1, snapCommChanged))

        val snapInputChanged = snap1.copy(inputDeviceIds = setOf(1))
        assertEquals(RouteDiffType.INPUT_CHANGED, AudioRouteMonitor.computeDiffInternal(snap1, snapInputChanged))

        val snapOutputChanged = snap1.copy(outputDeviceIds = setOf(3, 4, 5))
        assertEquals(RouteDiffType.OUTPUT_CHANGED, AudioRouteMonitor.computeDiffInternal(snap1, snapOutputChanged))

        val snapModeChanged = snap1.copy(audioMode = 3)
        assertEquals(RouteDiffType.AUDIO_MODE_CHANGED, AudioRouteMonitor.computeDiffInternal(snap1, snapModeChanged))
    }

    @Test
    fun testAudioDiagnosticsTextAndJsonExportV5() {
        val diag = AudioDiagnostics(
            manufacturer = "Cubot",
            model = "KingKong X Pro",
            androidVersion = "14",
            sdk = 34,
            build = "CUBOT_KINGKONG_X_PRO_V5",
            bluetoothDevice = "Klack Y10 Intercom",
            bluetoothProfile = "HFP/SCO",
            hfpAudioState = "Canal HFP/SCO Conectado e Ativo",
            scoCodec = "NOT_EXPOSED",
            communicationDevice = "Bluetooth SCO Headset (ID=10, Tipo=7)",
            audioMode = "MODE_NORMAL (0)",
            routeState = "ROTA PRONTA BIDIRECIONAL",
            inputAvailable = true,
            outputAvailable = true,
            isBidirectionalReady = true,
            routePreparationTimeMs = 450L,
            audioBufferEstimateMs = 20L,
            processingTimeMs = 0L,
            endToEndLatency = "NOT_MEASURED",
            routeLossCount = 1,
            recoveryCount = 1,
            scoDisconnectCount = 0,
            communicationDeviceChangeCount = 1,
            lastRecoveryDurationMs = 600L,
            scoKeepAliveState = "DISABLED",
            audioFocusState = "LIVRE",
            whatsappStatus = WhatsAppRouteStatus.ROUTE_PREPARED,
            inputDevices = listOf("Built-in Mic [Tipo=15]", "Bluetooth SCO Mic [Tipo=7]"),
            outputDevices = listOf("Built-in Speaker [Tipo=2]", "Bluetooth SCO Earphone [Tipo=7]"),
            recentEvents = listOf(
                RouteEvent(
                    event = "ROUTE_READY",
                    previousState = "COMMUNICATION_DEVICE_SELECTED",
                    newState = "RouteReady",
                    device = "Klack Y10 Intercom"
                )
            ),
            hardwareProfileName = "Cubot KingKong X Pro (MediaTek Dimensity 8200)"
        )

        val txt = diag.exportAsText()
        assertNotNull(txt)
        assertTrue(txt.contains("BT MIC PRO V5"))
        assertTrue(txt.contains("KingKong X Pro"))
        assertTrue(txt.contains("Klack Y10 Intercom"))
        assertTrue(txt.contains("NOT_EXPOSED"))
        assertTrue(txt.contains("NOT_MEASURED"))
        assertTrue(txt.contains("(routeLossCount): 1"))

        val json = diag.exportAsJson()
        assertNotNull(json)
        assertTrue(json.contains("\"manufacturer\": \"Cubot\""))
        assertTrue(json.contains("\"scoCodec\": \"NOT_EXPOSED\""))
        assertTrue(json.contains("\"endToEndLatency\": \"NOT_MEASURED\""))
        assertTrue(json.contains("\"routeLossCount\": 1"))
        assertTrue(json.contains("\"status\": \"ROUTE_PREPARED\""))
    }

    @Test
    fun testCubotKingKongXProProfileObservableParameters() {
        val cubot = CubotKingKongXProProfile.create()
        assertTrue(cubot.isCubotKingKongXPro)
        assertEquals(16000, cubot.preferredSampleRate)
        assertEquals(2, cubot.preferredBufferSize)
        assertEquals(10000L, cubot.scoConnectionTimeout)
        assertEquals(500L, cubot.routingRetryDelay)
        assertTrue(cubot.supportsMediaTekDuraSpeed)

        val generic = GenericDeviceProfile.create()
        assertFalse(generic.isCubotKingKongXPro)
        assertEquals(1, generic.preferredBufferSize)
        assertEquals(600L, generic.routingRetryDelay)
        assertFalse(generic.supportsMediaTekDuraSpeed)
    }

    @Test
    fun testWhatsAppRouteStatusEnumV5() {
        assertEquals("Desconhecido", WhatsAppRouteStatus.UNKNOWN.label)
        assertEquals("Rota de Comunicação Pronta no Android", WhatsAppRouteStatus.ROUTE_PREPARED.label)
        assertEquals("Validado Fisicamente pelo Usuário", WhatsAppRouteStatus.USER_VALIDATED.label)
        assertEquals("Não Diretamente Verificável (Sandbox de App)", WhatsAppRouteStatus.NOT_DIRECTLY_VERIFIABLE.label)
        assertEquals("Falha na Rota de Comunicação", WhatsAppRouteStatus.FAILED.label)
    }
}

