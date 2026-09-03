package com.btmicpro.ui

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btmicpro.core.AudioCaptureEngine
import com.btmicpro.core.AudioDiagnosticsData
import com.btmicpro.core.AudioFileManager
import com.btmicpro.core.DeviceCompatibilityManager
import com.btmicpro.core.LiveAudioMonitor
import com.btmicpro.core.MediaBooster
import com.btmicpro.core.RecordingItem
import com.btmicpro.core.RecordingState
import com.btmicpro.core.RiderAudioPreset
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
 * ViewModel principal do BT Mic Pro V4.
 *
 * Integra a máquina de estados de 8 estágios, presets de áudio para motociclistas,
 * monitor ao vivo em tempo real e painel Developer Audio Diagnostics.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

    private val _selectedPreset = MutableStateFlow(RiderAudioPreset.NORMAL)
    val selectedPreset: StateFlow<RiderAudioPreset> = _selectedPreset.asStateFlow()

    private val _diagnosticsData = MutableStateFlow<AudioDiagnosticsData?>(null)
    val diagnosticsData: StateFlow<AudioDiagnosticsData?> = _diagnosticsData.asStateFlow()

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

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

        val savedPresetIndex = prefs.getInt("rider_preset_index", 0)
        _selectedPreset.value = RiderAudioPreset.values().getOrElse(savedPresetIndex) { RiderAudioPreset.NORMAL }
        audioCaptureEngine.setPreset(_selectedPreset.value)

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

    fun setRiderPreset(preset: RiderAudioPreset) {
        _selectedPreset.value = preset
        prefs.edit().putInt("rider_preset_index", preset.ordinal).apply()
        audioCaptureEngine.setPreset(preset)
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(_denoiseIntensity.value, _isRawAudioMode.value, preset = preset)
        }
    }

    fun openDiagnostics() {
        refreshDiagnostics()
        _showDiagnosticsDialog.value = true
    }

    fun closeDiagnostics() {
        _showDiagnosticsDialog.value = false
    }

    fun refreshDiagnostics() {
        val modeStr = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL (0)"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE (1)"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL (2)"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION (3)"
            AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING (4)"
            else -> "UNKNOWN (${audioManager.mode})"
        }

        val commDevStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { "${it.productName} (type=${it.type})" } ?: "Nenhum"
        } else {
            "Não suportado (API < 31)"
        }

        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { 
            "${it.productName} [tipo=${it.type}]" 
        }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { 
            "${it.productName} [tipo=${it.type}]" 
        }

        val stateDesc = when (val s = routerState.value) {
            is RouterState.RoutingVerified -> "VERIFICADO (Pronto para WhatsApp: ${s.device.name})"
            is RouterState.ScoActive -> "SCO ATIVO (${s.device.name})"
            is RouterState.CommunicationDeviceSelected -> "DISPOSITIVO SELECIONADO (${s.device.name})"
            is RouterState.AudioDeviceAvailable -> "DISPOSITIVO DE ÁUDIO DETECTADO (${s.device.name})"
            is RouterState.BluetoothConnected -> "BLUETOOTH CONECTADO (${s.device.name})"
            is RouterState.Recovering -> "RECUPERANDO (Tentativa ${s.attempt})"
            is RouterState.RoutingLost -> "CONEXÃO PERDIDA: ${s.reason}"
            is RouterState.Disconnected -> "DESCONECTADO"
            else -> "INATIVO"
        }

        val profile = DeviceCompatibilityManager.currentProfile
        _diagnosticsData.value = AudioDiagnosticsData(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            bluetoothDeviceName = getConnectedBluetoothName() ?: "Nenhum fone conectado",
            audioMode = modeStr,
            communicationDevice = commDevStr,
            inputDevices = inputs,
            outputDevices = outputs,
            sampleRate = profile.preferredSampleRate,
            isScoActive = isScoActive(),
            routingStateDescription = stateDesc,
            estimatedLatencyMs = if (profile.isCubotKingKongXPro) 15 else 20,
            isKeeperActive = _isRouterEnabled.value,
            hardwareProfile = profile.profileName
        )
    }

    private fun isScoActive(): Boolean {
        return try {
            if (audioManager.isBluetoothScoOn) return true
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) { false }
    }

    private fun getConnectedBluetoothName(): String? {
        return try {
            val devs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }?.productName?.toString() 
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.availableCommunicationDevices.find { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
                    }?.productName?.toString()
                } else null
        } catch (e: Exception) { null }
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

    fun startRecording() {
        val currentDeviceName = when (val state = routerState.value) {
            is RouterState.RoutingVerified -> state.device.name
            is RouterState.ScoActive -> state.device.name
            is RouterState.RoutingActive -> state.device.name
            else -> if (_isRouterEnabled.value) "Fone Bluetooth (via Service)" else "Microfone do Sistema"
        }
        RecordingService.start(context)
        audioCaptureEngine.startRecording(
            deviceName = currentDeviceName,
            noiseDenoiseLevel = _denoiseIntensity.value,
            rawAudioMode = _isRawAudioMode.value,
            preset = _selectedPreset.value
        )
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
