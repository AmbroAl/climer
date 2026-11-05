package io.github.ambroal.climer.domain.time

/** Ora di calendario (per date e storico), distinta dal Clock monotono del timer. */
fun interface WallClock {
    fun nowMs(): Long
}
