package io.github.ambroal.climer.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.repo.BlockWithLoad
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.data.repo.toActiveRun
import io.github.ambroal.climer.timer.QueuedBlock
import io.github.ambroal.climer.timer.TimerController
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiState(
    val loading: Boolean = true,
    val session: SessionTemplateEntity? = null,
    val blocks: List<BlockWithLoad> = emptyList(),
    val workoutId: Long? = null,
    val doneBlockIds: Set<Long> = emptySet(),
    /** Seduta lasciata aperta altrove: va chiusa o ripresa prima di confonderle. */
    val otherInProgress: WorkoutLogEntity? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    private val controller: TimerController
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    private val current = workouts.inProgressForSession(sessionId)
    private val done = current.flatMapLatest { w -> w?.let { completedBlockIds(it.id) } ?: flowOf(emptySet()) }
    private val other = workouts.inProgress().map { w -> w?.takeIf { it.sourceSessionId != sessionId } }

    val state: StateFlow<SessionUiState> =
        combine(programs.observeSession(sessionId), programs.blocksWithLoads(sessionId), current, done, other) { s, blocks, w, d, o ->
            SessionUiState(loading = false, session = s, blocks = blocks, workoutId = w?.id, doneBlockIds = d, otherInProgress = o)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState())

    private fun completedBlockIds(workoutId: Long) =
        workouts.observeBlockLogs(workoutId).map { logs -> logs.filter { it.completed }.mapNotNull { it.sourceBlockId }.toSet() }

    /** Apre (o riprende) la seduta registrata, congela il blocco e avvia il timer. Ritorna l'id della seduta registrata. */
    suspend fun startBlock(blockId: Long): Long {
        val workoutId = workouts.startWorkout(sessionId)
        val start = workouts.startBlock(workoutId, blockId)
        // Blocco singolo: si apre fermo su "Prepara" e parte quando premi play.
        controller.start(start.toActiveRun(workoutId), autoStart = false)
        return workoutId
    }

    /**
     * Avvia tutti i blocchi in fila, dal primo non ancora fatto in questa seduta: alla fine di ognuno parte il successivo.
     * Null se la seduta non ha blocchi.
     */
    suspend fun startSession(): Long? {
        val first = state.value.blocks.indexOfFirst { it.block.id !in state.value.doneBlockIds }.coerceAtLeast(0)
        return startSessionFrom(first)
    }

    /** Cancella i progressi della seduta in corso; si riparte poi con "Avvia seduta" o dal singolo blocco. */
    suspend fun restartSession() {
        val workoutId = state.value.workoutId ?: return
        if (controller.state.value?.workoutId == workoutId) controller.stop()
        workouts.delete(workoutId)
    }

    private suspend fun startSessionFrom(first: Int): Long? {
        val blocks = state.value.blocks.map { it.block }
        if (blocks.isEmpty()) return null
        val workoutId = workouts.startWorkout(sessionId)
        val start = workouts.startBlock(workoutId, blocks[first].id)
        controller.start(
            start.toActiveRun(workoutId).copy(
                queue = blocks.drop(first + 1).map { QueuedBlock(it.id, it.name) },
                blockNumber = first + 1,
                blockCount = blocks.size
            )
        )
        return workoutId
    }

    fun closeOther() {
        val o = state.value.otherInProgress ?: return
        viewModelScope.launch { workouts.finish(o.id) }
    }

    fun duplicateBlock(id: Long) {
        viewModelScope.launch { programs.duplicateBlock(id) }
    }

    fun deleteBlock(id: Long) {
        viewModelScope.launch { programs.deleteBlock(id) }
    }

    fun reorderBlocks(orderedIds: List<Long>) {
        viewModelScope.launch { programs.reorderBlocks(sessionId, orderedIds) }
    }

    fun moveBlock(from: Int, to: Int) {
        viewModelScope.launch { programs.moveBlock(sessionId, from, to) }
    }
}
