package io.github.ambroal.climer.ui.measures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.repo.MeasureRepository
import io.github.ambroal.climer.data.repo.MeasureTypeWithLatest
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MeasureListUiState(val loading: Boolean = true, val types: List<MeasureTypeWithLatest> = emptyList())

@HiltViewModel
class MeasureListViewModel @Inject constructor(repo: MeasureRepository) : ViewModel() {
    val state: StateFlow<MeasureListUiState> = repo.observeTypesWithLatest()
        .map { MeasureListUiState(loading = false, types = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasureListUiState())
}
