package com.btmicpro.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * BluetoothAudioRouter — Fachada unificada que expõe a autoridade central do BluetoothRoutingEngine V5.
 */
class BluetoothAudioRouter(
    context: Context,
    private val coroutineScope: CoroutineScope
) {
    val engine = BluetoothRoutingEngine(context, coroutineScope)

    val routerState: StateFlow<RouterState> = engine.routerState
    val currentRoute: StateFlow<CommunicationRoute> = engine.currentRoute
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> = engine.whatsappStatus

    var silentAudioKeepAliveEnabled: Boolean
        get() = engine.useExperimentalKeepAlive
        set(value) { engine.useExperimentalKeepAlive = value }

    fun startRouting() {
        engine.startEngine()
    }

    fun stopRouting() {
        engine.stopEngine()
    }

    fun evaluateAndRouteBluetoothDevice() {
        coroutineScope.launch {
            engine.evaluateAndApplyRoute()
        }
    }

    fun getConnectedBluetoothName(): String? {
        return engine.getConnectedBluetoothName()
    }

    fun markUserValidatedWhatsApp() {
        engine.markUserValidatedWhatsApp()
    }

    fun getDiagnosticsData(): AudioDiagnostics {
        return engine.getFullDiagnostics()
    }
}

