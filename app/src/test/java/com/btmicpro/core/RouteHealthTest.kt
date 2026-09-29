package com.btmicpro.core

import org.junit.Assert.*
import org.junit.Test

class RouteHealthTest {
    @Test fun anotherCallWinsEvenWhenTheSameHeadsetIsSelected() {
        assertEquals(RouteAction.YIELD_TO_CALL, RouteHealth(true, true, true, true, true).action())
    }

    @Test fun ambiguousOrConflictingMicrophonesAreNotReportedAsMatched() {
        val output = AudioEndpoint(1, "AA", "Intercom")
        assertNull(matchInputEndpoint(output, listOf(AudioEndpoint(2, "BB", "Other")), 1))
        assertNull(matchInputEndpoint(output, listOf(AudioEndpoint(2, "", "Intercom"), AudioEndpoint(3, "", "Other")), 1))
        assertEquals(2, matchInputEndpoint(output, listOf(AudioEndpoint(2, "", "Intercom")), 1))
        assertEquals(3, matchInputEndpoint(output, listOf(AudioEndpoint(2, "BB", "Other"), AudioEndpoint(3, "AA", "Intercom")), 2))
    }

    @Test fun missingInputDoesNotDemandRepeatedDeviceSelection() {
        assertEquals(RouteAction.WAIT_FOR_AUDIO, RouteHealth(true, true, false, true, false).action())
    }

    @Test fun extendedMediaDelayStillYieldsImmediatelyToCapture() {
        val policy = MediaRoutePolicy()
        assertTrue(policy.shouldYield(true, true, false, 0, 2500))
        assertTrue(policy.shouldYield(true, false, false, 2000, 2500))
        assertFalse(policy.shouldYield(true, false, true, 2100, 2500))
        assertFalse(policy.shouldYield(true, false, false, 2200, 2500))
    }
}
