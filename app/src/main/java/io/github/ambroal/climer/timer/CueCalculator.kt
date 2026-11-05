package io.github.ambroal.climer.timer

private const val TEN_SECONDS_MS = 10_000L
internal const val HALFWAY_MIN_PHASE_MS = 20_000L

/** Segnali da emettere passando da [prev] a [curr]; una soglia scatta quando prev > soglia >= curr. */
fun cuesBetween(prev: TimerSnapshot?, curr: TimerSnapshot): List<Cue> {
    if (curr.isFinished) return if (prev != null && !prev.isFinished) listOf(Cue.Finished) else emptyList()
    val phase = curr.phase ?: return emptyList()
    if (prev == null) return if (curr.isRunning) listOf(Cue.PhaseStarted(curr.phaseIndex, phase)) else emptyList()
    if (prev.phaseIndex != curr.phaseIndex) return listOf(Cue.PhaseStarted(curr.phaseIndex, phase))

    val from = prev.phaseRemainingMs
    val to = curr.phaseRemainingMs
    fun crossed(threshold: Long) = from > threshold && threshold >= to

    val cues = mutableListOf<Cue>()
    if (phase.durationMs >= HALFWAY_MIN_PHASE_MS && crossed(phase.durationMs / 2)) cues += Cue.Halfway
    if (phase.durationMs > TEN_SECONDS_MS && crossed(TEN_SECONDS_MS)) cues += Cue.TenSecondsLeft
    for (s in 3 downTo 1) if (crossed(s * 1_000L)) cues += Cue.Countdown(s)
    val firstSecond = (to + 999) / 1_000
    val lastSecond = (from - 1) / 1_000
    for (s in lastSecond downTo maxOf(firstSecond, 1)) cues += Cue.Tick
    return cues
}
