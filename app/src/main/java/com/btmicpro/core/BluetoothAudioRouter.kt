package com.btmicpro.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gerenciador responsável por forçar o roteamento do microfone do sistema operacional
 * para fones de ouvido Bluetooth (Bluetooth SCO / Communication Device).
 *
 * Garante que aplicativos de terceiros (como WhatsApp, Telegram e gravadores)
 * utilizem a entrada de microfone do fone de ouvido conectado em vez do microfone interno.
 */
class BluetoothAudioRouter(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routerState = MutableStateFlow<RouterState>(RouterState.Inactive)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    private var audioSwitch: AudioSwitch? = null
    private var isRouterRunning = false
    private var originalAudioMode: Int = AudioManager.MODE_NORMAL

    // Callback nativo para Android 6.0+ (API 23+)
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Novos dispositivos de áudio detectados no sistema.")
            if (isRouterRunning) {
                evaluateAndRouteBluetoothDevice()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Dispositivos de áudio desconectados.")
            if (isRouterRunning) {
                evaluateAndRouteBluetoothDevice()
            }
        }
    }

    // Receptor legado para mudanças de estado do canal SCO (Android < 12)
    private val scoStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_ERROR
                )
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        Log.d(TAG, "Canal Bluetooth SCO conectado com sucesso (Broadcast).")
                        val currentBtName = getConnectedBluetoothName() ?: "Fone Bluetooth"
                        _routerState.value = RouterState.RoutingActive(
                            BluetoothDeviceInfo(name = currentBtName, isScoConnected = true)
                        )
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        Log.d(TAG, "Canal Bluetooth SCO desconectado.")
                        if (isRouterRunning) {
                            evaluateAndRouteBluetoothDevice()
                        }
                    }
                    AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                        Log.d(TAG, "Conectando ao canal Bluetooth SCO…")
                    }
                    else -> {
                        Log.w(TAG, "Estado desconhecido ou erro no canal SCO: $state")
                    }
                }
            }
        }
    }

    /**
     * Inicia o monitoramento ativo e ativa o roteamento de microfone para fone Bluetooth.
     */
    fun startRouting() {
        if (isRouterRunning) {
            Log.d(TAG, "O roteador de microfone já está em execução.")
            return
        }

        isRouterRunning = true
        originalAudioMode = audioManager.mode
        _routerState.value = RouterState.WaitingDevice

        // 1. Configura o modo de áudio global para comunicação (DSP ativo)
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "Modo de áudio configurado para MODE_IN_COMMUNICATION.")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao definir MODE_IN_COMMUNICATION", e)
        }

        // 2. Registra o callback nativo de dispositivos
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar AudioDeviceCallback", e)
        }

        // 3. Registra o receptor de broadcast para SCO
        try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(scoStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar scoStateReceiver", e)
        }

        // 4. Inicializa e conecta via Twilio AudioSwitch para alta resiliência
        initializeAudioSwitch()

        // 5. Executa a primeira avaliação de roteamento
        evaluateAndRouteBluetoothDevice()
        
        // 6. Joga o volume pro máximo
        maximizeBluetoothVolume()
    }

    /**
     * Inicializa a biblioteca AudioSwitch para suporte adicional de abstração de hardware.
     */
    private fun initializeAudioSwitch() {
        try {
            audioSwitch = AudioSwitch(context.applicationContext, loggingEnabled = true).apply {
                start { audioDevices, selectedDevice ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val btDevice = audioDevices.find { it is AudioDevice.BluetoothHeadset }
                        if (btDevice != null) {
                            Log.d(TAG, "AudioSwitch detectou fone Bluetooth: ${btDevice.name}")
                            selectDevice(btDevice)
                            activate()
                            _routerState.value = RouterState.RoutingActive(
                                BluetoothDeviceInfo(name = btDevice.name, isScoConnected = true)
                            )
                        } else if (isRouterRunning && _routerState.value !is RouterState.RoutingActive) {
                            _routerState.value = RouterState.WaitingDevice
                        }
                    }
                }
                activate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar AudioSwitch", e)
        }
    }

    /**
     * Avalia dispositivos disponíveis e força o roteamento para a entrada de áudio Bluetooth.
     */
    fun evaluateAndRouteBluetoothDevice() {
        if (!isRouterRunning) return

        try {
            // No Android 12 (API 31) ou superior, usamos a API moderna setCommunicationDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableDevices = audioManager.availableCommunicationDevices
                val btCommunicationDevice = availableDevices.find { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }

                if (btCommunicationDevice != null) {
                    val success = audioManager.setCommunicationDevice(btCommunicationDevice)
                    if (success) {
                        val deviceName = btCommunicationDevice.productName?.toString()
                            ?: "Fone Bluetooth"
                        Log.d(TAG, "setCommunicationDevice aplicado com sucesso: $deviceName")
                        _routerState.value = RouterState.RoutingActive(
                            BluetoothDeviceInfo(name = deviceName, isScoConnected = true)
                        )
                        return
                    } else {
                        Log.w(TAG, "Falha ao aplicar setCommunicationDevice para o dispositivo.")
                    }
                } else {
                    Log.d(TAG, "Nenhum dispositivo Bluetooth de comunicação encontrado.")
                    _routerState.value = RouterState.WaitingDevice
                }
            }

            // Fallback legado para todas as versões: ativação do canal Bluetooth SCO
            startLegacyBluetoothSco()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao avaliar e rotear dispositivo Bluetooth", e)
            _routerState.value = RouterState.Error("Falha ao configurar áudio: ${e.localizedMessage}")
        }
    }

    /**
     * Aciona o canal de voz Bluetooth SCO legado.
     */
    private fun startLegacyBluetoothSco() {
        try {
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "startBluetoothSco acionado.")
            }
            val name = getConnectedBluetoothName() ?: "Fone Bluetooth"
            _routerState.value = RouterState.RoutingActive(
                BluetoothDeviceInfo(name = name, isScoConnected = true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar Bluetooth SCO legado", e)
        }
    }

    /**
     * Obtém o nome do dispositivo Bluetooth atualmente conectado.
     */
    private fun getConnectedBluetoothName(): String? {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val btInput = devices.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            btInput?.productName?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Interrompe o roteamento e restaura as configurações originais de áudio do sistema.
     */
    fun stopRouting() {
        if (!isRouterRunning) return
        isRouterRunning = false

        try {
            // 1. Limpa o dispositivo de comunicação moderno (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
                Log.d(TAG, "clearCommunicationDevice executado.")
            }

            // 2. Desativa o canal Bluetooth SCO legado
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                Log.d(TAG, "stopBluetoothSco executado.")
            }

            // 3. Encerra o AudioSwitch
            audioSwitch?.stop()
            audioSwitch = null

            // 4. Desregistra callbacks e receptores
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (ignored: Exception) {}

            try {
                context.unregisterReceiver(scoStateReceiver)
            } catch (ignored: Exception) {}

            // 5. Restaura o modo de áudio original do sistema
            audioManager.mode = originalAudioMode
            Log.d(TAG, "Modo de áudio restaurado para o valor original ($originalAudioMode).")

            _routerState.value = RouterState.Inactive
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar roteamento de áudio Bluetooth", e)
        }
    }

    private fun maximizeBluetoothVolume() {
        try {
            // Volume da chamada de voz (usado pelo SCO)
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
            
            // Volume de mídia (usado pelo A2DP)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            
            Log.d(TAG, "Volume do Bluetooth maximizado com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar maximizar o volume", e)
        }
    }

    companion object {
        private const val TAG = "BluetoothAudioRouter"
    }
}
