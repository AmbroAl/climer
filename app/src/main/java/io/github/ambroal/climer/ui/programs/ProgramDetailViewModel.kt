package io.github.ambroal.climer.ui.programs

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.files.AttachmentStore
import io.github.ambroal.climer.data.repo.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgramDetailUiState(
    val program: ProgramEntity? = null,
    val sessions: List<SessionTemplateEntity> = emptyList(),
    val attachmentError: Boolean = false,
    val deleted: Boolean = false
)

@HiltViewModel
class ProgramDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ProgramRepository,
    private val attachments: AttachmentStore
) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L
    private val attachmentError = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<ProgramDetailUiState> =
        combine(repo.observeProgram(id), repo.observeSessions(id), attachmentError, deleted) { p, s, e, d ->
            ProgramDetailUiState(p, s, e, d)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramDetailUiState())

    private fun updateProgram(transform: (ProgramEntity) -> ProgramEntity) {
        val program = state.value.program ?: return
        viewModelScope.launch { repo.saveProgram(transform(program)) }
    }

    fun rename(name: String) = updateProgram { it.copy(name = name) }

    fun setNotes(notes: String) = updateProgram { it.copy(notes = notes) }

    fun addSession(name: String) {
        viewModelScope.launch { repo.addSession(id, name) }
    }

    fun renameSession(session: SessionTemplateEntity, name: String) {
        viewModelScope.launch { repo.saveSession(session.copy(name = name)) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }

    fun reorderSessions(orderedIds: List<Long>) {
        viewModelScope.launch { repo.reorderSessions(id, orderedIds) }
    }

    fun moveSession(from: Int, to: Int) {
        viewModelScope.launch { repo.moveSession(id, from, to) }
    }

    fun attach(uri: Uri) {
        val program = state.value.program ?: return
        viewModelScope.launch {
            runCatching { attachments.import(id, uri) }
                .onSuccess { a ->
                    attachmentError.value = false
                    repo.saveProgram(program.copy(attachmentPath = a.path, attachmentName = a.name, attachmentMime = a.mime))
                }
                .onFailure { attachmentError.value = true }
        }
    }

    fun attachmentUri(): Uri? = state.value.program?.attachmentPath?.let(attachments::uriFor)

    fun removeAttachment() {
        val program = state.value.program ?: return
        viewModelScope.launch {
            program.attachmentPath?.let { attachments.delete(it) }
            repo.saveProgram(program.copy(attachmentPath = null, attachmentName = null, attachmentMime = null))
        }
    }

    fun deleteProgram() {
        val program = state.value.program ?: return
        viewModelScope.launch {
            program.attachmentPath?.let { attachments.delete(it) }
            repo.deleteProgram(program.id)
            deleted.value = true
        }
    }
}
