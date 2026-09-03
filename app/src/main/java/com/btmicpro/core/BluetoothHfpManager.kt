package com.btmicpro.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BluetoothHfpManager — Responsável pelo gerenciamento direto do perfil Bluetooth HFP/Headset (Camada B).
 *
 * Responsabilidades (Itens 10, 14, 15 do Prompt Master):
 * 1. Adquirir proxy do BluetoothHeadset via BluetoothProfile.ServiceListener.
 * 2. Descobrir dispositivos Bluetooth conectados via proxy.
 * 3. Monitorar connection state e audio state (STATE_AUDIO_CONNECTING, STATE_AUDIO_CONNECTED, STATE_AUDIO_DISCONNECTED).
 * 4. Registrar codec quando a API oficial permitir; caso contrário, relatar explicitamente NOT_EXPOSED.
 * 5. Liberar proxy e desregistrar receivers no shutdown.
 */
class BluetoothHfpManager(
    private val context: Context,
    private val onAudioStateChanged: (HfpAudioState) -> Unit,
    private val onConnectionStateChanged: (BluetoothDevice?, Int) -> Unit
) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var headsetProxy: BluetoothHeadset? = null
    private var isReceiverRegistered = false

    private val _hfpAudioState = MutableStateFlow(HfpAudioState.AUDIO_DISCONNECTED)
    val hfpAudioState: StateFlow<HfpAudioState> = _hfpAudioState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                headsetProxy = proxy as? BluetoothHeadset
                Log.i(TAG, "Proxy BluetoothHeadset conectado com sucesso.")
                refreshConnectedDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                Log.w(TAG, "Proxy BluetoothHeadset desconectado pelo sistema.")
                headsetProxy = null
                _connectedDevice.value = null
                _hfpAudioState.value = HfpAudioState.AUDIO_DISCONNECTED
            }
        }
    }

    private val hfpReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    Log.d(TAG, "ACTION_CONNECTION_STATE_CHANGED: device=${device?.name} state=$state")
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        _connectedDevice.value = device
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (_connectedDevice.value?.address == device?.address) {
                            _connectedDevice.value = null
                            _hfpAudioState.value = HfpAudioState.AUDIO_DISCONNECTED
                        }
                    }
                    onConnectionStateChanged(device, state)
                }

                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val audioState = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED
                    )
                    val mapped = when (audioState) {
                        BluetoothHeadset.STATE_AUDIO_CONNECTING -> HfpAudioState.AUDIO_CONNECTING
                        BluetoothHeadset.STATE_AUDIO_CONNECTED -> HfpAudioState.AUDIO_CONNECTED
                        else -> HfpAudioState.AUDIO_DISCONNECTED
                    }
                    Log.i(TAG, "ACTION_AUDIO_STATE_CHANGED: $mapped (código=$audioState)")
                    _hfpAudioState.value = mapped
                    onAudioStateChanged(mapped)
                }
            }
        }
    }

    /**
     * Inicia o gerenciamento do proxy HFP e registra os receptores de broadcast.
     */
    fun start() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter não disponível neste aparelho.")
            return
        }

        try {
            bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter profile proxy BluetoothHeadset", e)
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            }
            try {
                context.registerReceiver(hfpReceiver, filter)
                isReceiverRegistered = true
                Log.d(TAG, "BroadcastReceiver HFP registrado com sucesso.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao registrar BroadcastReceiver HFP", e)
            }
        }
    }

    /**
     * Atualiza o dispositivo conectado inspecionando a lista do proxy do headset.
     */
    @SuppressLint("MissingPermission")
    fun refreshConnectedDevice(): BluetoothDevice? {
        val proxy = headsetProxy ?: return null
        return try {
            val devices = proxy.connectedDevices
            val primary = devices.firstOrNull()
            _connectedDevice.value = primary
            Log.d(TAG, "Dispositivo HFP primário conectado: ${primary?.name ?: "Nenhum"}")
            primary
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao consultar connectedDevices do proxy", e)
            null
        }
    }

    /**
     * Verifica se o áudio HFP/SCO está conectado via proxy.
     */
    @SuppressLint("MissingPermission")
    fun isAudioConnected(device: BluetoothDevice?): Boolean {
        val proxy = headsetProxy ?: return false
        val target = device ?: _connectedDevice.value ?: return false
        return try {
            proxy.isAudioConnected(target)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detecta o estado real do áudio Bluetooth e o codec reportado pelo SO.
     * Conforme Item 10 do Prompt Master: NUNCA inferir codec apenas por sample rate.
     * Se a API pública do Android não expuser o codec HFP de baixo nível (CVSD vs mSBC),
     * reporta estritamente NOT_EXPOSED.
     */
    @SuppressLint("MissingPermission")
    fun detectActualBluetoothAudioState(): BluetoothAudioActualState {
        val dev = _connectedDevice.value
        val isAudioActive = isAudioConnected(dev)

        // As APIs públicas padrão do AOSP (sem classes ocultas do framework) não expõem
        // o codec HFP ativo por método público direto em BluetoothHeadset antes do Android 14+.
        // Portanto, a representação honesta do sistema é NOT_EXPOSED.
        val reportedCodec = "NOT_EXPOSED"

        return BluetoothAudioActualState(
            connectedDeviceName = dev?.name,
            connectedDeviceAddress = dev?.address,
            hfpAudioState = _hfpAudioState.value,
            isAudioConnected = isAudioActive,
            reportedCodec = reportedCodec
        )
    }

    /**
     * Libera o proxy BluetoothHeadset e desregistra os listeners.
     */
    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(hfpReceiver)
            } catch (ignored: Exception) {}
            isReceiverRegistered = false
        }

        headsetProxy?.let { proxy ->
            try {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao fechar profile proxy BluetoothHeadset", e)
            }
            headsetProxy = null
        }

        _connectedDevice.value = null
        _hfpAudioState.value = HfpAudioState.AUDIO_DISCONNECTED
        Log.i(TAG, "BluetoothHfpManager encerrado.")
    }

    companion object {
        private const val TAG = "BTMIC_HFP_MGR"
    }
}

/**
 * Estado real e observável do áudio Bluetooth (sem falsas suposições).
 */
data class BluetoothAudioActualState(
    val connectedDeviceName: String?,
    val connectedDeviceAddress: String?,
    val hfpAudioState: HfpAudioState,
    val isAudioConnected: Boolean,
    val reportedCodec: String
)
