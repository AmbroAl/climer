package io.github.ambroal.climer.ui.session

import androidx.lifecycle.SavedStateHandle
import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.TimerController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val programs by lazy { ProgramRepository(db, settings) }
    private val workouts by lazy { WorkoutRepository(db, settings) { 1_000L } }

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

    private suspend fun seed(): Triple<Long, Long, Long> {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Senza misure", unit = "kg", perSide = true))
        val session = programs.addSession(programs.saveProgram(ProgramEntity(name = "No-hang")), "Lunedì")
        fun block(name: String) = BlockEntity(
            sessionId = session,
            position = 0,
            name = name,
            sideMode = SideMode.ALTERNATE_RIGHT_FIRST,
            load = LoadSpec.PercentOfMax(type, 55.0),
            config = IntervalConfig(10, 7, 3, 6, 6, 90, 0)
        )
        return Triple(session, programs.saveBlock(block("Forza")), programs.saveBlock(block("Ripetute")))
    }

    @Test
    fun `due blocchi nella stessa seduta registrata`() = runTest {
        val (session, a, b) = seed()
        val controller = controller()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller)
        val w1 = vm.startBlock(a)
        val w2 = vm.startBlock(b)
        assertEquals(w1, w2)
        assertEquals(listOf("Forza", "Forza", "Ripetute", "Ripetute"), db.workoutDao().getBlockLogs(w1).map { it.blockName })
        assertEquals("Ripetute", controller.state.value?.title)
    }

    @Test
    fun `blocco completato appare spuntato`() = runTest {
        val (session, a, _) = seed()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller())
        val w = vm.startBlock(a)
        workouts.markCompleted(db.workoutDao().getBlockLogs(w).map { it.id })
        val s = vm.state.first { a in it.doneBlockIds }
        assertEquals(w, s.workoutId)
    }

    @Test
    fun `avvia seduta parte dal primo blocco non fatto e mette in coda gli altri`() = runTest {
        val (session, a, _) = seed()
        programs.saveBlock(
            BlockEntity(sessionId = session, position = 0, name = "Defaticamento", config = IntervalConfig(0, 30, 0, 1, 1, 0, 0))
        )
        val controller = controller()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller)
        val w = vm.startBlock(a)
        workouts.markCompleted(db.workoutDao().getBlockLogs(w).map { it.id })
        vm.state.first { a in it.doneBlockIds && it.blocks.size == 3 }
        assertEquals(w, vm.startSession())
        val ui = controller.state.value
        assertEquals("Ripetute", ui?.title)
        assertEquals(2, ui?.blockNumber)
        assertEquals(3, ui?.blockCount)
        assertEquals("Defaticamento", ui?.nextBlockName)
    }

    @Test
    fun `il blocco singolo si apre in pausa, la seduta parte da sola`() = runTest {
        val (session, a, _) = seed()
        val controller = controller()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller)
        vm.startBlock(a)
        assertEquals(false, controller.state.value?.snapshot?.isRunning)
        vm.state.first { it.blocks.size == 2 }
        vm.startSession()
        assertEquals(true, controller.state.value?.snapshot?.isRunning)
    }

    @Test
    fun `riavvia seduta azzera i progressi senza far partire nulla`() = runTest {
        val (session, a, _) = seed()
        val controller = controller()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller)
        val old = vm.startBlock(a)
        workouts.markCompleted(db.workoutDao().getBlockLogs(old).map { it.id })
        vm.state.first { a in it.doneBlockIds && it.blocks.size == 2 }
        vm.restartSession()
        assertNull(db.workoutDao().getWorkout(old))
        assertNull(controller.state.value)
        vm.state.first { it.workoutId == null && it.doneBlockIds.isEmpty() }
    }

    @Test
    fun `massimale mancante non blocca l avvio`() = runTest {
        val (session, a, _) = seed()
        val controller = controller()
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to session)), programs, workouts, controller)
        val s = vm.state.first { it.blocks.isNotEmpty() }
        assertTrue(s.blocks.first().missingMax)
        vm.startBlock(a)
        assertNotNull(controller.state.value)
        assertEquals(null, controller.state.value?.current?.kg)
    }
}
