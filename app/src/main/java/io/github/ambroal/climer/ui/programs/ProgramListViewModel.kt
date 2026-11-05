package io.github.ambroal.climer.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.repo.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgramListUiState(val loading: Boolean = true, val programs: List<ProgramEntity> = emptyList())

@HiltViewModel
class ProgramListViewModel @Inject constructor(private val repo: ProgramRepository) : ViewModel() {
    val state: StateFlow<ProgramListUiState> = repo.observePrograms()
        .map { ProgramListUiState(loading = false, programs = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramListUiState())

    suspend fun create(name: String): Long = repo.saveProgram(ProgramEntity(name = name.trim()))
}
