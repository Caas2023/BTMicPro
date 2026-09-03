package com.btmicpro.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * BluetoothAudioRouter — Fachada unificada que expõe a autoridade central do BluetoothRoutingEngine V4.
 *
 * Garante que não haja guerras de concorrência ou duplicação de chamadas ao AudioManager.
 */
class BluetoothAudioRouter(
    context: Context,
    coroutineScope: CoroutineScope
) {
    val engine = BluetoothRoutingEngine(context, coroutineScope)

    val routerState: StateFlow<RouterState> = engine.routerState
    val currentRoute: StateFlow<CommunicationRoute> = engine.currentRoute
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> = engine.whatsappStatus

    var silentAudioKeepAliveEnabled: Boolean
        get() = engine.silentAudioKeepAliveEnabled
        set(value) { engine.silentAudioKeepAliveEnabled = value }

    fun startRouting() {
        engine.startEngine()
    }

    fun stopRouting() {
        engine.stopEngine()
    }

    fun evaluateAndRouteBluetoothDevice() {
        engine.evaluateAndApplyRoute()
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
