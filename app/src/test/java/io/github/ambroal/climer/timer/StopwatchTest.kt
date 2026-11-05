package io.github.ambroal.climer.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class StopwatchTest {
    @Test
    fun `avviso esattamente allo scadere di ogni minuto`() {
        assertEquals(emptyList<Cue>(), stopwatchCues(59_900, 59_999, 60_000))
        assertEquals(listOf(Cue.Interval(1)), stopwatchCues(59_900, 60_000, 60_000))
        assertEquals(listOf(Cue.Interval(2)), stopwatchCues(119_950, 120_050, 60_000))
    }

    @Test
    fun `senza intervallo nessun avviso`() = assertEquals(emptyList<Cue>(), stopwatchCues(59_900, 60_000, 0))

    @Test
    fun `dopo un lungo salto un solo avviso con il conteggio attuale`() =
        assertEquals(listOf(Cue.Interval(5)), stopwatchCues(10_000, 300_500, 60_000))

    @Test
    fun `alla partenza nessun avviso`() = assertEquals(emptyList<Cue>(), stopwatchCues(null, 0, 60_000))

    @Test
    fun `il display si aggiorna ogni decimo esatto`() {
        assertEquals(100L, nextStopwatchDelayMs(0))
        assertEquals(30L, nextStopwatchDelayMs(1_270))
        assertEquals(100L, nextStopwatchDelayMs(60_000))
    }
}
