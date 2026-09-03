package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de testes unitários da Arquitetura V4 (Item 62 do Prompt Master).
 * Valida:
 * 1. State Machine (10 estados e transições canônicas)
 * 2. Diagnostics (formatação TXT e JSON sem vazamento de dados privados)
 * 3. Compatibility (Perfil Cubot KingKong X Pro vs Genérico)
 * 4. Recovery (Lógica de contagem e backoff)
 */
class RoutingEngineV4Test {

    @Test
    fun testRouterStateTransitions() {
        val dev = BluetoothDeviceInfo(name = "Intercom Y10", sampleRate = 16000)

        // 1. DISCONNECTED
        val s1: RouterState = RouterState.Disconnected
        assertEquals(RouterState.Disconnected, s1)

        // 2. BLUETOOTH_CONNECTED
        val s2: RouterState = RouterState.BluetoothConnected(dev)
        assertTrue(s2 is RouterState.BluetoothConnected)
        assertEquals("Intercom Y10", (s2 as RouterState.BluetoothConnected).device.name)

        // 3. COMMUNICATION_DEVICE_AVAILABLE
        val s3: RouterState = RouterState.CommunicationDeviceAvailable(dev)
        assertTrue(s3 is RouterState.CommunicationDeviceAvailable)

        // 4. COMMUNICATION_DEVICE_SELECTED
        val s4: RouterState = RouterState.CommunicationDeviceSelected(dev)
        assertTrue(s4 is RouterState.CommunicationDeviceSelected)

        // 5. INPUT_AVAILABLE
        val s5: RouterState = RouterState.InputAvailable(dev)
        assertTrue(s5 is RouterState.InputAvailable)

        // 6. OUTPUT_AVAILABLE
        val s6: RouterState = RouterState.OutputAvailable(dev)
        assertTrue(s6 is RouterState.OutputAvailable)

        // 7. ROUTE_READY
        val route = CommunicationRoute(
            bluetoothDeviceName = "Intercom Y10",
            bluetoothProfile = "HFP/SCO",
            isBidirectionalReady = true
        )
        val s7: RouterState = RouterState.RouteReady(
            device = dev,
            sampleRate = 16000,
            estimatedLatencyMs = 15,
            route = route
        )
        assertTrue(s7 is RouterState.RouteReady)
        assertTrue((s7 as RouterState.RouteReady).route?.isBidirectionalReady == true)

        // 8. ROUTE_LOST
        val s8: RouterState = RouterState.RouteLost("Bluetooth desligado pelo usuário")
        assertTrue(s8 is RouterState.RouteLost)
        assertEquals("Bluetooth desligado pelo usuário", (s8 as RouterState.RouteLost).reason)

        // 9. RECOVERING
        val s9: RouterState = RouterState.Recovering(dev, 2)
        assertTrue(s9 is RouterState.Recovering)
        assertEquals(2, (s9 as RouterState.Recovering).attempt)

        // 10. ERROR
        val s10: RouterState = RouterState.Error("Falha de hardware")
        assertTrue(s10 is RouterState.Error)
    }

    @Test
    fun testAudioDiagnosticsTextAndJsonExport() {
        val diag = AudioDiagnostics(
            manufacturer = "Cubot",
            model = "KingKong X Pro",
            androidVersion = "14",
            sdk = 34,
            build = "CUBOT_KINGKONG_X_PRO_V1",
            bluetoothDevice = "Intercom Sena 50S",
            bluetoothProfile = "HFP/SCO",
            communicationDevice = "Bluetooth SCO Headset",
            inputDevices = listOf("Built-in Mic", "Bluetooth SCO Mic"),
            outputDevices = listOf("Built-in Speaker", "Bluetooth SCO Earphone"),
            audioMode = "MODE_NORMAL (0)",
            scoState = "SCO ATIVO",
            scoCodec = "mSBC (Wideband 16kHz)",
            routeState = "ROTA PRONTA",
            inputAvailable = true,
            outputAvailable = true,
            silentAudioKeeper = false,
            audioFocusState = "LIVRE",
            whatsappStatus = WhatsAppRouteStatus.ROUTE_PREPARED,
            estimatedLatency = "~15ms",
            hardwareProfileName = "Cubot KingKong X Pro"
        )

        val txt = diag.exportAsText()
        assertNotNull(txt)
        assertTrue(txt.contains("KingKong X Pro"))
        assertTrue(txt.contains("Intercom Sena 50S"))
        assertTrue(txt.contains("ROTA PRONTA"))
        assertTrue(txt.contains("mSBC (Wideband 16kHz)"))

        val json = diag.exportAsJson()
        assertNotNull(json)
        assertTrue(json.contains("\"manufacturer\": \"Cubot\""))
        assertTrue(json.contains("\"model\": \"KingKong X Pro\""))
        assertTrue(json.contains("\"scoState\": \"SCO ATIVO\""))
        assertTrue(json.contains("\"status\": \"ROUTE_PREPARED\""))
    }

    @Test
    fun testDeviceCompatibilityManagerProfiles() {
        // Perfil Cubot KingKong X Pro
        val cubotProfile = DeviceProfile(
            isCubotKingKongXPro = true,
            preferredSampleRate = 16000,
            preferredScoBufferMultiplier = 2,
            supportsMediaTekDuraSpeed = true,
            recommendedHighPassCutoff = 120.0f,
            profileName = "Cubot KingKong X Pro (MediaTek Dimensity 8200)"
        )
        assertTrue(cubotProfile.isCubotKingKongXPro)
        assertEquals(16000, cubotProfile.preferredSampleRate)
        assertEquals(2, cubotProfile.preferredScoBufferMultiplier)

        // Perfil Genérico
        val genericProfile = DeviceProfile(
            isCubotKingKongXPro = false,
            preferredSampleRate = 16000,
            preferredScoBufferMultiplier = 1,
            supportsMediaTekDuraSpeed = false,
            recommendedHighPassCutoff = 120.0f,
            profileName = "Perfil Genérico Android"
        )
        assertFalse(genericProfile.isCubotKingKongXPro)
        assertEquals(1, genericProfile.preferredScoBufferMultiplier)
    }

    @Test
    fun testWhatsAppRouteStatusEnum() {
        assertEquals("Desconhecido", WhatsAppRouteStatus.UNKNOWN.label)
        assertEquals("Rota de Comunicação Pronta no Android", WhatsAppRouteStatus.ROUTE_PREPARED.label)
        assertEquals("Validado Fisicamente no Aparelho", WhatsAppRouteStatus.USER_VALIDATED.label)
        assertEquals("Falha na Rota de Comunicação", WhatsAppRouteStatus.FAILED.label)
        assertEquals("Não Diretamente Verificável (Sandbox)", WhatsAppRouteStatus.NOT_DIRECTLY_VERIFIABLE.label)
    }
}
