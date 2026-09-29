package com.btmicpro.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosticsEscapingTest {
    private fun diagnostics(device: String) = AudioDiagnostics(
        manufacturer = "Teste", model = "Teste", androidVersion = "14", sdk = 34,
        build = "Teste", bluetoothDevice = device, bluetoothProfile = "HFP/SCO",
        hfpAudioState = "NOT_EXPOSED", communicationDevice = device,
        audioMode = "MODE_NORMAL", routeState = "Teste",
        inputAvailable = false, outputAvailable = false, isBidirectionalReady = false,
        inputDevices = listOf(device), outputDevices = listOf(device),
        recentEvents = listOf(RouteEvent(event = device, previousState = device, newState = device, reason = device))
    )

    @Test
    fun deviceNamesAndErrorsEscapeControlCharacters() {
        val json = diagnostics("Fone\"\\\r\n\t\b\u000c\u0000").exportAsJson()
        assertTrue(json.contains("Fone\\\"\\\\\\r\\n\\t\\b\\u000c\\u0000"))
        assertFalse(json.contains('\r'))
        assertFalse(json.contains('\t'))
        assertFalse(json.contains('\u0000'))
    }

    @Test
    fun everyJsonControlCharacterIsEscapedAndUnicodeIsPreserved() {
        val controls = (0..31).map { it.toChar() }.joinToString("")
        val json = diagnostics("Capacete São Paulo 🎧$controls").exportAsJson()
        assertTrue(json.contains("Capacete São Paulo 🎧"))
        for (control in controls) {
            if (control != '\n') assertFalse(json.contains(control))
        }
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\u001f"))
    }
}
