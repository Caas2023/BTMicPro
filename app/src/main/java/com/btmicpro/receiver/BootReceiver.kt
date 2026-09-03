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
 * V2: Trata BOOT_COMPLETED + LOCKED_BOOT_COMPLETED + MY_PACKAGE_REPLACED
 * para garantir "sempre em chamada" mesmo após reiniciar a moto com celular no bolso.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        Log.d(TAG, "BootReceiver acionado: $action")

        // Aceita múltiplos gatilhos de inicialização
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
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
                // Pequeno delay para o Bluetooth stack inicializar após boot
                // Sem isso, o setCommunicationDevice falha porque BT ainda não está pronto
                val delayMs = if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) 15000L else 5000L
                Log.d(TAG, "Agendando reinício do BtMicService em ${delayMs}ms...")

                // Usa goAsync para não bloquear o BroadcastReceiver + Handler para delay
                val pendingResult = goAsync()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "Reiniciando BtMicService automaticamente após o boot.")
                        BtMicService.start(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao reiniciar BtMicService após boot", e)
                    } finally {
                        pendingResult.finish()
                    }
                }, delayMs)
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
