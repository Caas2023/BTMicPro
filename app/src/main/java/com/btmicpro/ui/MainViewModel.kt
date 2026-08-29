package com.btmicpro.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btmicpro.core.AudioCaptureEngine
import com.btmicpro.core.AudioFileManager
import com.btmicpro.core.BluetoothAudioRouter
import com.btmicpro.core.RecordingItem
import com.btmicpro.core.RecordingState
import com.btmicpro.core.RouterState
import com.btmicpro.receiver.BootReceiver
import com.btmicpro.service.BtMicService
import com.btmicpro.service.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel principal responsável por gerenciar a lógica de estado, configurações,
 * histórico de gravações e comunicação com os serviços de áudio do BT Mic Pro.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)

    // Componentes Core
    val audioFileManager = AudioFileManager(context)
    val audioRouter = BluetoothAudioRouter(context, viewModelScope)
    val audioCaptureEngine = AudioCaptureEngine(context, viewModelScope, audioFileManager)

    // Estados Reativos
    val routerState: StateFlow<RouterState> = audioRouter.routerState
    val recordingState: StateFlow<RecordingState> = audioCaptureEngine.recordingState

    private val _isRouterEnabled = MutableStateFlow(false)
    val isRouterEnabled: StateFlow<Boolean> = _isRouterEnabled.asStateFlow()

    private val _isRawAudioMode = MutableStateFlow(false)
    val isRawAudioMode: StateFlow<Boolean> = _isRawAudioMode.asStateFlow()

    private val _denoiseIntensity = MutableStateFlow(0.85f)
    val denoiseIntensity: StateFlow<Float> = _denoiseIntensity.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(true)
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _recordingsList = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordingsList: StateFlow<List<RecordingItem>> = _recordingsList.asStateFlow()

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private val _showPromoPopup = MutableStateFlow(false)
    val showPromoPopup: StateFlow<Boolean> = _showPromoPopup.asStateFlow()

    init {
        // Carrega configurações persistidas
        _autoStartOnBoot.value = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
        _denoiseIntensity.value = prefs.getFloat(BootReceiver.KEY_DENOISE_LEVEL, 0.85f)
        _isRawAudioMode.value = prefs.getBoolean("raw_audio_mode", false)
        val wasEnabled = prefs.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)
        _isRouterEnabled.value = wasEnabled

        checkPromoPopup()

        if (wasEnabled) {
            startRouterService()
        }

        // Observa término de gravação para atualizar lista automaticamente
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
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val savedDate = prefs.getString("promo_date", "")
        var count = prefs.getInt("promo_count", 0)

        if (today != savedDate) {
            count = 0
            prefs.edit().putString("promo_date", today).apply()
        }

        if (count < 2) {
            _showPromoPopup.value = true
            prefs.edit().putInt("promo_count", count + 1).apply()
        }
    }

    fun dismissPromoPopup() {
        _showPromoPopup.value = false
    }

    /**
     * Alterna o estado do roteamento do microfone (Modo WhatsApp).
     */
    fun toggleRouter(enabled: Boolean) {
        _isRouterEnabled.value = enabled
        prefs.edit().putBoolean(BootReceiver.KEY_ROUTER_ENABLED, enabled).apply()

        if (enabled) {
            startRouterService()
        } else {
            stopRouterService()
        }
    }

    fun setRawAudioMode(enabled: Boolean) {
        _isRawAudioMode.value = enabled
        prefs.edit().putBoolean("raw_audio_mode", enabled).apply()
    }

    private fun startRouterService() {
        BtMicService.start(context)
        audioRouter.startRouting()
    }

    private fun stopRouterService() {
        BtMicService.stop(context)
        audioRouter.stopRouting()
    }

    /**
     * Inicia a gravação com IA / DSP.
     */
    fun startRecording() {
        val currentDeviceName = when (val state = routerState.value) {
            is RouterState.RoutingActive -> state.device.name
            else -> "Microfone do Sistema"
        }
        RecordingService.start(context)
        audioCaptureEngine.startRecording(
            deviceName = currentDeviceName,
            noiseDenoiseLevel = _denoiseIntensity.value,
            rawAudioMode = _isRawAudioMode.value
        )
    }

    /**
     * Para a gravação com IA / DSP.
     */
    fun stopRecording() {
        audioCaptureEngine.stopRecording()
        RecordingService.stop(context)
    }

    /**
     * Ajusta o nível de agressividade da redução de ruído (0.0f a 1.0f).
     */
    fun setDenoiseIntensity(level: Float) {
        _denoiseIntensity.value = level
        prefs.edit().putFloat(BootReceiver.KEY_DENOISE_LEVEL, level).apply()
    }

    /**
     * Configura o auto-start no boot do sistema.
     */
    fun setAutoStartOnBoot(enabled: Boolean) {
        _autoStartOnBoot.value = enabled
        prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, enabled).apply()
    }

    /**
     * Recarrega a lista de gravações salvas.
     */
    fun loadRecordings() {
        viewModelScope.launch {
            _recordingsList.value = audioFileManager.getSavedRecordings()
        }
    }

    /**
     * Exclui um registro de gravação.
     */
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

    /**
     * Envia o áudio gravado e tratado diretamente para o WhatsApp.
     */
    fun shareAudioToWhatsApp(filePath: String) {
        audioFileManager.shareAudioToWhatsApp(filePath)
    }

    /**
     * Alterna a reprodução de um item de gravação no player embutido.
     */
    fun togglePlayback(item: RecordingItem) {
        val isPlaying = audioFileManager.togglePlayback(item.filePath) {
            _currentlyPlayingPath.value = null
        }
        _currentlyPlayingPath.value = if (isPlaying) item.filePath else null
    }

    override fun onCleared() {
        super.onCleared()
        audioFileManager.stopPlayback()
    }
}
