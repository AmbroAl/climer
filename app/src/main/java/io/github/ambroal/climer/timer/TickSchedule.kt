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

/**
 * Quanto resta di un'attesa di [waitMs] decisa su [shown], lo stato già pubblicato, ora che il timer è a [now].
 * L'attesa va decisa su ciò che è stato mostrato, non su una lettura più recente: i delay seguono un orologio
 * diverso da quello del timer e possono svegliarsi 1 ms prima del confine. Mostrato 2001 ("3") e riletto 2000,
 * il prossimo cambiamento risulterebbe a 1000: il "2" non verrebbe mai mostrato né suonato.
 */
fun remainingWaitMs(waitMs: Long, shown: TimerSnapshot, now: TimerSnapshot): Long =
    (waitMs - (shown.totalRemainingMs - now.totalRemainingMs)).coerceAtLeast(0)
