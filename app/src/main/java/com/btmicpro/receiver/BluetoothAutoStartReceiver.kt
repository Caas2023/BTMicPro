package com.btmicpro.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import com.btmicpro.service.BtMicService

/**
 * Liga automaticamente o "sempre em chamada" quando o capacete/intercom Bluetooth conecta.
 * Para moto: você liga o Klack Y10 e o app já ativa sozinho, sem tirar a luva.
 *
 * Ouve: ACL_CONNECTED (qualquer BT), HEADSET profile, e SCO state.
 * Só ativa se o usuário já tinha deixado o modo ligado (KEY_ROUTER_ENABLED).
 */
class BluetoothAutoStartReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        Log.d(TAG, "BluetoothAutoStartReceiver: $action")

        // Filtra apenas eventos de conexão
        val isConnectionEvent = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> true
            else -> false
        }
        if (!isConnectionEvent) return

        // Para SCO, só reage se conectou
        if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            if (state != AudioManager.SCO_AUDIO_STATE_CONNECTED) return
        }

        try {
            val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val shouldAutoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
            val wasRouterEnabled = prefs.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)

            Log.d(TAG, "shouldAutoStart=$shouldAutoStart wasRouterEnabled=$wasRouterEnabled")

            if (!shouldAutoStart || !wasRouterEnabled) {
                Log.d(TAG, "Auto-start desabilitado ou router estava desligado - ignorando")
                return
            }

            // Verifica se tem dispositivo BT de comunicação realmente conectado
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val hasBtDevice = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    audioManager.availableCommunicationDevices.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    }
                } else {
                    audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    }
                }
            } catch (e: Exception) { true } // se falhar, assume que tem BT e tenta

            // Também verifica via BT device do intent
            val btDevice: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val deviceName = btDevice?.name ?: "desconhecido"
            Log.d(TAG, "BT conectado: $deviceName hasBtDevice=$hasBtDevice")

            Log.d(TAG, "Capacete conectado - iniciando BtMicService automaticamente")
            BtMicService.start(context)

        } catch (e: Exception) {
            Log.e(TAG, "Erro no auto-start", e)
        }
    }

    companion object {
        private const val TAG = "BTAutoStart"
    }
}
