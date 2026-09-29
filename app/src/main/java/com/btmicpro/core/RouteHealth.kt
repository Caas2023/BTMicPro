package com.btmicpro.core

data class AudioEndpoint(val id: Int, val address: String, val name: String)

/** Não confunde entrada disponível com correspondência confirmada entre vários intercoms. */
fun matchInputEndpoint(output: AudioEndpoint, inputs: List<AudioEndpoint>, outputCount: Int): Int? {
    if (inputs.isEmpty()) return null
    if (output.address.isNotBlank()) {
        inputs.singleOrNull { it.address == output.address }?.let { return it.id }
        if (inputs.any { it.address.isNotBlank() }) return null
    }
    return if (outputCount == 1) inputs.singleOrNull()?.id else null
}

enum class RouteAction { KEEP, SELECT, WAIT_FOR_AUDIO, WAIT_FOR_DEVICE, YIELD_TO_CALL }

/**
 * Quantas avaliações não-KEEP consecutivas são toleradas antes de desmontar
 * uma rota saudável. O stack Bluetooth (ex: MediaTek) pode oscilar o SCO por
 * instantes — cada oscilação gerava teardown + reseleção completa, inflando
 * Quedas de Rota / Trocas CommDevice e piscando status/notificação.
 * Falhas reais persistem por várias avaliações e passam normalmente.
 */
const val TRANSIENT_FAILURE_TOLERANCE = 3

fun shouldTolerateTransientFailure(
    action: RouteAction,
    routeWasReady: Boolean,
    consecutiveFailures: Int
): Boolean {
    if (!routeWasReady) return false
    if (action == RouteAction.KEEP) return false
    // Outro app assumindo a comunicação exige yield imediato, sem tolerância.
    if (action == RouteAction.YIELD_TO_CALL) return false
    if (action == RouteAction.WAIT_FOR_DEVICE) return false
    return consecutiveFailures < TRANSIENT_FAILURE_TOLERANCE
}

data class RouteHealth(
    val hasDevice: Boolean,
    val selectionMatches: Boolean,
    val inputMatches: Boolean,
    val audioConnected: Boolean,
    val anotherAppCommunicating: Boolean
) {
    val ready: Boolean get() = hasDevice && selectionMatches && inputMatches && audioConnected
    fun action(): RouteAction = when {
        anotherAppCommunicating -> RouteAction.YIELD_TO_CALL
        ready -> RouteAction.KEEP
        !hasDevice -> RouteAction.WAIT_FOR_DEVICE
        !selectionMatches -> RouteAction.SELECT
        else -> RouteAction.WAIT_FOR_AUDIO
    }
}

// BUG-13: Aumentado de 4 tentativas (~7.5s total) para 6 tentativas (~30.5s total).
// Intercoms Bluetooth podem demorar 10-20s para reconectar SCO, especialmente após
// perda temporária de sinal ou troca de modo. Com 4 tentativas, a engine desistia
// cedo demais e ficava em RouteLost permanente, obrigando reinício manual do app.
class RecoveryBudget(private val delays: List<Long> = listOf(500, 1000, 2000, 4000, 8000, 15000)) {
    private var attempts = 0
    val exhausted: Boolean get() = attempts >= delays.size
    val attempt: Int get() = attempts
    fun nextDelay(): Long? = if (exhausted) null else delays[attempts++]
    fun reset() { attempts = 0 }
}
