package io.github.ambroal.climer.ui.measures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.repo.MeasureRepository
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.format.parseDecimalOrNull
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryForm(
    val id: Long = 0,
    val perSide: Boolean,
    val date: LocalDate,
    val value: String = "",
    val right: String = "",
    val left: String = "",
    val note: String = ""
) {
    private fun invalid(text: String) = text.isNotBlank() && parseDecimalOrNull(text) == null

    val valueError: Boolean get() = invalid(value)
    val rightError: Boolean get() = invalid(right)
    val leftError: Boolean get() = invalid(left)

    val canSave: Boolean get() = if (perSide) {
        (right.isNotBlank() || left.isNotBlank()) && !rightError && !leftError
    } else {
        value.isNotBlank() && !valueError
    }
}

data class MeasureDetailUiState(
    val type: MeasureTypeEntity? = null,
    val entries: List<MeasureEntryEntity> = emptyList(),
    val entryForm: EntryForm? = null,
    val deleted: Boolean = false
)

private fun Double?.asText() = this?.let(::formatKg).orEmpty()

@HiltViewModel
class MeasureDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: MeasureRepository) : ViewModel() {
    private val typeId: Long = savedStateHandle.get<Long>("typeId") ?: 0L
    private val form = MutableStateFlow<EntryForm?>(null)
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<MeasureDetailUiState> =
        combine(repo.observeType(typeId), repo.observeEntries(typeId), form, deleted) { type, entries, f, d ->
            MeasureDetailUiState(type, entries, f, d)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MeasureDetailUiState())

    fun openNewEntry(today: LocalDate = LocalDate.now()) {
        val type = state.value.type ?: return
        form.value = EntryForm(perSide = type.perSide, date = today)
    }

    fun openEditEntry(entry: MeasureEntryEntity) {
        val type = state.value.type ?: return
        form.value =
            EntryForm(entry.id, type.perSide, entry.date, entry.value.asText(), entry.right.asText(), entry.left.asText(), entry.note)
    }

    fun closeEntry() {
        form.value = null
    }

    fun onEntryDate(date: LocalDate) = form.update { it?.copy(date = date) }

    fun onEntryValue(text: String) = form.update { it?.copy(value = text) }

    fun onEntryRight(text: String) = form.update { it?.copy(right = text) }

    fun onEntryLeft(text: String) = form.update { it?.copy(left = text) }

    fun onEntryNote(text: String) = form.update { it?.copy(note = text) }

    fun saveEntry() {
        val f = form.value ?: return
        if (!f.canSave) return
        viewModelScope.launch {
            repo.saveEntry(
                MeasureEntryEntity(
                    id = f.id,
                    typeId = typeId,
                    date = f.date,
                    value = if (f.perSide) null else parseDecimalOrNull(f.value),
                    right = if (f.perSide) parseDecimalOrNull(f.right) else null,
                    left = if (f.perSide) parseDecimalOrNull(f.left) else null,
                    note = f.note.trim()
                )
            )
            form.value = null
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    fun deleteType() {
        viewModelScope.launch {
            repo.deleteType(typeId)
            deleted.value = true
        }
    }
}
