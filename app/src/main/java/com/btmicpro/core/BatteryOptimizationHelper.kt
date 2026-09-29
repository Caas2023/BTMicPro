package com.btmicpro.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper para garantir que o app não seja morto pelo sistema com tela desligada.
 * Em Xiaomi/MIUI, Samsung, OnePlus, o Android mata ForegroundService em 5-15 min
 * se não estiver na whitelist de otimização de bateria.
 *
 * Sem isso, o "sempre em chamada" morre na moto na estrada.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryHelper"

    /**
     * Verifica se já está ignorando otimização de bateria.
     * Se sim, o sistema NÃO mata o serviço.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "isIgnoringBatteryOptimizations: $isIgnoring")
            isIgnoring
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar battery optimization", e)
            false
        }
    }

    /**
     * Abre a tela do sistema para o usuário liberar o app.
     * Deve ser chamado após o usuário conceder permissões básicas.
     * Não é automática - Android exige gesto do usuário por segurança.
     */
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        return try {
            if (isIgnoringBatteryOptimizations(context)) {
                Log.d(TAG, "Já está na whitelist, não precisa pedir")
                return true
            }

            // A lista do sistema permite ajuste manual sem solicitar uma permissão adicional.
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Configurações de otimização de bateria abertas")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar ignore battery optimization", e)
            false
        }
    }
}
