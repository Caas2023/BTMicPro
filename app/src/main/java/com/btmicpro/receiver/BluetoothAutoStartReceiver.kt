package com.btmicpro.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.btmicpro.service.BtMicService

/**
 * Liga automaticamente o "sempre em chamada" quando o capacete/intercom Bluetooth conecta.
 * Para moto: você liga o Klack Y10 e o app já ativa sozinho, sem tirar a luva.
 *
 * Ouve somente perfis de voz (HEADSET/LE Audio) e confirmação SCO.
 * Só ativa se o usuário já tinha deixado o modo ligado (KEY_ROUTER_ENABLED).
 */
class BluetoothAutoStartReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        Log.d(TAG, "BluetoothAutoStartReceiver: $action")

        val isConnected = when (action) {
            ACTION_HEADSET_CONNECTION,
            ACTION_LE_AUDIO_CONNECTION ->
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->
                intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ==
                    AudioManager.SCO_AUDIO_STATE_CONNECTED
            else -> false
        }
        if (!isConnected) return

        try {
            val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val shouldAutoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, true)
            val wasRouterEnabled = prefs.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)

            Log.d(TAG, "shouldAutoStart=$shouldAutoStart wasRouterEnabled=$wasRouterEnabled")

            if (!shouldAutoStart || !wasRouterEnabled) {
                Log.d(TAG, "Auto-start desabilitado ou router estava desligado - ignorando")
                return
            }

            val btDevice: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val deviceName = btDevice?.name ?: "desconhecido"
            Log.d(TAG, "Perfil de voz conectado: $deviceName ($action)")

            Log.d(TAG, "Capacete conectado - iniciando BtMicService automaticamente")
            BtMicService.start(context)

        } catch (e: Exception) {
            Log.e(TAG, "Erro no auto-start", e)
        }
    }

    companion object {
        private const val TAG = "BTAutoStart"
        private const val ACTION_HEADSET_CONNECTION =
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        private const val ACTION_LE_AUDIO_CONNECTION =
            "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED"
    }
}
