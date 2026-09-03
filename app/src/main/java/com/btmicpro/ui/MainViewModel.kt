package com.btmicpro.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btmicpro.core.AudioDiagnostics
import com.btmicpro.core.BluetoothAudioRouter
import com.btmicpro.core.CommunicationRoute
import com.btmicpro.core.DeviceCompatibilityManager
import com.btmicpro.core.LiveAudioMonitor
import com.btmicpro.core.MediaBooster
import com.btmicpro.core.RiderAudioPreset
import com.btmicpro.core.RouterState
import com.btmicpro.core.WhatsAppRouteStatus
import com.btmicpro.receiver.BootReceiver
import com.btmicpro.service.BtMicService
import com.btmicpro.service.FloatingButtonService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MainViewModel V4 — Arquitetura bidirecional WhatsApp ↔ Intercom.
 *
 * Responsabilidades:
 * - Controlador e estabilizador da rota de áudio Bluetooth de comunicação.
 * - Gerenciamento de telemetria e diagnóstico em tempo real (AudioDiagnostics).
 * - Exportação de relatórios em TXT e JSON.
 * - Controle experimental de keep-alive e monitoramento ao vivo opcional.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val mediaBooster = MediaBooster(context)
    val liveAudioMonitor = LiveAudioMonitor(context, viewModelScope)

    val routerState: StateFlow<RouterState> = com.btmicpro.core.RouterStateHolder.routerState
    val isLiveMonitorEnabled: StateFlow<Boolean> = liveAudioMonitor.isMonitoring

    private val _isRouterEnabled = MutableStateFlow(false)
    val isRouterEnabled: StateFlow<Boolean> = _isRouterEnabled.asStateFlow()

    private val _isRawAudioMode = MutableStateFlow(false)
    val isRawAudioMode: StateFlow<Boolean> = _isRawAudioMode.asStateFlow()

    private val _denoiseIntensity = MutableStateFlow(0.85f)
    val denoiseIntensity: StateFlow<Float> = _denoiseIntensity.asStateFlow()

    private val _selectedPreset = MutableStateFlow(RiderAudioPreset.NORMAL)
    val selectedPreset: StateFlow<RiderAudioPreset> = _selectedPreset.asStateFlow()

    // Diagnóstico V4 completo e exportável
    private val _diagnostics = MutableStateFlow<AudioDiagnostics?>(null)
    val diagnostics: StateFlow<AudioDiagnostics?> = _diagnostics.asStateFlow()

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

    // SilentAudioKeeper experimental (Item 28)
    private val _silentKeepAliveEnabled = MutableStateFlow(false)
    val silentKeepAliveEnabled: StateFlow<Boolean> = _silentKeepAliveEnabled.asStateFlow()

    // Status do WhatsApp
    private val _whatsappStatus = MutableStateFlow(WhatsAppRouteStatus.UNKNOWN)
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> = _whatsappStatus.asStateFlow()

    private val _isBarModeEnabled = MutableStateFlow(false)
    val isBarModeEnabled: StateFlow<Boolean> = _isBarModeEnabled.asStateFlow()

    private val _isFloatingButtonEnabled = MutableStateFlow(false)
    val isFloatingButtonEnabled: StateFlow<Boolean> = _isFloatingButtonEnabled.asStateFlow()

    private val _barBoostLevel = MutableStateFlow(80)
    val barBoostLevel: StateFlow<Int> = _barBoostLevel.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _showPromoPopup = MutableStateFlow(false)
    val showPromoPopup: StateFlow<Boolean> = _showPromoPopup.asStateFlow()

    // Instância local para diagnóstico sob demanda quando o serviço não está ativo
    private val localRouter = BluetoothAudioRouter(context, viewModelScope)

    init {
        _autoStartOnBoot.value = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
        _denoiseIntensity.value = prefs.getFloat(BootReceiver.KEY_DENOISE_LEVEL, 0.85f)
        _isRawAudioMode.value = prefs.getBoolean("raw_audio_mode", false)
        _silentKeepAliveEnabled.value = prefs.getBoolean("silent_keepalive_enabled", false)

        val savedPresetIndex = prefs.getInt("rider_preset_index", 0)
        _selectedPreset.value = RiderAudioPreset.values().getOrElse(savedPresetIndex) { RiderAudioPreset.NORMAL }

        _isBarModeEnabled.value = prefs.getBoolean("bar_mode_enabled", false)
        _isFloatingButtonEnabled.value = prefs.getBoolean("floating_button_enabled", false)
        if (_isFloatingButtonEnabled.value && FloatingButtonService.isOverlayGranted(context)) {
            FloatingButtonService.start(context)
        }
        _barBoostLevel.value = prefs.getInt("bar_boost_level", 80)
        if (_isBarModeEnabled.value) {
            mediaBooster.enableBarMode(_barBoostLevel.value)
        }

        val wasEnabled = prefs.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)
        _isRouterEnabled.value = wasEnabled

        viewModelScope.launch {
            com.btmicpro.core.RouterStateHolder.isServiceRunning.collect { isRunning ->
                _isRouterEnabled.value = isRunning
                prefs.edit().putBoolean(BootReceiver.KEY_ROUTER_ENABLED, isRunning).apply()
                if (isRunning) {
                    _whatsappStatus.value = WhatsAppRouteStatus.ROUTE_PREPARED
                }
            }
        }

        checkPromoPopup()

        if (wasEnabled && !com.btmicpro.core.RouterStateHolder.isServiceRunning.value) {
            startRouterService()
        }
    }

    fun setRiderPreset(preset: RiderAudioPreset) {
        _selectedPreset.value = preset
        prefs.edit().putInt("rider_preset_index", preset.ordinal).apply()
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(_denoiseIntensity.value, _isRawAudioMode.value, preset = preset)
        }
    }

    fun toggleSilentKeepAlive(enabled: Boolean) {
        _silentKeepAliveEnabled.value = enabled
        prefs.edit().putBoolean("silent_keepalive_enabled", enabled).apply()
        localRouter.silentAudioKeepAliveEnabled = enabled
    }

    fun markWhatsAppUserValidated() {
        _whatsappStatus.value = WhatsAppRouteStatus.USER_VALIDATED
    }

    fun openDiagnostics() {
        refreshDiagnostics()
        _showDiagnosticsDialog.value = true
    }

    fun closeDiagnostics() {
        _showDiagnosticsDialog.value = false
    }

    fun refreshDiagnostics() {
        localRouter.silentAudioKeepAliveEnabled = _silentKeepAliveEnabled.value
        _diagnostics.value = localRouter.getDiagnosticsData()
    }

    fun exportDiagnosticsText(): String {
        refreshDiagnostics()
        return _diagnostics.value?.exportAsText() ?: "Sem dados de diagnóstico"
    }

    fun exportDiagnosticsJson(): String {
        refreshDiagnostics()
        return _diagnostics.value?.exportAsJson() ?: "{}"
    }

    private fun checkPromoPopup() {
        val now = System.currentTimeMillis()
        val lastClosed = prefs.getLong("promo_last_closed", 0L)
        val fiveHoursMs = 5 * 60 * 60 * 1000L

        if (lastClosed != 0L && (now - lastClosed) < fiveHoursMs) {
            _showPromoPopup.value = false
            val remaining = fiveHoursMs - (now - lastClosed)
            viewModelScope.launch {
                delay(remaining)
                if (canShowPromoToday()) {
                    _showPromoPopup.value = true
                }
            }
            return
        }

        if (canShowPromoToday()) {
            _showPromoPopup.value = true
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val savedDate = prefs.getString("promo_date_v2", "")
            var count = prefs.getInt("promo_count_v2", 0)
            if (today != savedDate) {
                count = 0
                prefs.edit().putString("promo_date_v2", today).apply()
            }
            prefs.edit().putInt("promo_count_v2", count + 1).apply()
        } else {
            _showPromoPopup.value = false
        }
    }

    private fun canShowPromoToday(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val savedDate = prefs.getString("promo_date_v2", "")
        val count = prefs.getInt("promo_count_v2", 0)
        if (today != savedDate) return true
        return count < 5
    }

    fun dismissPromoPopup() {
        _showPromoPopup.value = false
        prefs.edit().putLong("promo_last_closed", System.currentTimeMillis()).apply()
        viewModelScope.launch {
            delay(5 * 60 * 60 * 1000L)
            if (canShowPromoToday()) {
                _showPromoPopup.value = true
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val savedDate = prefs.getString("promo_date_v2", "")
                var count = prefs.getInt("promo_count_v2", 0)
                if (today != savedDate) {
                    count = 0
                    prefs.edit().putString("promo_date_v2", today).apply()
                }
                prefs.edit().putInt("promo_count_v2", count + 1).apply()
            }
        }
    }

    fun onResumeCheckPromo() {
        if (!_showPromoPopup.value) {
            checkPromoPopup()
        }
    }

    fun toggleRouter(enabled: Boolean) {
        _isRouterEnabled.value = enabled
        prefs.edit().putBoolean(BootReceiver.KEY_ROUTER_ENABLED, enabled).apply()
        if (enabled) {
            com.btmicpro.core.RouterStateHolder.updateState(RouterState.WaitingDevice)
            startRouterService()
        } else {
            com.btmicpro.core.RouterStateHolder.updateState(RouterState.Disconnected)
            stopRouterService()
        }
    }

    fun setRawAudioMode(enabled: Boolean) {
        _isRawAudioMode.value = enabled
        prefs.edit().putBoolean("raw_audio_mode", enabled).apply()
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(_denoiseIntensity.value, enabled, preset = _selectedPreset.value)
        }
    }

    fun toggleLiveMonitor(enabled: Boolean) {
        if (enabled) {
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = _isRawAudioMode.value,
                preset = _selectedPreset.value
            )
        } else {
            liveAudioMonitor.stopMonitoring()
        }
    }

    fun toggleFloatingButton(enabled: Boolean): Boolean {
        if (enabled && !FloatingButtonService.isOverlayGranted(context)) {
            FloatingButtonService.requestOverlayPermission(context)
            return false
        }
        _isFloatingButtonEnabled.value = enabled
        prefs.edit().putBoolean("floating_button_enabled", enabled).apply()
        if (enabled) FloatingButtonService.start(context) else FloatingButtonService.stop(context)
        return true
    }

    fun toggleBarMode(enabled: Boolean) {
        _isBarModeEnabled.value = enabled
        prefs.edit().putBoolean("bar_mode_enabled", enabled).apply()
        if (enabled) mediaBooster.enableBarMode(_barBoostLevel.value) else mediaBooster.disableBarMode()
    }

    fun setBarBoostLevel(level: Int) {
        _barBoostLevel.value = level
        prefs.edit().putInt("bar_boost_level", level).apply()
        if (_isBarModeEnabled.value) mediaBooster.setBoostLevel(level)
    }

    private fun startRouterService() { BtMicService.start(context) }
    private fun stopRouterService() { BtMicService.stop(context) }

    fun setDenoiseIntensity(level: Float) {
        _denoiseIntensity.value = level
        prefs.edit().putFloat(BootReceiver.KEY_DENOISE_LEVEL, level).apply()
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        _autoStartOnBoot.value = enabled
        prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, enabled).apply()
    }

    override fun onCleared() {
        super.onCleared()
        liveAudioMonitor.stopMonitoring()
        mediaBooster.release()
    }
}
