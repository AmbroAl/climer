package io.github.ambroal.climer.ui.stopwatch

import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.FakeClock
import io.github.ambroal.climer.timer.RunMode
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StopwatchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val settings = FakeSettingsRepository(AppSettings(stopwatchAlertSec = 30))

    private fun TestScope.controller() = TimerController(
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

    @Test
    fun `i giri mostrano tempo del giro e totale, il piu recente in alto`() {
        val rows = lapRows(listOf(65_050, 100_000))
        assertEquals(listOf(LapRow(2, 34_950, 100_000), LapRow(1, 65_050, 65_050)), rows)
    }

    @Test
    fun `avvia, pausa e azzera`() = runTest {
        val controller = controller()
        val vm = StopwatchViewModel(controller, settings)
        vm.toggle()
        assertEquals(RunMode.STOPWATCH, controller.state.value?.mode)
        clock.now += 5_000
        vm.lap()
        vm.toggle()
        assertEquals(false, controller.state.value?.snapshot?.isRunning)
        assertEquals(listOf(5_000L), controller.state.value?.laps)
        vm.reset()
        assertNull(controller.state.value)
    }

    @Test
    fun `spegnere l avviso lo toglie dal cronometro in corso, riaccenderlo usa lo stesso intervallo`() = runTest {
        val controller = controller()
        val vm = StopwatchViewModel(controller, settings)
        vm.toggle()
        clock.now += 31_000
        controller.tick()
        assertEquals(1, controller.state.value?.alertCount)
        vm.setAlertOn(false)
        assertEquals(false, settings.state.value.stopwatchAlertOn)
        assertEquals(30, settings.state.value.stopwatchAlertSec)
        assertEquals(0, controller.state.value?.alertCount)
        vm.setAlertOn(true)
        assertEquals(1, controller.state.value?.alertCount)
    }

    @Test
    fun `con l avviso spento il cronometro parte senza avvisi`() = runTest {
        val controller = controller()
        settings.state.value = settings.state.value.copy(stopwatchAlertOn = false)
        val vm = StopwatchViewModel(controller, settings)
        vm.toggle()
        clock.now += 31_000
        controller.tick()
        assertEquals(0, controller.state.value?.alertCount)
    }

    @Test
    fun `scegliere zero spegne l avviso e tiene l intervallo di prima`() = runTest {
        val vm = StopwatchViewModel(controller(), settings)
        vm.setAlertEvery(0)
        assertEquals(false, settings.state.value.stopwatchAlertOn)
        assertEquals(30, settings.state.value.stopwatchAlertSec)
        vm.setAlertEvery(45)
        assertEquals(true, settings.state.value.stopwatchAlertOn)
        assertEquals(45, settings.state.value.stopwatchAlertSec)
    }

    @Test
    fun `con un timer in corso chiede conferma prima di sostituirlo`() = runTest {
        val controller = controller()
        controller.start(ActiveRun("Round", IntervalConfig(), TimerRunState()))
        val vm = StopwatchViewModel(controller, settings)
        vm.toggle()
        assertEquals(RunMode.TIMER, controller.state.value?.mode)
        assertTrue(vm.state.first { it.confirmReplace }.confirmReplace)
        vm.confirmReplace()
        assertEquals(RunMode.STOPWATCH, controller.state.value?.mode)
    }
}
