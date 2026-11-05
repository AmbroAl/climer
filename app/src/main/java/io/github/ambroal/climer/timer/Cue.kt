package io.github.ambroal.climer.timer

sealed interface Cue {
    data class PhaseStarted(val index: Int, val phase: Phase) : Cue

    data class Countdown(val seconds: Int) : Cue

    data object TenSecondsLeft : Cue

    data object Halfway : Cue

    data object Tick : Cue

    data object Finished : Cue

    /** Cronometro: è scaduto l'avviso numero [count]. */
    data class Interval(val count: Int) : Cue
}
