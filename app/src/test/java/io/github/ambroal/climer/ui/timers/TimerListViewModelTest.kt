package io.github.ambroal.climer.ui.timers

import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.TimerController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimerListViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `toccare un timer lo apre in pausa`() = runTest {
        val controller = TimerController(
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
        val vm = TimerListViewModel(TimerRepository(db) { 0L }, controller, FakeSettingsRepository())
        vm.start(SavedTimerEntity(id = 1, name = "TEST", colorArgb = 0, config = IntervalConfig(), createdAt = 0))
        assertEquals("TEST", controller.state.value?.title)
        assertEquals(false, controller.state.value?.snapshot?.isRunning)
    }
}
