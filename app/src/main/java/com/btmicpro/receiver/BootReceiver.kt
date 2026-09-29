package com.btmicpro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.btmicpro.service.BtMicService

/**
 * Receptor de inicialização do sistema operacional.
 * Reinicia o serviço de roteamento de microfone automaticamente após boot,
 * atualização do app ou desbloqueio inicial.
 *
 * Trata BOOT_COMPLETED + MY_PACKAGE_REPLACED. Não executa antes do primeiro
 * desbloqueio, pois as preferências usam armazenamento protegido por credencial.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        Log.d(TAG, "BootReceiver acionado: $action")

        // Aceita múltiplos gatilhos de inicialização
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" || // HTC, etc
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.MY_PACKAGE_REPLACED"

        if (!isBootEvent) return

        Log.d(TAG, "Dispositivo reiniciado/atualizado. Verificando configuração de auto-start.")

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val shouldAutoStart = prefs.getBoolean(KEY_AUTO_START, true)
            val wasRouterEnabled = prefs.getBoolean(KEY_ROUTER_ENABLED, false)

            Log.d(TAG, "shouldAutoStart=$shouldAutoStart, wasRouterEnabled=$wasRouterEnabled")

            if (shouldAutoStart && wasRouterEnabled) {
                // A engine já possui retry; manter o receiver curto evita timeout do broadcast.
                Log.d(TAG, "Solicitando reinício do BtMicService após boot/update.")
                BtMicService.start(context)
            } else {
                Log.d(TAG, "Auto-start desabilitado ou router estava desligado - não reiniciando")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no BootReceiver", e)
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
