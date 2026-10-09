package com.btmicpro.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.btmicpro.core.AudioDiagnostics
import com.btmicpro.core.AudioModeProfile
import com.btmicpro.core.CommunicationRoute
import com.btmicpro.core.DeviceCompatibilityManager
import com.btmicpro.core.LiveAudioMonitor
import com.btmicpro.core.MediaBooster
import com.btmicpro.core.RiderAudioPreset
import com.btmicpro.core.RouterState
import com.btmicpro.core.WhatsAppRouteStatus
import com.btmicpro.receiver.BootReceiver
import com.btmicpro.service.BtMicService
import com.btmicpro.service.BtMicServiceStartMode
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
    val dualVolumeManager = com.btmicpro.core.DualVolumeManager(context)

    val mediaVolume: StateFlow<Int> = dualVolumeManager.mediaVolume
    val callVolume: StateFlow<Int> = dualVolumeManager.callVolume
    val maxMediaVolume: Int get() = dualVolumeManager.maxMediaVolume
    val maxCallVolume: Int get() = dualVolumeManager.maxCallVolume
    val isVolumeSyncEnabled: StateFlow<Boolean> = dualVolumeManager.isSyncEnabled

    val routerState: StateFlow<RouterState> = com.btmicpro.core.RouterStateHolder.routerState
    val isLiveMonitorEnabled: StateFlow<Boolean> = liveAudioMonitor.isMonitoring
    val liveMonitorError: StateFlow<String?> = liveAudioMonitor.lastError

    private val _isRouterEnabled = MutableStateFlow(false)
    val isRouterEnabled: StateFlow<Boolean> = _isRouterEnabled.asStateFlow()
    private var desiredRouterEnabled = false

    private val _isRawAudioMode = MutableStateFlow(false)
    val isRawAudioMode: StateFlow<Boolean> = _isRawAudioMode.asStateFlow()

    private val _denoiseIntensity = MutableStateFlow(1.0f)
    val denoiseIntensity: StateFlow<Float> = _denoiseIntensity.asStateFlow()

    private val _selectedPreset = MutableStateFlow(RiderAudioPreset.EXTREME_WIND)
    val selectedPreset: StateFlow<RiderAudioPreset> = _selectedPreset.asStateFlow()

    // Diagnóstico V4 completo e exportável
    private val _diagnostics = MutableStateFlow<AudioDiagnostics?>(null)
    val diagnostics: StateFlow<AudioDiagnostics?> = _diagnostics.asStateFlow()

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

    // Status externo compartilhado: nunca é inferido apenas pelo serviço estar vivo.
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> =
        com.btmicpro.core.RouterStateHolder.whatsappStatus

    private val _isBarModeEnabled = MutableStateFlow(true)
    val isBarModeEnabled: StateFlow<Boolean> = _isBarModeEnabled.asStateFlow()

    private val _isFloatingButtonEnabled = MutableStateFlow(false)
    val isFloatingButtonEnabled: StateFlow<Boolean> = _isFloatingButtonEnabled.asStateFlow()

    private val _barBoostLevel = MutableStateFlow(100)
    val barBoostLevel: StateFlow<Int> = _barBoostLevel.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _showPromoPopup = MutableStateFlow(false)
    val showPromoPopup: StateFlow<Boolean> = _showPromoPopup.asStateFlow()

    // Modo de Áudio (Perfis de Compatibilidade de Retorno/Sidetone)
    private val _audioModeProfile = MutableStateFlow(
        AudioModeProfile.fromCode(prefs.getString("audio_mode_profile", AudioModeProfile.defaultProfile.code))
    )
    val audioModeProfile: StateFlow<AudioModeProfile> = _audioModeProfile.asStateFlow()

    // Volume do Retorno do Capacete (0.0f = Mudo / Zerado por padrão para não ouvir a própria voz)
    private val _returnVolume = MutableStateFlow(prefs.getFloat("return_volume", 0.0f))
    val returnVolume: StateFlow<Float> = _returnVolume.asStateFlow()

    // Navegação entre Tela Principal Ultra-Clean e Configurações Avançadas
    private val _showSettingsScreen = MutableStateFlow(false)
    val showSettingsScreen: StateFlow<Boolean> = _showSettingsScreen.asStateFlow()

    init {
        _autoStartOnBoot.value = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
        // Por padrão, Melhoramento no MÁXIMO EXTREMO (1.0 = 100%)
        _denoiseIntensity.value = prefs.getFloat(BootReceiver.KEY_DENOISE_LEVEL, 1.0f)
        _isRawAudioMode.value = prefs.getBoolean("raw_audio_mode", false)
        // Modo de Áudio
        liveAudioMonitor.setAudioModeProfile(_audioModeProfile.value)

        _returnVolume.value = prefs.getFloat("return_volume", 0.0f)

        // Persiste o novo código após migrar perfis das versões 1.5.17 e anteriores.
        if (prefs.getString("audio_mode_profile", null) != _audioModeProfile.value.code) {
            prefs.edit { putString("audio_mode_profile", _audioModeProfile.value.code) }
            com.btmicpro.core.AppLogger.i("PROFILE_MIGRATED", "perfil=${_audioModeProfile.value.code}")
        }

        // Preset Vento Extremo por padrão
        val savedPresetIndex = prefs.getInt("rider_preset_index", RiderAudioPreset.EXTREME_WIND.ordinal)
        _selectedPreset.value = RiderAudioPreset.values().getOrElse(savedPresetIndex) { RiderAudioPreset.EXTREME_WIND }

        _isBarModeEnabled.value = prefs.getBoolean("bar_mode_enabled", true)
        _isFloatingButtonEnabled.value = prefs.getBoolean("floating_button_enabled", false)
        if (_isFloatingButtonEnabled.value) {
            if (FloatingButtonService.isOverlayGranted(context)) {
                FloatingButtonService.start(context)
            } else {
                // Não abrir Configurações de sobreposição automaticamente ao iniciar o app.
                _isFloatingButtonEnabled.value = false
                prefs.edit { putBoolean("floating_button_enabled", false) }
                com.btmicpro.core.AppLogger.w("FLOATING_BUTTON", "Sem permissão de sobreposição; botão flutuante desativado sem abrir tela do sistema")
            }
        }
        _barBoostLevel.value = prefs.getInt("bar_boost_level", 100)
        if (_isBarModeEnabled.value) {
            mediaBooster.enableBarMode(_barBoostLevel.value)
        }

        val wasEnabled = prefs.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)
        desiredRouterEnabled = wasEnabled

        viewModelScope.launch {
            com.btmicpro.core.RouterStateHolder.isServiceRunning.collect { isRunning ->
                _isRouterEnabled.value = isRunning
                if (!isRunning) {
                    liveAudioMonitor.stopMonitoring()
                }
            }
        }

        checkPromoPopup()

    }

    fun resumeDesiredRouter() {
        if (desiredRouterEnabled &&
            !com.btmicpro.core.RouterStateHolder.isServiceRunning.value
        ) {
            startRouterService()
        }
    }

    fun setAudioModeProfile(profile: AudioModeProfile) {
        com.btmicpro.core.AppLogger.i("PROFILE_SELECTED", "de=${_audioModeProfile.value.code}; para=${profile.code}; aplicação após captura ativa terminar")
        _audioModeProfile.value = profile
        prefs.edit { putString("audio_mode_profile", profile.code) }
        liveAudioMonitor.setAudioModeProfile(profile)
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = _isRawAudioMode.value,
                initialVolume = _returnVolume.value,
                preset = _selectedPreset.value,
                audioModeProfile = profile
            )
        }
    }

    fun setRiderPreset(preset: RiderAudioPreset) {
        _selectedPreset.value = preset
        prefs.edit { putInt("rider_preset_index", preset.ordinal) }
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = _isRawAudioMode.value,
                initialVolume = _returnVolume.value,
                preset = preset,
                audioModeProfile = _audioModeProfile.value
            )
        }
    }

    fun markWhatsAppUserValidated() {
        com.btmicpro.core.RouterStateHolder.activeEngine?.markUserValidatedWhatsApp()
    }

    fun markAudioProblem() {
        com.btmicpro.core.AppLogger.w("USER_REPORT", "Usuário marcou corte/falha de áudio; perfil selecionado=${_audioModeProfile.value.code}")
        com.btmicpro.core.AppLogger.i("USER_REPORT_SNAPSHOT", exportDiagnosticsText())
    }

    fun openSettings() {
        refreshDiagnostics()
        _showSettingsScreen.value = true
    }

    fun closeSettings() {
        _showSettingsScreen.value = false
    }

    fun setReturnVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _returnVolume.value = clamped
        prefs.edit { putFloat("return_volume", clamped) }
        liveAudioMonitor.setReturnVolume(clamped)
    }

    fun openDiagnostics() {
        refreshDiagnostics()
        _showDiagnosticsDialog.value = true
    }

    fun closeDiagnostics() {
        _showDiagnosticsDialog.value = false
    }

    fun refreshDiagnostics() {
        val activeEngine = com.btmicpro.core.RouterStateHolder.activeEngine
        _diagnostics.value = if (activeEngine != null) {
            activeEngine.getFullDiagnostics()
        } else {
            val temporaryEngine = com.btmicpro.core.BluetoothRoutingEngine(context, viewModelScope)
            val data = temporaryEngine.getFullDiagnostics()
            temporaryEngine.stopEngine()
            data
        }
    }

    fun exportDiagnosticsText(): String {
        refreshDiagnostics()
        return _diagnostics.value?.exportAsText() ?: "Sem dados de diagnóstico"
    }

    fun exportDiagnosticsJson(): String {
        refreshDiagnostics()
        return _diagnostics.value?.exportAsJson() ?: "{}"
    }

    val logsList: StateFlow<List<String>> = com.btmicpro.core.AppLogger.logsState

    fun copyAllLogs(context: Context) {
        val text = com.btmicpro.core.AppLogger.getAllLogsText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("BT Mic Pro Flight Recorder", text)
        clipboard.setPrimaryClip(clip)
        com.btmicpro.core.AppLogger.i("MainViewModel", "Logs recentes copiados (${text.lines().size} linhas); histórico completo disponível no ZIP")
    }

    fun shareLogs(context: Context) {
        val diagnostics = exportDiagnosticsText()
        viewModelScope.launch {
            try {
                val file = com.btmicpro.core.AppLogger.exportLogs(diagnostics)
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("Logs BT Mic Pro", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Exportar logs de todos os dias")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                com.btmicpro.core.AppLogger.e("LOG_EXPORT", "Falha ao exportar logs", e)
                android.widget.Toast.makeText(context, "Não foi possível exportar os logs: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun clearLogs() {
        com.btmicpro.core.AppLogger.clearLogs()
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
                prefs.edit { putString("promo_date_v2", today) }
            }
            prefs.edit { putInt("promo_count_v2", count + 1) }
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
        prefs.edit { putLong("promo_last_closed", System.currentTimeMillis()) }
        viewModelScope.launch {
            delay(5 * 60 * 60 * 1000L)
            if (canShowPromoToday()) {
                _showPromoPopup.value = true
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val savedDate = prefs.getString("promo_date_v2", "")
                var count = prefs.getInt("promo_count_v2", 0)
                if (today != savedDate) {
                    count = 0
                    prefs.edit { putString("promo_date_v2", today) }
                }
                prefs.edit { putInt("promo_count_v2", count + 1) }
            }
        }
    }

    fun onResumeCheckPromo() {
        if (!_showPromoPopup.value) {
            checkPromoPopup()
        }
    }

    fun toggleRouter(enabled: Boolean) {
        com.btmicpro.core.AppLogger.i("USER_ROUTER", "enabled=$enabled")
        desiredRouterEnabled = enabled
        prefs.edit { putBoolean(BootReceiver.KEY_ROUTER_ENABLED, enabled) }
        if (enabled) {
            // Teste local e roteamento externo são modos mutuamente exclusivos.
            liveAudioMonitor.stopMonitoring()
            startRouterService()
        } else {
            stopRouterService()
        }
    }

    fun setRawAudioMode(enabled: Boolean) {
        _isRawAudioMode.value = enabled
        prefs.edit { putBoolean("raw_audio_mode", enabled) }
        if (liveAudioMonitor.isMonitoring.value) {
            liveAudioMonitor.stopMonitoring()
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = enabled,
                initialVolume = _returnVolume.value,
                preset = _selectedPreset.value,
                audioModeProfile = _audioModeProfile.value
            )
        }
    }

    fun toggleLiveMonitor(enabled: Boolean) {
        if (enabled) {
            if (_isRouterEnabled.value) toggleRouter(false)
            liveAudioMonitor.startMonitoring(
                denoiseIntensity = _denoiseIntensity.value,
                bypassDsp = _isRawAudioMode.value,
                initialVolume = _returnVolume.value,
                preset = _selectedPreset.value,
                audioModeProfile = _audioModeProfile.value
            )
        } else {
            liveAudioMonitor.stopMonitoring()
        }
    }

    fun stopLiveMonitorForBackground() {
        liveAudioMonitor.stopMonitoring()
    }

    fun toggleFloatingButton(enabled: Boolean): Boolean {
        if (enabled && !FloatingButtonService.isOverlayGranted(context)) {
            _isFloatingButtonEnabled.value = false
            prefs.edit { putBoolean("floating_button_enabled", false) }
            FloatingButtonService.stop(context)
            android.widget.Toast.makeText(context, "Botão flutuante ficou desligado: permissão de sobreposição não concedida", android.widget.Toast.LENGTH_LONG).show()
            com.btmicpro.core.AppLogger.w("FLOATING_BUTTON", "Usuário tentou ativar sem permissão; não abrimos Configurações automaticamente")
            return false
        }
        _isFloatingButtonEnabled.value = enabled
        prefs.edit { putBoolean("floating_button_enabled", enabled) }
        if (enabled) FloatingButtonService.start(context) else FloatingButtonService.stop(context)
        return true
    }

    fun toggleBarMode(enabled: Boolean) {
        _isBarModeEnabled.value = enabled
        prefs.edit { putBoolean("bar_mode_enabled", enabled) }
        if (enabled) mediaBooster.enableBarMode(_barBoostLevel.value) else mediaBooster.disableBarMode()
    }

    fun setBarBoostLevel(level: Int) {
        com.btmicpro.core.AppLogger.i("USER_BOOST", "level=$level")
        _barBoostLevel.value = level
        prefs.edit { putInt("bar_boost_level", level) }
        if (_isBarModeEnabled.value) mediaBooster.setBoostLevel(level)
    }

    private fun startRouterService() {
        if (!BtMicService.start(context, BtMicServiceStartMode.ROUTE_WITH_MICROPHONE)) {
            com.btmicpro.core.AppLogger.w(
                "MainViewModel",
                "Serviço não iniciou; a preferência foi preservada para uma nova tentativa manual"
            )
        }
    }
    private fun stopRouterService() { BtMicService.stop(context) }

    fun setDenoiseIntensity(level: Float) {
        _denoiseIntensity.value = level
        prefs.edit { putFloat(BootReceiver.KEY_DENOISE_LEVEL, level) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        _autoStartOnBoot.value = enabled
        prefs.edit { putBoolean(BootReceiver.KEY_AUTO_START, enabled) }
    }

    fun setMediaVolume(level: Int) {
        dualVolumeManager.setMediaVolume(level, showUi = true)
    }

    fun setCallVolume(level: Int) {
        dualVolumeManager.setCallVolume(level, showUi = true)
    }

    fun stepMediaVolume(up: Boolean) {
        dualVolumeManager.stepMedia(up)
    }

    fun stepCallVolume(up: Boolean) {
        dualVolumeManager.stepCall(up)
    }

    fun setVolumeSyncEnabled(enabled: Boolean) {
        dualVolumeManager.setSyncEnabled(enabled)
    }

    fun setUnifiedVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val mediaLevel = ((clamped / 100f) * maxMediaVolume).toInt().coerceIn(0, maxMediaVolume)
        val callLevel = ((clamped / 100f) * maxCallVolume).toInt().coerceIn(0, maxCallVolume)
        dualVolumeManager.setSyncEnabled(true)
        dualVolumeManager.setMediaVolume(mediaLevel, showUi = true)
        dualVolumeManager.setCallVolume(callLevel, showUi = false)
        com.btmicpro.core.AppLogger.i("USER_VOLUME", "unificado=${clamped}%; media=$mediaLevel/$maxMediaVolume; call=$callLevel/$maxCallVolume")
    }

    fun stepUnifiedVolume(up: Boolean) {
        val currentMediaPercent = ((mediaVolume.value.toFloat() / maxMediaVolume.coerceAtLeast(1)) * 100).toInt()
        val currentCallPercent = ((callVolume.value.toFloat() / maxCallVolume.coerceAtLeast(1)) * 100).toInt()
        val next = (((currentMediaPercent + currentCallPercent) / 2) + if (up) 5 else -5).coerceIn(0, 100)
        setUnifiedVolumePercent(next)
    }

    override fun onCleared() {
        super.onCleared()
        liveAudioMonitor.stopMonitoring()
        mediaBooster.release()
    }
}
