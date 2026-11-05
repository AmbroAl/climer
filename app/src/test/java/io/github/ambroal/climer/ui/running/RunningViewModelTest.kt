package io.github.ambroal.climer.ui.running

import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.data.repo.toActiveRun
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunningViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val timers by lazy { TimerRepository(db) { 0L } }
    private val programs by lazy { ProgramRepository(db, settings) }
    private val workouts by lazy { WorkoutRepository(db, settings) { 0L } }

    private fun TestScope.controller() = TimerController(
        clock = FakeClock(),
        store = object : ActiveRunStore {
            override suspend fun save(run: ActiveRun?) = Unit

            override suspend fun load(): ActiveRun? = null
        },
        launcher = { },
        completion = { },
        cues = { _, _ -> },
        scope = backgroundScope
    )

    private fun TestScope.vm(controller: TimerController) = RunningViewModel(controller, settings, timers, programs, workouts)

    @Test
    fun `la modifica dal menu viene salvata nel timer`() = runTest {
        val id = timers.save(SavedTimerEntity(name = "TEST", colorArgb = 0, config = IntervalConfig(), createdAt = 0))
        val controller = controller()
        controller.start(ActiveRun("TEST", IntervalConfig(), TimerRunState(), savedTimerId = id), autoStart = false)
        val newConfig = IntervalConfig(5, 20, 10, 4, 2, 60, 0)
        vm(controller).applyConfig(newConfig)
        assertEquals(newConfig, timers.get(id)?.config)
        assertEquals(newConfig, controller.state.value?.config)
    }

    @Test
    fun `la modifica dal menu aggiorna il blocco e le ripetute previste`() = runTest {
        val session = programs.addSession(programs.saveProgram(ProgramEntity(name = "P")), "S")
        val blockId = programs.saveBlock(
            BlockEntity(
                sessionId = session,
                position = 0,
                name = "Round",
                sideMode = SideMode.ALTERNATE_RIGHT_FIRST,
                config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
            )
        )
        val workout = workouts.startWorkout(session)
        val controller = controller()
        controller.start(workouts.startBlock(workout, blockId).toActiveRun(workout), autoStart = false)
        val newConfig = IntervalConfig(10, 7, 3, 20, 4, 10, 0)
        vm(controller).applyConfig(newConfig)
        assertEquals(newConfig, programs.getBlock(blockId)?.config)
        assertEquals(listOf(40, 40), db.workoutDao().getBlockLogs(workout).map { it.plannedReps })
    }

    @Test
    fun `uscire da un blocco mai avviato lo chiude e toglie la registrazione vuota`() = runTest {
        val session = programs.addSession(programs.saveProgram(ProgramEntity(name = "P")), "S")
        val blockId = programs.saveBlock(BlockEntity(sessionId = session, position = 0, name = "Round", config = IntervalConfig()))
        val workout = workouts.startWorkout(session)
        val controller = controller()
        controller.start(workouts.startBlock(workout, blockId).toActiveRun(workout), autoStart = false)
        assertEquals(true, vm(controller).discardIfUnstarted())
        assertNull(controller.state.value)
        assertNull(db.workoutDao().getWorkout(workout))
    }

    @Test
    fun `uscire da un timer gia avviato non lo chiude`() = runTest {
        val controller = controller()
        controller.start(ActiveRun("TEST", IntervalConfig(), TimerRunState()))
        controller.togglePause()
        assertEquals(false, vm(controller).discardIfUnstarted())
        assertNotNull(controller.state.value)
    }
}
