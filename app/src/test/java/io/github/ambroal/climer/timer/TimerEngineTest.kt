package io.github.ambroal.climer.timer

import io.github.ambroal.climer.domain.interval.IntervalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {
    // PREPARE 10s, WORK 7s, REST 3s, WORK 7s
    private val phases = buildPhases(IntervalConfig(10, 7, 3, 2, 1, 0, 0))
    private val clock = FakeClock(1_000_000)
    private val e = TimerEngine(phases, clock)

    @Test
    fun `fermo finche non parte`() {
        clock.now += 50_000
        assertEquals(0, e.snapshot().phaseIndex)
        assertFalse(e.snapshot().isRunning)
    }

    @Test
    fun `tempo da timestamp`() {
        e.start()
        clock.now += 12_500
        val s = e.snapshot()
        assertEquals(1, s.phaseIndex)
        assertEquals(4_500, s.phaseRemainingMs)
        assertEquals(14_500, s.totalRemainingMs)
    }

    @Test
    fun `pausa congela il tempo`() {
        e.start()
        clock.now += 5_000
        e.pause()
        clock.now += 60_000
        assertEquals(5_000, e.snapshot().phaseRemainingMs)
        e.start()
        clock.now += 1_000
        assertEquals(4_000, e.snapshot().phaseRemainingMs)
    }

    @Test
    fun `salto grande di clock dopo schermo spento`() {
        e.start()
        clock.now += 3_600_000
        val s = e.snapshot()
        assertTrue(s.isFinished)
        assertFalse(s.isRunning)
        assertNull(s.phase)
        assertEquals(phases.size, s.phaseIndex)
        assertEquals(0, s.totalRemainingMs)
    }

    @Test
    fun `skipTo va all inizio della fase`() {
        e.start()
        e.skipTo(2)
        val s = e.snapshot()
        assertEquals(2, s.phaseIndex)
        assertEquals(3_000, s.phaseRemainingMs)
        assertTrue(s.isRunning)
    }

    @Test
    fun `skipTo in pausa resta in pausa`() {
        e.skipTo(3)
        clock.now += 10_000
        assertEquals(7_000, e.snapshot().phaseRemainingMs)
        assertFalse(e.snapshot().isRunning)
    }

    @Test
    fun `start due volte non azzera`() {
        e.start()
        clock.now += 2_000
        e.start()
        clock.now += 1_000
        assertEquals(7_000, e.snapshot().phaseRemainingMs)
    }

    @Test
    fun `inizio nel futuro dopo un riavvio resta in pausa`() {
        val restored = TimerEngine(phases, clock, TimerRunState(elapsedBeforeMs = 5_000, runningSinceMs = clock.now + 600_000))
        val s = restored.snapshot()
        assertFalse(s.isRunning)
        assertEquals(5_000, s.phaseRemainingMs)
    }

    @Test
    fun `ripristino da runState dopo process death`() {
        e.start()
        clock.now += 12_500
        val restored = TimerEngine(phases, clock, e.runState)
        assertEquals(e.snapshot(), restored.snapshot())
    }
}
