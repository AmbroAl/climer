package io.github.ambroal.climer.timer

import kotlinx.serialization.Serializable

@Serializable
data class TimerRunState(val elapsedBeforeMs: Long = 0, val runningSinceMs: Long? = null)

data class TimerSnapshot(
    val phaseIndex: Int,
    val phase: Phase?,
    val phaseRemainingMs: Long,
    val totalRemainingMs: Long,
    val isRunning: Boolean,
    val isFinished: Boolean
)

/** Il tempo trascorso si ricava sempre dai timestamp del [Clock], mai sommando tick. */
class TimerEngine(private val phases: List<Phase>, private val clock: Clock, initial: TimerRunState = TimerRunState()) {
    private val phaseStarts: LongArray = LongArray(phases.size).also { starts ->
        var acc = 0L
        phases.forEachIndexed { i, p ->
            starts[i] = acc
            acc += p.durationMs
        }
    }
    private val totalMs: Long = phases.sumOf { it.durationMs }

    var runState: TimerRunState = initial
        private set

    init {
        // Un inizio "nel futuro" viene da un timestamp di un avvio precedente del telefono: si riparte in pausa.
        val since = initial.runningSinceMs
        if (since != null && since > clock.elapsedRealtimeMs()) runState = TimerRunState(initial.elapsedBeforeMs)
    }

    fun start() {
        if (runState.runningSinceMs != null || elapsedMs() >= totalMs) return
        runState = runState.copy(runningSinceMs = clock.elapsedRealtimeMs())
    }

    fun pause() {
        if (runState.runningSinceMs == null) return
        runState = TimerRunState(elapsedBeforeMs = elapsedMs().coerceAtMost(totalMs), runningSinceMs = null)
    }

    fun skipTo(phaseIndex: Int) {
        val target = if (phaseIndex >= phases.size) totalMs else phaseStarts[phaseIndex.coerceAtLeast(0)]
        val running = runState.runningSinceMs != null && target < totalMs
        runState = TimerRunState(elapsedBeforeMs = target, runningSinceMs = if (running) clock.elapsedRealtimeMs() else null)
    }

    fun snapshot(): TimerSnapshot {
        val elapsed = elapsedMs().coerceIn(0, totalMs)
        if (elapsed >= totalMs) {
            return TimerSnapshot(phases.size, null, 0, 0, isRunning = false, isFinished = true)
        }
        val index = phaseStarts.indexOfLast { it <= elapsed }
        val phase = phases[index]
        return TimerSnapshot(
            phaseIndex = index,
            phase = phase,
            phaseRemainingMs = phaseStarts[index] + phase.durationMs - elapsed,
            totalRemainingMs = totalMs - elapsed,
            isRunning = runState.runningSinceMs != null,
            isFinished = false
        )
    }

    private fun elapsedMs(): Long {
        val since = runState.runningSinceMs ?: return runState.elapsedBeforeMs
        return runState.elapsedBeforeMs + (clock.elapsedRealtimeMs() - since)
    }
}
