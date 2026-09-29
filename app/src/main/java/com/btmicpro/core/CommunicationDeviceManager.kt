package com.btmicpro.core

import android.content.Context
import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay

/** Owns route requests. Never changes audio mode, focus or private capture policy. */
@Suppress("DEPRECATION")
class CommunicationDeviceManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var modernRequestOwned = false
    private var legacyRequestOwned = false
    val hasOwnedRequest: Boolean get() = modernRequestOwned || legacyRequestOwned

    fun getAvailableCommunicationDevices(): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.filter { it.isSink && isVoiceBluetooth(it) }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.isSink && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }

    fun getCurrentCommunicationDevice(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.communicationDevice else null

    fun findBestBluetoothCommunicationDevice(preferredDeviceName: String? = null): AudioDeviceInfo? {
        val available = getAvailableCommunicationDevices()
        val currentId = getCurrentCommunicationDevice()?.id
        return available.firstOrNull { !preferredDeviceName.isNullOrBlank() && it.productName.toString() == preferredDeviceName }
            ?: available.firstOrNull { it.id == currentId }
            ?: available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: available.firstOrNull()
    }

    suspend fun selectCommunicationDeviceWithConfirmation(device: AudioDeviceInfo, timeoutMs: Long = 10000L,
                                                         forceRequest: Boolean = false): Boolean {
        if (!device.isSink || !isVoiceBluetooth(device)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return requestLegacySco()
        // A healthy selection must remain untouched, including after duplicate callbacks.
        if (!forceRequest && modernRequestOwned && audioManager.communicationDevice?.id == device.id) return true
        val accepted = audioManager.setCommunicationDevice(device)
        AppLogger.i(TAG, "SELECT: id=${device.id}; type=${device.type}; aceito=$accepted; recuperação=$forceRequest")
        if (!accepted) return false
        modernRequestOwned = true
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (audioManager.communicationDevice?.id == device.id) return true
            delay(150)
        }
    // Do not clear another app's route on timeout. Recovery observes before retrying.
        AppLogger.w(TAG, "SELECT_TIMEOUT: id=${device.id}; timeoutMs=$timeoutMs")
        return false
    }

    /**
     * Reafirma a seleção atual sem esperar confirmação e sem mexer em estado.
     * Marca-passo para stacks instáveis (ex: MediaTek): cada oscilação do SCO
     * é re-solicitada na hora, mas a UI, os contadores de queda e a notificação
     * não são derrubados — a v1.5.4 recuperava rápido por reselecionar sempre;
     * a v1.5.5 quebrou isso ao apenas ignorar. Aqui voltamos a re-solicitar
     * de imediato, porém em silêncio.
     */
    @SuppressLint("MissingPermission")
    fun reassertCommunicationDevice(device: AudioDeviceInfo): Boolean {
        if (!device.isSink || !isVoiceBluetooth(device)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return requestLegacySco()
        return try {
            audioManager.setCommunicationDevice(device).also { accepted ->
                if (accepted) modernRequestOwned = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao reafirmar rota", e)
            false
        }
    }

    fun requestLegacySco(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        if (!audioManager.isBluetoothScoAvailableOffCall) return false
        // A previous request may still be owned even after SCO disconnected.
        // Balance it before retrying so OEM stacks do not keep a stale request.
        if (legacyRequestOwned && !audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            legacyRequestOwned = false
        }
        if (!legacyRequestOwned) {
            audioManager.startBluetoothSco()
            legacyRequestOwned = true
            audioManager.isBluetoothScoOn = true
        }
        return true // Request accepted; the HFP/SCO monitor confirms actual audio separately.
    }

    fun onLegacyScoDisconnected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && legacyRequestOwned) {
            try { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false }
            catch (e: Exception) { Log.w(TAG, "Falha ao encerrar solicitação SCO desconectada", e) }
            finally { legacyRequestOwned = false }
        }
    }

    fun findMatchingInput(output: AudioDeviceInfo): AudioDeviceInfo? {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource && it.type == output.type }
        val outputAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) output.address.orEmpty() else ""
        val selected = AudioEndpoint(output.id, outputAddress, output.productName.toString())
        val candidates = inputs.map {
            val inputAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.address.orEmpty() else ""
            AudioEndpoint(it.id, inputAddress, it.productName.toString())
        }
        val outputCount = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).count { it.type == output.type }
        val matchId = matchInputEndpoint(selected, candidates, outputCount) ?: return null
        return inputs.firstOrNull { it.id == matchId }
    }

    fun clearCommunicationDevice() {
        if (modernRequestOwned || legacyRequestOwned) AppLogger.i(TAG, "CLEAR: liberando solicitação própria")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (modernRequestOwned) {
                try { audioManager.clearCommunicationDevice() }
                catch (e: Exception) { Log.w(TAG, "Falha ao liberar seleção de comunicação", e) }
                finally { modernRequestOwned = false }
            }
        } else if (legacyRequestOwned) {
            try { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false }
            catch (e: Exception) { Log.w(TAG, "Falha ao liberar SCO", e) }
            finally { legacyRequestOwned = false }
        }
    }

    companion object {
        private const val TAG = "BTMIC_COMM_MGR"
        fun isVoiceBluetooth(device: AudioDeviceInfo): Boolean =
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}
