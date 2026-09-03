package com.btmicpro.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btmicpro.core.AudioCaptureEngine
import com.btmicpro.core.AudioFileManager
import com.btmicpro.core.LiveAudioMonitor
import com.btmicpro.core.MediaBooster
import com.btmicpro.core.RecordingItem
import com.btmicpro.core.RecordingState
import com.btmicpro.core.RouterState
import com.btmicpro.receiver.BootReceiver
import com.btmicpro.service.BtMicService
import com.btmicpro.service.FloatingButtonService
import com.btmicpro.service.RecordingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel principal com integração do CleanVoice DSP, Roteamento Bluetooth e Live Audio Monitor.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)

    val audioFileManager = AudioFileManager(context)
    val audioCaptureEngine = AudioCaptureEngine(context, viewModelScope, audioFileManager)
    val mediaBooster = MediaBooster(context)
    val liveAudioMonitor = LiveAudioMonitor(context, viewModelScope)

    val routerState: StateFlow<RouterState> = com.btmicpro.core.RouterStateHolder.routerState
    val recordingState: StateFlow<RecordingState> = audioCaptureEngine.recordingState
    val isLiveMonitorEnabled: StateFlow<Boolean> = liveAudioMonitor.isMonitoring

    private val _isRouterEnabled = MutableStateFlow(false)
    val isRouterEnabled: StateFlow<Boolean> = _isRouterEnabled.asStateFlow()

    private val _isRawAudioMode = MutableStateFlow(false)
    val isRawAudioMode: StateFlow<Boolean> = _isRawAudioMode.asStateFlow()

    private val _denoiseIntensity = MutableStateFlow(0.85f)
    val denoiseIntensity: StateFlow<Float> = _denoiseIntensity.asStateFlow()

    private val _isBarModeEnabled = MutableStateFlow(false)
    private val _isFloatingButtonEnabled = MutableStateFlow(false)
    val isFloatingButtonEnabled: StateFlow<Boolean> = _isFloatingButtonEnabled.asStateFlow()
    val isBarModeEnabled: StateFlow<Boolean> = _isBarModeEnabled.asStateFlow()

    private val _barBoostLevel = MutableStateFlow(80)
    val barBoostLevel: StateFlow<Int> = _barBoostLevel.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _recordingsList = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordingsList: StateFlow<List<RecordingItem>> = _recordingsList.asStateFlow()

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private val _showPromoPopup = MutableStateFlow(false)
    val showPromoPopup: StateFlow<Boolean> = _showPromoPopup.asStateFlow()

    init {
        _autoStartOnBoot.value = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
        _denoiseIntensity.value = prefs.getFloat(BootReceiver.KEY_DENOISE_LEVEL, 0.85f)
        _isRawAudioMode.value = prefs.getBoolean("raw_audio_mode", false)

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

        // Observa status do serviço para sincronizar botão da UI em tempo real
        viewModelScope.launch {
            com.btmicpro.core.RouterStateHolder.isServiceRunning.collect { isRunning ->
                _isRouterEnabled.value = isRunning
                prefs.edit().putBoolean(BootReceiver.KEY_ROUTER_ENABLED, isRunning).apply()
            }
        }

        checkPromoPopup()

        if (wasEnabled && !com.btmicpro.core.RouterStateHolder.isServiceRunning.value) {
            startRouterService()
        }

        viewModelScope.launch {
            audioCaptureEngine.recordingState.collect { state ->
                if (state is RecordingState.Finished) {
                    loadRecordings()
                    RecordingService.stop(context)
                }
            }
        }

        loadRecordings()
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
            com.btmicpro.core.RouterStateHolder.updateState(RouterState.Inactive)
            stopRouterService()
        }
    }

    fun setRawAudioMode(enabled: Boolean) {
        _isRawAudioMode.value = enabled
        prefs.edit().putBoolean("raw_audio_mode", enabled).apply()
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(_denoiseIntensity.value, enabled)
        }
    }

    fun toggleLiveMonitor(enabled: Boolean) {
        if (enabled) {
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = _isRawAudioMode.value
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

    fun startRecording() {
        val currentDeviceName = when (val state = routerState.value) {
            is RouterState.RoutingActive -> state.device.name
            else -> if (_isRouterEnabled.value) "Fone Bluetooth (via Service)" else "Microfone do Sistema"
        }
        RecordingService.start(context)
        audioCaptureEngine.startRecording(currentDeviceName, _denoiseIntensity.value, _isRawAudioMode.value)
    }

    fun stopRecording() { 
        audioCaptureEngine.stopRecording()
        RecordingService.stop(context) 
    }

    fun setDenoiseIntensity(level: Float) { 
        _denoiseIntensity.value = level
        prefs.edit().putFloat(BootReceiver.KEY_DENOISE_LEVEL, level).apply() 
    }

    fun setAutoStartOnBoot(enabled: Boolean) { 
        _autoStartOnBoot.value = enabled
        prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, enabled).apply() 
    }

    fun loadRecordings() { 
        viewModelScope.launch { 
            _recordingsList.value = audioFileManager.getSavedRecordings() 
        } 
    }

    fun deleteRecording(item: RecordingItem) {
        viewModelScope.launch {
            if (_currentlyPlayingPath.value == item.filePath) { 
                audioFileManager.stopPlayback()
                _currentlyPlayingPath.value = null 
            }
            audioFileManager.deleteRecording(item.filePath)
            loadRecordings()
        }
    }

    fun shareAudioToWhatsApp(filePath: String) { 
        audioFileManager.shareAudioToWhatsApp(filePath) 
    }

    fun togglePlayback(item: RecordingItem) {
        val isPlaying = audioFileManager.togglePlayback(item.filePath) { _currentlyPlayingPath.value = null }
        _currentlyPlayingPath.value = if (isPlaying) item.filePath else null
    }

    override fun onCleared() { 
        super.onCleared()
        liveAudioMonitor.stopMonitoring()
        audioFileManager.stopPlayback()
        mediaBooster.release() 
    }
}
