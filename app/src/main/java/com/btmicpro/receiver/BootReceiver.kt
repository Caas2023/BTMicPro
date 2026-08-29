package com.btmicpro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.btmicpro.service.BtMicService

/**
 * Receptor de inicialização do sistema operacional.
 * Reinicia o serviço de roteamento de microfone automaticamente após o boot do dispositivo,
 * caso o usuário tenha mantido o recurso ativado.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Dispositivo reiniciado. Verificando configuração de auto-start.")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldAutoStart = prefs.getBoolean(KEY_AUTO_START, true)
        val wasRouterEnabled = prefs.getBoolean(KEY_ROUTER_ENABLED, false)

        if (shouldAutoStart && wasRouterEnabled) {
            Log.d(TAG, "Reiniciando BtMicService automaticamente após o boot.")
            BtMicService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        const val PREFS_NAME = "bt_mic_pro_prefs"
        const val KEY_AUTO_START = "key_auto_start_on_boot"
        const val KEY_ROUTER_ENABLED = "key_router_enabled"
        const val KEY_DENOISE_LEVEL = "key_denoise_level"
    }
}
