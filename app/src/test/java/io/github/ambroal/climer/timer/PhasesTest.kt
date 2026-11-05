package io.github.ambroal.climer.timer

import io.github.ambroal.climer.domain.interval.IntervalConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PhasesTest {
    @Test
    fun `dita volume dura 30 minuti e 42`() = assertEquals(1_842_000L, IntervalConfig(10, 7, 3, 30, 6, 10, 0).totalDurationMs())

    @Test
    fun `my rounds dura 5 minuti e 2`() = assertEquals(302_000L, IntervalConfig(5, 7, 3, 30, 1, 0, 0).totalDurationMs())

    @Test
    fun `sequenza dita volume`() {
        val p = buildPhases(IntervalConfig(10, 7, 3, 30, 6, 10, 0))
        assertEquals(PhaseType.PREPARE, p.first().type)
        assertEquals(Phase(PhaseType.WORK, 7_000, 30, 1), p[59])
        assertEquals(Phase(PhaseType.CYCLE_REST, 10_000, 0, 1), p[60])
        assertEquals(Phase(PhaseType.WORK, 7_000, 30, 6), p.last())
        assertEquals(1 + 6 * 59 + 5, p.size)
    }

    @Test
    fun `fasi a durata zero vengono omesse`() {
        val p = buildPhases(IntervalConfig(0, 7, 0, 3, 1, 0, 0))
        assertEquals(listOf(PhaseType.WORK, PhaseType.WORK, PhaseType.WORK), p.map { it.type })
    }

    @Test
    fun `rinfrescati in coda`() = assertEquals(PhaseType.COOLDOWN, buildPhases(IntervalConfig(cooldownSec = 60)).last().type)

    @Test
    fun `la formula coincide con la somma delle fasi`() {
        listOf(
            IntervalConfig(0, 1, 0, 1, 1, 0, 0),
            IntervalConfig(10, 7, 3, 30, 6, 10, 60),
            IntervalConfig(5, 20, 0, 3, 4, 0, 0),
            IntervalConfig(0, 7, 3, 1, 5, 90, 0)
        ).forEach { assertEquals(it.toString(), buildPhases(it).sumOf { p -> p.durationMs }, it.totalDurationMs()) }
    }

    @Test(timeout = 1_000)
    fun `durata di una configurazione enorme senza costruire le fasi`() =
        assertEquals(3_030L * 6_661 * 10_000 - 6_661 * 3_000L, IntervalConfig(0, 7, 3, 3_030, 6_661, 0, 0).totalDurationMs())

    @Test
    fun `il break sostituisce l ultimo riposo`() {
        val config = IntervalConfig(10, 7, 3, 1, 1, 0, 10)
        assertEquals(listOf(PhaseType.PREPARE, PhaseType.WORK, PhaseType.COOLDOWN), buildPhases(config).map { it.type })
        assertEquals(27_000L, config.totalDurationMs())
    }

    @Test
    fun `con piu cicli il break segue subito l ultimo lavoro`() {
        val config = IntervalConfig(0, 7, 3, 2, 2, 10, 60)
        val types = buildPhases(config).map { it.type }
        assertEquals(listOf(PhaseType.WORK, PhaseType.COOLDOWN), types.takeLast(2))
        assertEquals(buildPhases(config).sumOf { it.durationMs }, config.totalDurationMs())
    }

    @Test
    fun `un round un ciclo`() = assertEquals(listOf(PhaseType.PREPARE, PhaseType.WORK), buildPhases(IntervalConfig()).map { it.type })
}
