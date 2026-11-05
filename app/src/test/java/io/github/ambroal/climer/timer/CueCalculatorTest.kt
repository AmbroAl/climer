package io.github.ambroal.climer.timer

import io.github.ambroal.climer.domain.interval.IntervalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueCalculatorTest {
    private val phases = buildPhases(IntervalConfig(10, 7, 3, 2, 1, 0, 0))
    private val dita = buildPhases(IntervalConfig(10, 7, 3, 30, 6, 10, 0))
    private val long = buildPhases(IntervalConfig(prepareSec = 0, workSec = 40))

    private fun at(p: List<Phase>, ms: Long): TimerSnapshot {
        val clock = FakeClock()
        val e = TimerEngine(p, clock)
        e.start()
        clock.now = ms
        return e.snapshot()
    }

    private fun snapAt(ms: Long) = at(phases, ms)
    private fun ditaAt(ms: Long) = at(dita, ms)
    private fun longAt(ms: Long) = at(long, ms)

    @Test
    fun `primo snapshot in corso annuncia la fase`() = assertEquals(listOf(Cue.PhaseStarted(0, phases[0])), cuesBetween(null, snapAt(0)))

    @Test
    fun `cambio fase`() = assertEquals(listOf(Cue.PhaseStarted(1, phases[1])), cuesBetween(snapAt(9_950), snapAt(10_050)))

    @Test
    fun `salto di molte fasi non spara una raffica`() = // fasi DITA VOLUME: a 40 s inizia dita[7] (WORK, round 4)
        assertEquals(listOf(Cue.PhaseStarted(7, dita[7])), cuesBetween(ditaAt(1_000), ditaAt(40_000)))

    @Test
    fun `fase da 7 secondi niente dieci secondi ne meta`() =
        assertTrue(cuesBetween(snapAt(10_000), snapAt(16_900)).none { it == Cue.TenSecondsLeft || it == Cue.Halfway })

    @Test
    fun `3-2-1 in una fase da 10`() = assertEquals(
        listOf(3, 2, 1),
        (6_900L..9_900L step 100).zipWithNext()
            .flatMap { (a, b) -> cuesBetween(snapAt(a), snapAt(b)) }
            .filterIsInstance<Cue.Countdown>()
            .map { it.seconds }
    )

    @Test
    fun `dieci secondi rimanenti in una fase da 40`() = assertTrue(Cue.TenSecondsLeft in cuesBetween(longAt(29_900), longAt(30_100)))

    @Test
    fun `nessun dieci secondi nella fase da 10`() = assertTrue(Cue.TenSecondsLeft !in cuesBetween(snapAt(0), snapAt(500)))

    @Test
    fun `meta in una fase da 40`() = assertTrue(Cue.Halfway in cuesBetween(longAt(19_900), longAt(20_100)))

    @Test
    fun `tick a ogni secondo`() = assertEquals(2, cuesBetween(snapAt(1_500), snapAt(3_500)).count { it == Cue.Tick })

    @Test
    fun `fine timer`() = assertEquals(listOf(Cue.Finished), cuesBetween(snapAt(26_900), snapAt(27_100)))

    @Test
    fun `in pausa nessun segnale`() {
        val clock = FakeClock()
        val e = TimerEngine(phases, clock)
        assertEquals(emptyList<Cue>(), cuesBetween(null, e.snapshot()))
    }
}
