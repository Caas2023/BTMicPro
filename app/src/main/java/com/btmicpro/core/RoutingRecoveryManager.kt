package com.btmicpro.core

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * RoutingRecoveryManager — Gerencia a autorrecuperação resiliente da rota de comunicação Bluetooth (Itens 23, 24, 59).
 *
 * Aplica retries com backoff exponencial serializado e mede a duração exata do processo de recuperação.
 */
class RoutingRecoveryManager(
    private val maxAttempts: Int = 4,
    private val backoffDelaysMs: LongArray = longArrayOf(500L, 1000L, 2000L, 4000L),
    private val onAttempt: (attempt: Int) -> Unit,
    private val onRecoverySuccess: (durationMs: Long) -> Unit,
    private val onRecoveryFailed: (reason: String) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentAttempt = 0
    private var isRecovering = false
    private var pendingRunnable: Runnable? = null

    // Métricas de recuperação (Item 59 do Prompt Master)
    var routeLostAt: Long = 0L
        private set
    var routeRecoveredAt: Long = 0L
        private set
    var lastRecoveryDurationMs: Long = 0L
        private set

    /**
     * Inicia a sequência de recuperação serializada.
     */
    fun startRecovery(reason: String) {
        if (isRecovering) {
            Log.d(TAG, "Recuperação já em andamento, ignorando nova solicitação ($reason)")
            return
        }

        isRecovering = true
        currentAttempt = 0
        routeLostAt = System.currentTimeMillis()
        Log.w(TAG, "Iniciando sequência de recuperação de rota V5. Motivo: $reason")
        scheduleNextAttempt()
    }

    private fun scheduleNextAttempt() {
        currentAttempt++
        if (currentAttempt > maxAttempts) {
            isRecovering = false
            Log.e(TAG, "Limite máximo de tentativas de recuperação atingido ($maxAttempts).")
            onRecoveryFailed("Falha ao restabelecer rota após $maxAttempts tentativas com backoff.")
            return
        }

        val index = (currentAttempt - 1).coerceIn(0, backoffDelaysMs.size - 1)
        val delayMs = backoffDelaysMs[index]

        Log.i(TAG, "Agendando tentativa $currentAttempt de $maxAttempts em ${delayMs}ms...")

        pendingRunnable = Runnable {
            if (!isRecovering) return@Runnable
            onAttempt(currentAttempt)
        }
        handler.postDelayed(pendingRunnable!!, delayMs)
    }

    /**
     * Dispara a próxima tentativa de recuperação do backoff se a anterior não surtiu efeito.
     */
    fun retry() {
        if (!isRecovering) return
        scheduleNextAttempt()
    }

    /**
     * Notifica conclusão bem-sucedida da recuperação e calcula o tempo total decorrido.
     */
    fun markSuccess() {
        if (!isRecovering) return
        routeRecoveredAt = System.currentTimeMillis()
        lastRecoveryDurationMs = (routeRecoveredAt - routeLostAt).coerceAtLeast(0L)

        Log.i(TAG, "Recuperação de rota concluída com sucesso na tentativa $currentAttempt em ${lastRecoveryDurationMs}ms!")
        cancel()
        onRecoverySuccess(lastRecoveryDurationMs)
    }

    /**
     * Cancela qualquer rotina pendente e reseta o estado.
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
