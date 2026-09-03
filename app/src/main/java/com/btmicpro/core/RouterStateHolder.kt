package com.btmicpro.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton responsável por centralizar o estado do roteamento Bluetooth.
 * Garante que a UI (ViewModel) e o Serviço em Primeiro Plano (BtMicService)
 * compartilhem a mesma fonte da verdade em tempo real, sem atrasos de sincronização.
 */
object RouterStateHolder {

    private val _routerState = MutableStateFlow<RouterState>(RouterState.Inactive)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    /**
     * Atualiza o estado atual do roteamento Bluetooth.
     */
    fun updateState(state: RouterState) {
        _routerState.value = state
    }

    /**
     * Atualiza o status de execução do serviço em primeiro plano.
     */
    fun updateServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
        if (!isRunning) {
            _routerState.value = RouterState.Inactive
        }
    }
}
