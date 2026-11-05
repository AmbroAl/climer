package io.github.ambroal.climer.data.repo

import androidx.room.withTransaction
import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.MaxValue
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.domain.load.kgFor
import io.github.ambroal.climer.timer.totalDurationMs
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class BlockLoads(val kgRight: Double?, val kgLeft: Double?, val kgSingle: Double?, val missingMax: Boolean)

data class BlockWithLoad(
    val block: BlockEntity,
    val kgRight: Double?,
    val kgLeft: Double?,
    val kgSingle: Double?,
    val missingMax: Boolean,
    val totalDurationMs: Long,
    val lastTime: List<BlockLogEntity>
)

/** I kg richiesti dalle mani che il blocco usa; [BlockLoads.missingMax] se ne manca almeno uno. */
fun computeLoads(sideMode: SideMode, load: LoadSpec, max: MaxValue?, stepKg: Double): BlockLoads {
    val right = kgFor(load, Side.RIGHT, max, stepKg)
    val left = kgFor(load, Side.LEFT, max, stepKg)
    val single = kgFor(load, null, max, stepKg)
    val needed = when (sideMode) {
        SideMode.NONE -> listOf(single)
        SideMode.RIGHT -> listOf(right)
        SideMode.LEFT -> listOf(left)
        SideMode.BOTH -> if (single != null) listOf(single) else listOf(right, left)
        SideMode.ALTERNATE_RIGHT_FIRST, SideMode.ALTERNATE_LEFT_FIRST -> listOf(right, left)
    }
    val missing = load is LoadSpec.PercentOfMax && needed.any { it == null }
    return BlockLoads(right, left, single, missing)
}

class ProgramRepository @Inject constructor(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val dao = db.programDao()
    private val measures = db.measureDao()
    private val workouts = db.workoutDao()

    fun observePrograms(): Flow<List<ProgramEntity>> = dao.observePrograms()

    fun observeProgram(id: Long): Flow<ProgramEntity?> = dao.observeProgram(id)

    suspend fun getProgram(id: Long): ProgramEntity? = dao.getProgram(id)

    suspend fun saveProgram(program: ProgramEntity): Long {
        val id = dao.upsertProgram(program)
        return if (program.id == 0L) id else program.id
    }

    suspend fun deleteProgram(id: Long) = dao.deleteProgram(id)

    fun observeSessions(programId: Long): Flow<List<SessionTemplateEntity>> = dao.observeSessions(programId)

    fun observeSession(id: Long): Flow<SessionTemplateEntity?> = dao.observeSession(id)

    suspend fun addSession(programId: Long, name: String): Long = db.withTransaction {
        val position = dao.allSessions().count { it.programId == programId }
        dao.upsertSession(SessionTemplateEntity(programId = programId, position = position, name = name))
    }

    suspend fun saveSession(session: SessionTemplateEntity) {
        dao.upsertSession(session)
    }

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun moveSession(programId: Long, from: Int, to: Int) = db.withTransaction {
        val list = dao.allSessions().filter { it.programId == programId }.sortedWith(compareBy({ it.position }, { it.id }))
        dao.updateSessions(list.moved(from, to).mapIndexed { i, s -> s.copy(position = i) })
    }

    suspend fun getBlock(id: Long): BlockEntity? = dao.getBlock(id)

    suspend fun saveBlock(block: BlockEntity): Long = db.withTransaction {
        if (block.id == 0L) {
            dao.upsertBlock(block.copy(position = dao.getBlocks(block.sessionId).size))
        } else {
            dao.upsertBlock(block)
            block.id
        }
    }

    suspend fun duplicateBlock(id: Long): Long? {
        val source = dao.getBlock(id) ?: return null
        return saveBlock(source.copy(id = 0, name = "${source.name} (copia)"))
    }

    suspend fun deleteBlock(id: Long) = dao.deleteBlock(id)

    suspend fun moveBlock(sessionId: Long, from: Int, to: Int) = db.withTransaction {
        val list = dao.getBlocks(sessionId)
        dao.updateBlocks(list.moved(from, to).mapIndexed { i, b -> b.copy(position = i) })
    }

    /** Salva l'ordine ottenuto trascinando: [orderedIds] dall'alto in basso. */
    suspend fun reorderBlocks(sessionId: Long, orderedIds: List<Long>) = db.withTransaction {
        val byId = dao.getBlocks(sessionId).associateBy { it.id }
        dao.updateBlocks(orderedIds.mapNotNull { byId[it] }.mapIndexed { i, b -> b.copy(position = i) })
    }

    suspend fun reorderSessions(programId: Long, orderedIds: List<Long>) = db.withTransaction {
        val byId = dao.allSessions().filter { it.programId == programId }.associateBy { it.id }
        dao.updateSessions(orderedIds.mapNotNull { byId[it] }.mapIndexed { i, s -> s.copy(position = i) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun blocksWithLoads(sessionId: Long): Flow<List<BlockWithLoad>> = dao.observeBlocks(sessionId).flatMapLatest { blocks ->
        if (blocks.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(blocks.map { blockWithLoad(it) }) { it.toList() }
        }
    }

    private fun blockWithLoad(block: BlockEntity): Flow<BlockWithLoad> {
        val load = block.load
        val maxFlow = if (load is LoadSpec.PercentOfMax) {
            measures.latestMax(load.measureTypeId)
        } else {
            flowOf(null)
        }
        return combine(maxFlow, settings.settings, workouts.lastCompletedFor(block.id)) { max, s, last ->
            val loads = computeLoads(block.sideMode, load, max, s.roundingStepKg)
            BlockWithLoad(block, loads.kgRight, loads.kgLeft, loads.kgSingle, loads.missingMax, block.config.totalDurationMs(), last)
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
