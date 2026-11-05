package io.github.ambroal.climer.ui.measures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.repo.MeasureRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasureTypeEditorUiState(
    val id: Long = 0,
    val name: String = "",
    val unit: String = "kg",
    val perSide: Boolean = true,
    /** Con misure già registrate cambiare "per mano" renderebbe illeggibile lo storico. */
    val perSideLocked: Boolean = false,
    val loaded: Boolean = false,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class MeasureTypeEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: MeasureRepository) :
    ViewModel() {
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(MeasureTypeEditorUiState(loaded = id == 0L))
    val state: StateFlow<MeasureTypeEditorUiState> = _state.asStateFlow()

    init {
        if (id != 0L) {
            viewModelScope.launch {
                val type = repo.getType(id)
                val locked = repo.hasEntries(id)
                _state.update {
                    if (type == null) {
                        it.copy(loaded = true)
                    } else {
                        MeasureTypeEditorUiState(type.id, type.name, type.unit, type.perSide, locked, loaded = true)
                    }
                }
            }
        }
    }

    fun onName(name: String) = _state.update { it.copy(name = name) }

    fun onUnit(unit: String) = _state.update { it.copy(unit = unit) }

    fun onPerSide(perSide: Boolean) = _state.update { if (it.perSideLocked) it else it.copy(perSide = perSide) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.saveType(MeasureTypeEntity(s.id, s.name.trim(), s.unit.trim(), s.perSide))
            _state.update { it.copy(saved = true) }
        }
    }
}
