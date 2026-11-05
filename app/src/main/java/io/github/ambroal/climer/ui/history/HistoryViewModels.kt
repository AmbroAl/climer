package io.github.ambroal.climer.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.repo.WorkoutRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(val loading: Boolean = true, val workouts: List<WorkoutLogEntity> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(workouts: WorkoutRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = workouts.history()
        .map { HistoryUiState(loading = false, workouts = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

data class WorkoutDetailUiState(
    val workout: WorkoutLogEntity? = null,
    val logs: List<BlockLogEntity> = emptyList(),
    val deleted: Boolean = false
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val workouts: WorkoutRepository) :
    ViewModel() {
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<WorkoutDetailUiState> =
        combine(workouts.observeWorkout(id), workouts.observeBlockLogs(id), deleted) { w, logs, d -> WorkoutDetailUiState(w, logs, d) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    fun delete() {
        viewModelScope.launch {
            workouts.delete(id)
            deleted.value = true
        }
    }
}
