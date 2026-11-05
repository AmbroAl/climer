package io.github.ambroal.climer.ui.nav

import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.RunMode
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `segnala un timer in corso per poterci tornare`() = runTest {
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
        val vm = AppViewModel(controller, FakeSettingsRepository(), TimerRepository(db) { 0L })
        assertFalse(vm.timerActive.value)
        controller.start(ActiveRun("Round", IntervalConfig(), TimerRunState()))
        assertTrue(vm.timerActive.first { it })
        controller.stop()
        assertFalse(vm.timerActive.first { !it })
        controller.start(ActiveRun("Cronometro", IntervalConfig(), TimerRunState(), mode = RunMode.STOPWATCH))
        assertEquals(RunMode.STOPWATCH, vm.activeMode.first { it != null })
    }
}
