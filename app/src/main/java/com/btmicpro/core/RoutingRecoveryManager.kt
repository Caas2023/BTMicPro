package com.btmicpro.core

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * RoutingRecoveryManager — Gerencia a autorrecuperação resiliente da rota de comunicação Bluetooth.
 * Aplica retries com backoff exponencial e garante que apenas uma rotina de recuperação execute por vez (serialização).
 */
class RoutingRecoveryManager(
    private val maxAttempts: Int = 4,
    private val onAttempt: (attempt: Int) -> Unit,
    private val onRecoverySuccess: () -> Unit,
    private val onRecoveryFailed: (reason: String) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentAttempt = 0
    private var isRecovering = false
    private var pendingRunnable: Runnable? = null

    /**
     * Inicia a sequência de recuperação se não houver outra em andamento.
     */
    fun startRecovery(reason: String) {
        if (isRecovering) {
            Log.d(TAG, "Recuperação já em andamento, ignorando nova solicitação ($reason)")
            return
        }

        isRecovering = true
        currentAttempt = 0
        Log.w(TAG, "Iniciando sequência de recuperação de rota. Motivo: $reason")
        scheduleNextAttempt()
    }

    private fun scheduleNextAttempt() {
        currentAttempt++
        if (currentAttempt > maxAttempts) {
            isRecovering = false
            Log.e(TAG, "Limite máximo de tentativas de recuperação atingido ($maxAttempts).")
            onRecoveryFailed("Falha ao restabelecer rota após $maxAttempts tentativas")
            return
        }

        // Backoff exponencial: 600ms, 1200ms, 2400ms, 3500ms
        val delayMs = when (currentAttempt) {
            1 -> 600L
            2 -> 1200L
            3 -> 2400L
            else -> 3500L
        }

        Log.i(TAG, "Agendando tentativa $currentAttempt de $maxAttempts em ${delayMs}ms...")
        onAttempt(currentAttempt)

        pendingRunnable = Runnable {
            if (!isRecovering) return@Runnable
            executeAttempt()
        }
        handler.postDelayed(pendingRunnable!!, delayMs)
    }

    private fun executeAttempt() {
        // Notifica o orquestrador para revalidar a rota
        // Se a rota for validada com sucesso, o orquestrador chamará markSuccess()
        // Caso contrário, se o orquestrador chamar markFailure(), o backoff continua
    }

    /**
     * Notifica o gerenciador que a tentativa atual falhou, prosseguindo com a próxima etapa do backoff.
     */
    fun retry() {
        if (!isRecovering) return
        scheduleNextAttempt()
    }

    /**
     * Marca a recuperação como bem-sucedida e encerra a rotina.
     */
    fun markSuccess() {
        if (!isRecovering) return
        Log.i(TAG, "Recuperação de rota concluída com sucesso na tentativa $currentAttempt!")
        cancel()
        onRecoverySuccess()
    }

    /**
     * Cancela qualquer recuperação pendente e reseta os contadores.
     */
    fun cancel() {
        isRecovering = false
        currentAttempt = 0
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }

    fun isRecoveryActive(): Boolean = isRecovering

    companion object {
        private const val TAG = "BTMIC_RECOVERY"
    }
}
