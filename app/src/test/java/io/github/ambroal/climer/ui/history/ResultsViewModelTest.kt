package io.github.ambroal.climer.ui.history

import androidx.lifecycle.SavedStateHandle
import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutStatus
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.domain.load.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResultsViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val workouts by lazy { WorkoutRepository(db, FakeSettingsRepository()) { 5_000L } }

    private suspend fun vm(): Pair<ResultsViewModel, Long> {
        val w = db.workoutDao().insertWorkout(
            WorkoutLogEntity(sourceSessionId = 1, programName = "P", sessionName = "S", startedAt = 1_000)
        )
        db.workoutDao().insertBlockLogs(
            listOf(
                BlockLogEntity(
                    workoutId = w,
                    sourceBlockId = 1,
                    position = 0,
                    blockName = "Round",
                    side = Side.RIGHT,
                    plannedKg = 17.5,
                    plannedReps = 90,
                    completed = true
                ),
                BlockLogEntity(
                    workoutId = w,
                    sourceBlockId = 1,
                    position = 1,
                    blockName = "Round",
                    side = Side.LEFT,
                    plannedKg = 16.5,
                    plannedReps = 90,
                    completed = true
                )
            )
        )
        val vm = ResultsViewModel(SavedStateHandle(mapOf("workoutId" to w)), workouts)
        vm.state.first { it.loaded }
        return vm to w
    }

    @Test
    fun `si chiede solo la nota e i blocchi restano come registrati`() = runTest {
        val (vm, w) = vm()
        val before = db.workoutDao().getBlockLogs(w)
        vm.onNote("Dita stanche")
        vm.save()
        vm.state.first { it.saved }
        assertEquals(before, db.workoutDao().getBlockLogs(w))
        assertEquals("Dita stanche", db.workoutDao().getWorkout(w)?.note)
    }

    @Test
    fun `dopo il salvataggio la seduta e chiusa`() = runTest {
        val (vm, w) = vm()
        vm.onNote("Buona seduta")
        vm.save()
        vm.state.first { it.saved }
        val workout = db.workoutDao().getWorkout(w)
        assertEquals(WorkoutStatus.DONE, workout?.status)
        assertEquals("Buona seduta", workout?.note)
        assertEquals(5_000L, workout?.endedAt)
        assertNull(workouts.inProgress().first())
    }
}
