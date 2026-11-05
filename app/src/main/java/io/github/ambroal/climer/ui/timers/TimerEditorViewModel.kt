package io.github.ambroal.climer.ui.timers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.interval.MAX_CYCLES
import io.github.ambroal.climer.domain.interval.MAX_ROUNDS
import io.github.ambroal.climer.domain.interval.NewIntervalConfig
import io.github.ambroal.climer.timer.totalDurationMs
import io.github.ambroal.climer.ui.theme.Palette
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerEditorUiState(
    val id: Long = 0,
    val name: String = "",
    val colorArgb: Long = Palette[3],
    val config: IntervalConfig = NewIntervalConfig,
    val createdAt: Long = 0,
    val loaded: Boolean = false,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank()
    val totalMs: Long get() = config.totalDurationMs()
}

fun IntervalConfig.sanitized() = copy(
    prepareSec = prepareSec.coerceAtLeast(0),
    workSec = workSec.coerceAtLeast(1),
    restSec = restSec.coerceAtLeast(0),
    rounds = rounds.coerceIn(1, MAX_ROUNDS),
    cycles = cycles.coerceIn(1, MAX_CYCLES),
    restBetweenCyclesSec = restBetweenCyclesSec.coerceAtLeast(0),
    cooldownSec = cooldownSec.coerceAtLeast(0)
)

@HiltViewModel
class TimerEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: TimerRepository) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(TimerEditorUiState(loaded = id == 0L))
    val state: StateFlow<TimerEditorUiState> = _state.asStateFlow()

    init {
        if (id != 0L) {
            viewModelScope.launch {
                val timer = repo.get(id)
                _state.update {
                    if (timer == null) {
                        it.copy(loaded = true)
                    } else {
                        TimerEditorUiState(timer.id, timer.name, timer.colorArgb, timer.config, timer.createdAt, loaded = true)
                    }
                }
            }
        }
    }

    fun onName(name: String) = _state.update { it.copy(name = name) }

    fun onColor(argb: Long) = _state.update { it.copy(colorArgb = argb) }

    fun onConfig(config: IntervalConfig) = _state.update { it.copy(config = config.sanitized()) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.save(SavedTimerEntity(s.id, s.name.trim(), s.colorArgb, s.config, s.createdAt))
            _state.update { it.copy(saved = true) }
        }
    }
}
