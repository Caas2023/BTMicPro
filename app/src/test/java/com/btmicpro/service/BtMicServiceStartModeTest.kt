package com.btmicpro.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BtMicServiceStartModeTest {

    @Test
    fun interactiveActionRequestsMicrophoneCapture() {
        assertEquals(
            BtMicServiceStartMode.ROUTE_WITH_MICROPHONE,
            BtMicServiceStartMode.fromAction(BtMicServiceStartMode.ACTION_ROUTE_WITH_MICROPHONE)
        )
    }

    @Test
    fun automaticAndRestartStartsRemainRouteOnly() {
        assertEquals(
            BtMicServiceStartMode.ROUTE_ONLY,
            BtMicServiceStartMode.fromAction(BtMicServiceStartMode.ACTION_ROUTE_ONLY)
        )
        assertEquals(BtMicServiceStartMode.ROUTE_ONLY, BtMicServiceStartMode.fromAction(null))
        assertEquals(BtMicServiceStartMode.ROUTE_ONLY, BtMicServiceStartMode.fromAction("unknown"))
    }
}
