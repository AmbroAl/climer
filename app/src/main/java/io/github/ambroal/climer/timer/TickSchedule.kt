package io.github.ambroal.climer.timer

/**
 * Millisecondi fino al prossimo momento in cui cambia qualcosa: il secondo mostrato (arrotondato per eccesso)
 * oppure la metà di una fase lunga. Svegliarsi lì, invece che a intervalli fissi, rende regolari display e segnali.
 */
fun nextTickDelayMs(snapshot: TimerSnapshot): Long? {
    if (!snapshot.isRunning || snapshot.isFinished) return null
    val phase = snapshot.phase ?: return null
    val remaining = snapshot.phaseRemainingMs
    var target = (remaining - 1) / 1_000 * 1_000
    val half = phase.durationMs / 2
    if (phase.durationMs >= HALFWAY_MIN_PHASE_MS && half < remaining) target = maxOf(target, half)
    return (remaining - target).coerceIn(1, 1_000)
}
