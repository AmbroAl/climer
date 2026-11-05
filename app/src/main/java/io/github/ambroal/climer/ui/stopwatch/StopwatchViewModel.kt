package io.github.ambroal.climer.ui.stopwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.data.settings.stopwatchAlertEverySec
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.RunMode
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LapRow(val number: Int, val splitMs: Long, val totalMs: Long)

/** Giri dal più recente: per ognuno il tempo del giro e il totale. */
fun lapRows(laps: List<Long>): List<LapRow> =
    laps.mapIndexed { i, total -> LapRow(i + 1, total - (laps.getOrNull(i - 1) ?: 0), total) }.reversed()

data class StopwatchUiState(
    val active: Boolean = false,
    val running: Boolean = false,
    val elapsedMs: Long = 0,
    val laps: List<LapRow> = emptyList(),
    val alertCount: Int = 0,
    /** C'è un timer (non il cronometro) in corso: avviare il cronometro lo sostituirebbe. */
    val confirmReplace: Boolean = false,
    val settings: AppSettings = AppSettings()
)

@HiltViewModel
class StopwatchViewModel @Inject constructor(private val controller: TimerController, private val settingsRepo: SettingsRepository) :
    ViewModel() {
    private val confirm = MutableStateFlow(false)

    val state: StateFlow<StopwatchUiState> = combine(controller.state, settingsRepo.settings, confirm) { ui, s, c ->
        val sw = ui?.takeIf { it.mode == RunMode.STOPWATCH }
        StopwatchUiState(
            active = sw != null,
            running = sw?.snapshot?.isRunning == true,
            elapsedMs = sw?.elapsedMs ?: 0,
            laps = lapRows(sw?.laps.orEmpty()),
            alertCount = sw?.alertCount ?: 0,
            confirmReplace = c,
            settings = s
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StopwatchUiState())

    private val isStopwatch get() = controller.state.value?.mode == RunMode.STOPWATCH

    fun toggle() {
        when {
            isStopwatch -> controller.togglePause()
            controller.state.value != null -> confirm.value = true
            else -> startNew()
        }
    }

    fun confirmReplace() {
        confirm.value = false
        startNew()
    }

    fun dismissReplace() {
        confirm.value = false
    }

    private fun startNew() {
        val every = state.value.settings.stopwatchAlertEverySec
        controller.start(ActiveRun("Cronometro", IntervalConfig(), TimerRunState(), mode = RunMode.STOPWATCH, alertEverySec = every))
    }

    fun lap() {
        if (isStopwatch) controller.lap()
    }

    fun reset() {
        if (isStopwatch) controller.stop()
    }

    fun setAlertOn(on: Boolean) = updateAlert { it.copy(stopwatchAlertOn = on) }

    /** Zero spegne l'avviso ma tiene l'intervallo di prima, così riaccendendolo si ritrova. */
    fun setAlertEvery(seconds: Int) = updateAlert {
        if (seconds > 0) it.copy(stopwatchAlertOn = true, stopwatchAlertSec = seconds) else it.copy(stopwatchAlertOn = false)
    }

    private fun updateAlert(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            var updated: AppSettings? = null
            settingsRepo.update { transform(it).also { new -> updated = new } }
            val every = updated?.stopwatchAlertEverySec ?: return@launch
            if (isStopwatch) controller.setAlertEvery(every)
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }
}
