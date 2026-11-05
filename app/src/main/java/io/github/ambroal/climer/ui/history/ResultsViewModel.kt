package io.github.ambroal.climer.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.repo.WorkoutRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Per ora a fine seduta si chiede solo una nota: i blocchi restano come li ha registrati il timer. */
data class ResultsUiState(
    val workout: WorkoutLogEntity? = null,
    val note: String = "",
    val loaded: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class ResultsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val workouts: WorkoutRepository) : ViewModel() {
    private val workoutId: Long = savedStateHandle.get<Long>("workoutId") ?: 0L
    private val _state = MutableStateFlow(ResultsUiState())
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workouts.pruneAbandoned(workoutId)
            val workout = workouts.getWorkout(workoutId)
            _state.value = ResultsUiState(workout, workout?.note.orEmpty(), loaded = true)
        }
    }

    fun onNote(text: String) = _state.update { it.copy(note = text) }

    fun save() {
        if (!_state.value.loaded) return
        val note = _state.value.note.trim()
        viewModelScope.launch {
            workouts.saveNote(workoutId, note)
            workouts.finish(workoutId)
            _state.update { it.copy(saved = true) }
        }
    }
}
