package io.github.ambroal.climer.timer

import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private class MemoryStore : ActiveRunStore {
        var run: ActiveRun? = null

        override suspend fun save(run: ActiveRun?) {
            this.run = run
        }

        override suspend fun load(): ActiveRun? = run
    }

    private val clock = FakeClock(5_000_000)
    private val store = MemoryStore()
    private val completed = mutableListOf<Long>()
    private val ctx = BlockRunContext("Round", "No-hang", "Travetto", SideMode.ALTERNATE_RIGHT_FIRST, 17.5, 16.5, null)
    private val run = ActiveRun(
        title = "Round 7:3",
        config = IntervalConfig(10, 7, 3, 30, 6, 10, 0),
        runState = TimerRunState(),
        workoutId = 1,
        blockLogIds = listOf(11, 12),
        context = ctx
    )

    private fun TestScope.controller() = TimerController(
        clock = clock,
        store = store,
        launcher = ServiceLauncher { },
        completion = BlockCompletionSink { completed += it },
        cues = CueSink { _, _ -> },
        scope = backgroundScope
    )

    @Test
    fun `ripristino dopo process death riprende dal punto giusto`() = runTest {
        val first = controller()
        first.start(run)
        runCurrent()
        clock.now += 365_000 // ciclo 2: sinistra
        first.tick()
        val before = first.state.value
        val second = controller()
        second.restoreIfAny()
        second.tick()
        val after = second.state.value
        assertEquals(before?.snapshot?.phaseIndex, after?.snapshot?.phaseIndex)
        assertEquals(Side.LEFT, after?.current?.side)
        assertEquals(16.5, after?.current?.kg)
    }

    @Test
    fun `a fine timer marca i blocchi completati`() = runTest {
        val c = controller()
        c.start(run)
        clock.now += 1_842_000
        c.tick()
        runCurrent()
        assertTrue(c.state.value?.snapshot?.isFinished == true)
        assertEquals(listOf(11L, 12L), completed)
        assertNull(store.run)
    }

    @Test
    fun `nel riposo tra i cicli il prossimo e la mano sinistra`() = runTest {
        val c = controller()
        c.start(run)
        clock.now += 10_000 + 297_000 + 1_000
        c.tick()
        val ui = c.state.value
        assertEquals(PhaseType.CYCLE_REST, ui?.snapshot?.phase?.type)
        assertEquals(Side.RIGHT, ui?.current?.side)
        assertEquals(Side.LEFT, ui?.next?.side)
    }

    @Test
    fun `dopo un riavvio del telefono il timer riprende in pausa`() = runTest {
        val first = controller()
        first.start(run)
        clock.now += 20_000
        first.togglePause()
        first.togglePause()
        runCurrent()
        clock.boot += 1
        clock.now = 9_000_000 // nuovo avvio con uptime maggiore: solo il conteggio dei boot lo rivela
        val second = controller()
        second.restoreIfAny()
        val ui = second.state.value
        assertEquals(false, ui?.snapshot?.isRunning)
        assertEquals(20_000L, 1_842_000L - (ui?.snapshot?.totalRemainingMs ?: 0))
    }

    @Test
    fun `servizio non avviabile non fa crashare il timer`() = runTest {
        val c = TimerController(clock, store, { error("ForegroundServiceStartNotAllowed") }, { }, { _, _ -> }, backgroundScope)
        c.start(run)
        assertEquals(true, c.state.value?.snapshot?.isRunning)
    }

    @Test
    fun `apertura senza avvio resta in pausa`() = runTest {
        val c = controller()
        c.start(run, autoStart = false)
        assertEquals(false, c.state.value?.snapshot?.isRunning)
        assertEquals(0, c.state.value?.snapshot?.phaseIndex)
    }

    @Test
    fun `il segnale di inizio lavoro arriva esattamente allo scadere`() = runTest {
        val heard = mutableListOf<Pair<Long, Cue>>()
        val c = TimerController(
            clock = Clock { testScheduler.currentTime },
            store = store,
            launcher = { },
            completion = { },
            cues = { cues, _ -> cues.forEach { heard += testScheduler.currentTime to it } },
            scope = backgroundScope
        )
        c.start(run)
        advanceTimeBy(1_250)
        c.togglePause()
        c.togglePause()
        advanceTimeBy(9_000)
        val work = heard.first { (it.second as? Cue.PhaseStarted)?.phase?.type == PhaseType.WORK }
        assertEquals(10_000L, work.first)
        assertEquals(listOf(7_000L, 8_000L, 9_000L), heard.filter { it.second is Cue.Countdown }.map { it.first })
    }

    @Test
    fun `un risveglio in anticipo di un millisecondo non fa saltare il secondo`() = runTest {
        // I delay seguono un orologio diverso da quello del timer: al risveglio il timer può essere ancora
        // nel millisecondo prima del confine, e scattare subito dopo.
        var lastWake = -1L
        val lagging = Clock {
            val t = testScheduler.currentTime
            val early = t != lastWake
            lastWake = t
            1_000_000 + if (early) t - 1 else t
        }
        val remainingAtCountdown = mutableListOf<Long>()
        var workStartRemaining: Long? = null
        val c = TimerController(
            clock = lagging,
            store = store,
            launcher = { },
            completion = { },
            cues = { cues, ui ->
                if (cues.any { it is Cue.Countdown }) remainingAtCountdown += ui.snapshot.phaseRemainingMs
                if (cues.any { (it as? Cue.PhaseStarted)?.phase?.type == PhaseType.WORK }) {
                    workStartRemaining = workStartRemaining ?: ui.snapshot.phaseRemainingMs
                }
            },
            scope = backgroundScope
        )
        c.start(run)
        advanceTimeBy(10_500)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), remainingAtCountdown)
        assertEquals(7_000L, workStartRemaining)
    }

    @Test
    fun `riavvia riporta il blocco all inizio e lo fa ripartire`() = runTest {
        val c = controller()
        c.start(run)
        clock.now += 400_000
        c.togglePause()
        c.restart()
        runCurrent()
        val s = c.state.value?.snapshot
        assertEquals(0, s?.phaseIndex)
        assertEquals(10_000L, s?.phaseRemainingMs)
        assertEquals(true, s?.isRunning)
        assertEquals(listOf(11L, 12L), store.run?.blockLogIds)
    }

    @Test
    fun `riavvia funziona anche a blocco finito`() = runTest {
        val c = controller()
        c.start(run)
        clock.now += 2_000_000
        c.tick()
        runCurrent()
        c.restart()
        assertEquals(true, c.state.value?.snapshot?.isRunning)
        assertEquals(0, c.state.value?.snapshot?.phaseIndex)
    }

    @Test
    fun `l uscita audio resta sveglia solo mentre il timer corre`() = runTest {
        val states = mutableListOf<Boolean>()
        val c = TimerController(
            clock,
            store,
            { },
            { },
            object : CueSink {
                override fun play(cues: List<Cue>, ui: RunningUi) = Unit

                override fun onRunningChanged(running: Boolean) {
                    states += running
                }
            },
            backgroundScope
        )
        c.start(run)
        assertEquals(true, states.last())
        c.togglePause()
        assertEquals(false, states.last())
        c.togglePause()
        clock.now += 2_000_000
        c.tick()
        assertEquals(false, states.last())
    }

    private fun TestScope.sessionController(heard: MutableList<Cue>, starter: NextBlockStarter) = TimerController(
        clock = clock,
        store = store,
        launcher = { },
        completion = BlockCompletionSink { completed += it },
        cues = { c, _ -> heard += c },
        scope = backgroundScope,
        nextBlock = starter
    )

    private fun blockRun(title: String, ids: List<Long>) =
        ActiveRun(title, IntervalConfig(5, 7, 3, 2, 1, 0, 0), TimerRunState(), workoutId = 1, blockLogIds = ids)

    @Test
    fun `a fine blocco parte da solo il successivo della seduta`() = runTest {
        val heard = mutableListOf<Cue>()
        val c = sessionController(heard) { _, blockId -> if (blockId == 2L) blockRun("Ripetute", listOf(21)) else null }
        c.start(run.copy(queue = listOf(QueuedBlock(2, "Ripetute")), blockNumber = 1, blockCount = 2))
        assertEquals("Ripetute", c.state.value?.nextBlockName)
        clock.now += 1_842_000
        c.tick()
        runCurrent()
        val ui = c.state.value
        assertEquals("Ripetute", ui?.title)
        assertEquals(2, ui?.blockNumber)
        assertEquals(2, ui?.blockCount)
        assertEquals(true, ui?.snapshot?.isRunning)
        assertEquals(listOf(11L, 12L), completed)
        assertTrue(heard.none { it == Cue.Finished })
        assertEquals(listOf(21L), store.run?.blockLogIds)
    }

    @Test
    fun `blocco eliminato nel frattempo viene saltato`() = runTest {
        val c = sessionController(mutableListOf()) { _, blockId -> if (blockId == 3L) blockRun("Forza", listOf(31)) else null }
        c.start(run.copy(queue = listOf(QueuedBlock(2, "Sparito"), QueuedBlock(3, "Forza")), blockNumber = 1, blockCount = 3))
        clock.now += 1_842_000
        c.tick()
        runCurrent()
        assertEquals("Forza", c.state.value?.title)
        assertEquals(3, c.state.value?.blockNumber)
    }

    @Test
    fun `modificare il timer in corso mantiene il punto raggiunto`() = runTest {
        val c = controller()
        c.start(run)
        clock.now += 12_500 // lavoro del round 1, 2,5 s dentro
        c.updateConfig(IntervalConfig(10, 8, 3, 30, 6, 10, 0))
        runCurrent()
        val s = c.state.value?.snapshot
        assertEquals(1, s?.phaseIndex)
        assertEquals(5_500L, s?.phaseRemainingMs)
        assertEquals(true, s?.isRunning)
        assertEquals(8, store.run?.config?.workSec)
    }

    @Test
    fun `modificare il timer fermo su prepara lo lascia fermo`() = runTest {
        val c = controller()
        c.start(run, autoStart = false)
        c.updateConfig(IntervalConfig(5, 7, 3, 30, 6, 10, 0))
        val s = c.state.value?.snapshot
        assertEquals(false, s?.isRunning)
        assertEquals(0, s?.phaseIndex)
        assertEquals(5_000L, s?.phaseRemainingMs)
    }

    @Test
    fun `un timer mai avviato si riconosce`() = runTest {
        val c = controller()
        c.start(run, autoStart = false)
        assertEquals(false, c.hasStarted())
        c.togglePause()
        assertEquals(true, c.hasStarted())
        c.togglePause()
        assertEquals(true, c.hasStarted())
    }

    @Test
    fun `cronometro conta il tempo, avvisa al minuto esatto e segna i giri`() = runTest {
        val heard = mutableListOf<Pair<Long, Cue>>()
        val c = TimerController(
            clock = Clock { testScheduler.currentTime },
            store = store,
            launcher = { },
            completion = { },
            cues = { cues, _ -> cues.forEach { heard += testScheduler.currentTime to it } },
            scope = backgroundScope
        )
        c.start(ActiveRun("Cronometro", IntervalConfig(), TimerRunState(), mode = RunMode.STOPWATCH, alertEverySec = 60))
        advanceTimeBy(65_050)
        assertEquals(listOf(60_000L to Cue.Interval(1)), heard)
        assertEquals(65_000L, c.state.value?.elapsedMs)
        c.lap()
        c.togglePause()
        advanceTimeBy(30_000)
        val ui = c.state.value
        assertEquals(65_050L, ui?.elapsedMs)
        assertEquals(listOf(65_050L), ui?.laps)
        assertEquals(1, ui?.alertCount)
        c.setAlertEvery(30)
        assertEquals(2, c.state.value?.alertCount)
    }

    @Test
    fun `stop azzera stato e salvataggio`() = runTest {
        val c = controller()
        c.start(run)
        c.stop()
        runCurrent()
        assertNull(c.state.value)
        assertNull(store.run)
    }
}
