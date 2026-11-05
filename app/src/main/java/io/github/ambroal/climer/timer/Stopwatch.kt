package io.github.ambroal.climer.timer

/** Il cronometro usa il motore del timer con un'unica fase molto lunga: oltre le 100 ore si ferma. */
const val STOPWATCH_MAX_MS = 100L * 3_600_000

fun stopwatchPhases(): List<Phase> = listOf(Phase(PhaseType.WORK, STOPWATCH_MAX_MS, 0, 0))

/**
 * Avviso a ogni multiplo di [everyMs] attraversato tra [prevElapsed] ed [elapsed]; dopo un salto lungo
 * (app chiusa, schermo spento) un solo avviso con il conteggio attuale, non una raffica.
 */
fun stopwatchCues(prevElapsed: Long?, elapsed: Long, everyMs: Long): List<Cue> {
    if (prevElapsed == null || everyMs <= 0) return emptyList()
    val before = prevElapsed / everyMs
    val now = elapsed / everyMs
    return if (now > before) listOf(Cue.Interval(now.toInt())) else emptyList()
}

/** Il display mostra i decimi: ci si sveglia a ogni decimo esatto (gli avvisi, a secondi interi, cadono lì). */
fun nextStopwatchDelayMs(elapsed: Long): Long = 100 - elapsed % 100
