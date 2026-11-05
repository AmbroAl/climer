package io.github.ambroal.climer.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.ui.timers.sanitized
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RunningViewModel @Inject constructor(
    private val controller: TimerController,
    settings: SettingsRepository,
    private val timers: TimerRepository,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository
) : ViewModel() {
    val state: StateFlow<RunningUiState> = combine(controller.state, settings.settings, ::toRunningUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toRunningUiState(controller.state.value, AppSettings()))

    fun togglePause() = controller.togglePause()

    fun skipTo(index: Int) = controller.skipTo(index)

    fun restart() = controller.restart()

    fun stop() = controller.stop()

    /**
     * Uscendo da un timer mai avviato lo si chiude, invece di lasciarlo "in corso"; per un blocco si toglie
     * anche la registrazione vuota. True se il timer è stato chiuso.
     */
    suspend fun discardIfUnstarted(): Boolean {
        if (controller.hasStarted()) return false
        val ui = controller.state.value ?: return false
        controller.stop()
        ui.workoutId?.let { workouts.discardBlockLogs(it, ui.blockLogIds) }
        return true
    }

    /** Applica la modifica al timer in corso e la salva nel timer o nel blocco di origine. */
    suspend fun applyConfig(config: IntervalConfig) {
        val ui = controller.state.value ?: return
        val clean = config.sanitized()
        controller.updateConfig(clean)
        ui.savedTimerId?.let { id -> timers.get(id)?.let { timers.save(it.copy(config = clean)) } }
        ui.blockId?.let { id ->
            val block = programs.getBlock(id) ?: return@let
            programs.saveBlock(block.copy(config = clean))
            workouts.updatePlannedReps(ui.blockLogIds, clean, block.sideMode)
        }
    }
}
