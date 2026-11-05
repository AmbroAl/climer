package io.github.ambroal.climer.ui.timers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.timer.TimerRunState
import io.github.ambroal.climer.timer.totalDurationMs
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerRow(val timer: SavedTimerEntity, val totalMs: Long)

data class TimerListUiState(val loading: Boolean = true, val timers: List<TimerRow> = emptyList())

fun startSavedTimer(controller: TimerController, timer: SavedTimerEntity, autoStart: Boolean = true) = controller.start(
    ActiveRun(title = timer.name, config = timer.config, runState = TimerRunState(), savedTimerId = timer.id),
    autoStart
)

@HiltViewModel
class TimerListViewModel @Inject constructor(
    private val repo: TimerRepository,
    private val controller: TimerController,
    private val settings: SettingsRepository
) : ViewModel() {
    val state: StateFlow<TimerListUiState> = repo.observeAll()
        .map { list -> TimerListUiState(loading = false, timers = list.map { TimerRow(it, it.config.totalDurationMs()) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerListUiState())

    fun start(timer: SavedTimerEntity) {
        startSavedTimer(controller, timer, autoStart = false)
        viewModelScope.launch { settings.update { it.copy(lastTimerId = timer.id) } }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch { repo.duplicate(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
