package io.github.ambroal.climer.ui.running

import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.PhaseType
import io.github.ambroal.climer.timer.QueuedBlock
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningUiStateTest {
    private val clock = FakeClock(0)
    private val ctx = BlockRunContext("Round 7:3", "No-hang", "Travetto 20 mm", SideMode.ALTERNATE_RIGHT_FIRST, 17.5, 16.5, null)

    private fun TestScope.uiAt(ms: Long, context: BlockRunContext? = ctx, queue: List<QueuedBlock> = emptyList()): RunningUiState {
        val controller = TimerController(
            clock = clock,
            store = object : ActiveRunStore {
                override suspend fun save(run: ActiveRun?) = Unit

                override suspend fun load(): ActiveRun? = null
            },
            launcher = { },
            completion = { },
            cues = { _, _ -> },
            scope = backgroundScope
        )
        controller.start(
            ActiveRun(
                "Round 7:3",
                IntervalConfig(10, 7, 3, 30, 6, 10, 0),
                TimerRunState(),
                context = context,
                queue = queue,
                blockNumber = if (queue.isEmpty()) 0 else 1,
                blockCount = queue.size + 1
            )
        )
        clock.now = ms
        controller.tick()
        return toRunningUiState(controller.state.value, AppSettings())
    }

    @Test
    fun `nel riposo tra i cicli la fascia dice di cambiare mano`() = runTest {
        val s = uiAt(10_000 + 297_000 + 1_000)
        assertEquals(PhaseType.CYCLE_REST, s.phaseType)
        assertEquals(Banner(change = true, side = Side.LEFT, kg = 16.5), s.banner)
    }

    @Test
    fun `nel lavoro del ciclo 1 destra 17,5`() = runTest {
        assertEquals(Banner(change = false, side = Side.RIGHT, kg = 17.5), uiAt(12_000).banner)
    }

    @Test
    fun `round e cicli rimasti a meta del ciclo 2`() = runTest {
        val s = uiAt(317_000 + 51_000) // ciclo 2, lavoro del round 6
        assertEquals(PhaseType.WORK, s.phaseType)
        assertEquals(25, s.roundsLeft)
        assertEquals(5, s.cyclesLeft)
    }

    @Test
    fun `in preparazione tutti i round e cicli`() = runTest {
        val s = uiAt(1_000)
        assertEquals(30, s.roundsLeft)
        assertEquals(6, s.cyclesLeft)
        assertEquals("00:09", s.phaseRemaining)
        assertEquals("30:41", s.totalRemaining)
        assertEquals(PhaseType.WORK, s.nextType)
    }

    @Test
    fun `entrambe le mani con pesi diversi li mostra tutti e due`() = runTest {
        val both = ctx.copy(sideMode = SideMode.BOTH)
        assertEquals(Banner(change = false, side = null, kg = null, rightKg = 17.5, leftKg = 16.5), uiAt(12_000, both).banner)
    }

    @Test
    fun `in seduta l ultima fase annuncia il prossimo blocco`() = runTest {
        val s = uiAt(1_840_000, queue = listOf(QueuedBlock(2, "Ripetute"), QueuedBlock(3, "Forza")))
        assertNull(s.nextType)
        assertEquals("Ripetute", s.nextBlockName)
        assertEquals("1/3", s.blockLabel)
    }

    @Test
    fun `blocco singolo senza etichetta`() = runTest { assertNull(uiAt(12_000).blockLabel) }

    @Test
    fun `timer semplice senza fascia`() = runTest {
        assertNull(uiAt(12_000, context = null).banner)
    }

    @Test
    fun `a fine timer zero rimasti`() = runTest {
        val s = uiAt(2_000_000)
        assertTrue(s.isFinished)
        assertEquals(0, s.roundsLeft)
        assertEquals(0, s.cyclesLeft)
    }
}
