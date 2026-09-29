package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TransientToleranceTest {

    @Test
    fun keepIsNeverTolerated() {
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.KEEP, true, 0))
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.KEEP, false, 0))
    }

    @Test
    fun yieldToCallIsNeverTolerated() {
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.YIELD_TO_CALL, true, 0))
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.YIELD_TO_CALL, true, 2))
    }

    @Test
    fun notReadyRouteIsNeverTolerated() {
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.SELECT, false, 0))
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.WAIT_FOR_AUDIO, false, 1))
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.WAIT_FOR_DEVICE, false, 2))
    }

    @Test
    fun softFailuresToleratedBelowThreshold() {
        assertEquals(true, shouldTolerateTransientFailure(RouteAction.SELECT, true, 0))
        assertEquals(true, shouldTolerateTransientFailure(RouteAction.SELECT, true, 1))
        assertEquals(true, shouldTolerateTransientFailure(RouteAction.SELECT, true, TRANSIENT_FAILURE_TOLERANCE - 1))
        assertEquals(true, shouldTolerateTransientFailure(RouteAction.WAIT_FOR_AUDIO, true, 0))
        assertEquals(false, shouldTolerateTransientFailure(RouteAction.WAIT_FOR_DEVICE, true, 1))
    }

    @Test
    fun persistentFailuresProceedToTeardown() {
        assertEquals(
            false,
            shouldTolerateTransientFailure(RouteAction.SELECT, true, TRANSIENT_FAILURE_TOLERANCE)
        )
        assertEquals(
            false,
            shouldTolerateTransientFailure(RouteAction.WAIT_FOR_AUDIO, true, TRANSIENT_FAILURE_TOLERANCE + 2)
        )
    }
}
