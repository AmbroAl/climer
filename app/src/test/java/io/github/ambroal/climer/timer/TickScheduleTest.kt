package io.github.ambroal.climer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TickScheduleTest {
    private fun snap(remaining: Long, duration: Long = 7_000, running: Boolean = true) =
        TimerSnapshot(1, Phase(PhaseType.WORK, duration, 1, 1), remaining, remaining, running, isFinished = false)

    @Test
    fun `si sveglia esattamente al cambio del secondo`() {
        assertEquals(500L, nextTickDelayMs(snap(6_500)))
        assertEquals(1L, nextTickDelayMs(snap(6_001)))
    }

    @Test
    fun `sul confine del secondo aspetta il secondo intero successivo`() = assertEquals(1_000L, nextTickDelayMs(snap(6_000)))

    @Test
    fun `si sveglia anche alla meta di una fase lunga`() = assertEquals(400L, nextTickDelayMs(snap(12_900, duration = 25_000)))

    @Test
    fun `l attesa si conta dallo stato mostrato, non da una lettura successiva`() {
        // Mostrato a 6001 (display ancora "7"); l'orologio intanto è scattato a 6000: il confine è già qui.
        assertEquals(0L, remainingWaitMs(1, shown = snap(6_001), now = snap(6_000)))
    }

    @Test
    fun `il tempo speso dopo aver mostrato lo stato viene tolto dall attesa`() =
        assertEquals(985L, remainingWaitMs(1_000, shown = snap(6_000), now = snap(5_985)))

    @Test
    fun `un cambiamento gia passato non da attese negative`() =
        assertEquals(0L, remainingWaitMs(1_000, shown = snap(6_000), now = snap(4_500)))

    @Test
    fun `in pausa o a fine timer non serve svegliarsi`() {
        assertNull(nextTickDelayMs(snap(6_500, running = false)))
        assertNull(nextTickDelayMs(TimerSnapshot(5, null, 0, 0, isRunning = false, isFinished = true)))
    }
}
