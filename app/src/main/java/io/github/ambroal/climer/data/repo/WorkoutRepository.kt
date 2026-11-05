package io.github.ambroal.climer.data.repo

import androidx.room.withTransaction
import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutStatus
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.domain.load.cyclesPerSide
import io.github.ambroal.climer.domain.time.WallClock
import io.github.ambroal.climer.timer.ActiveRun
import io.github.ambroal.climer.timer.TimerRunState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class BlockStart(val blockLogIds: List<Long>, val config: IntervalConfig, val context: BlockRunContext, val blockId: Long)

class WorkoutRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: WallClock
) {
    private val dao = db.workoutDao()
    private val programs = db.programDao()
    private val measures = db.measureDao()

    suspend fun startWorkout(sessionId: Long): Long = db.withTransaction {
        dao.findInProgressForSession(sessionId)?.let { return@withTransaction it.id }
        val session = requireNotNull(programs.getSession(sessionId)) { "Seduta $sessionId inesistente" }
        val program = programs.getProgram(session.programId)
        dao.insertWorkout(
            WorkoutLogEntity(
                sourceSessionId = sessionId,
                programName = program?.name.orEmpty(),
                sessionName = session.name,
                startedAt = clock.nowMs()
            )
        )
    }

    /** Congela kg e configurazione: lo storico registra il previsto di quel giorno anche se poi cambiano programma o massimali. */
    suspend fun startBlock(workoutId: Long, blockId: Long): BlockStart = db.withTransaction {
        val block = requireNotNull(programs.getBlock(blockId)) { "Blocco $blockId inesistente" }
        dao.deleteAbandoned(workoutId, block.id)
        val load = block.load
        val max = if (load is LoadSpec.PercentOfMax) measures.latestMax(load.measureTypeId).first() else null
        val loads = computeLoads(block.sideMode, load, max, settings.settings.first().roundingStepKg)
        val config = block.config
        // "Entrambe" con pesi diversi per mano: una riga per mano, entrambe con tutte le ripetute.
        val bothSplit = block.sideMode == SideMode.BOTH && loads.kgSingle == null && load != LoadSpec.None
        val perSide = if (bothSplit) {
            mapOf(
                Side.RIGHT to config.cycles,
                Side.LEFT to config.cycles
            )
        } else {
            block.sideMode.cyclesPerSide(config.cycles)
        }
        val basePosition = dao.getBlockLogs(workoutId).size
        fun row(index: Int, side: Side?, kg: Double?, cycles: Int) = BlockLogEntity(
            workoutId = workoutId,
            sourceBlockId = block.id,
            position = basePosition + index,
            blockName = block.name,
            exercise = block.exercise,
            equipment = block.equipment,
            side = side,
            plannedKg = kg,
            plannedReps = config.rounds * cycles
        )
        val rows = if (perSide.isEmpty()) {
            listOf(row(0, null, loads.kgSingle, config.cycles))
        } else {
            perSide.entries.mapIndexed { i, (side, cycles) ->
                row(i, side, if (side == Side.RIGHT) loads.kgRight else loads.kgLeft, cycles)
            }
        }
        val ids = dao.insertBlockLogs(rows)
        val context = BlockRunContext(
            blockName = block.name,
            exercise = block.exercise,
            equipment = block.equipment,
            sideMode = block.sideMode,
            kgRight = loads.kgRight,
            kgLeft = loads.kgLeft,
            kgSingle = loads.kgSingle
        )
        BlockStart(ids, config, context, block.id)
    }

    /** Toglie le righe di un blocco aperto e mai avviato; se la seduta registrata resta vuota, sparisce anche lei. */
    suspend fun discardBlockLogs(workoutId: Long, blockLogIds: List<Long>) = db.withTransaction {
        dao.deleteBlockLogs(blockLogIds)
        if (dao.getBlockLogs(workoutId).isEmpty()) dao.deleteWorkout(workoutId)
    }

    suspend fun pruneAbandoned(workoutId: Long) = dao.deleteAbandonedOfCompleted(workoutId)

    /** Dopo una modifica del timer: le ripetute previste seguono round e cicli nuovi. */
    suspend fun updatePlannedReps(blockLogIds: List<Long>, config: IntervalConfig, sideMode: SideMode) = db.withTransaction {
        val perSide = sideMode.cyclesPerSide(config.cycles)
        val rows = dao.getBlockLogsByIds(blockLogIds)
        dao.updateBlockLogs(rows.map { it.copy(plannedReps = config.rounds * (it.side?.let { s -> perSide[s] } ?: config.cycles)) })
    }

    suspend fun markCompleted(blockLogIds: List<Long>) {
        if (blockLogIds.isNotEmpty()) dao.markCompleted(blockLogIds)
    }

    suspend fun saveNote(workoutId: Long, note: String) {
        dao.getWorkout(workoutId)?.let { dao.updateWorkout(it.copy(note = note)) }
    }

    suspend fun finish(workoutId: Long) {
        val workout = dao.getWorkout(workoutId) ?: return
        dao.updateWorkout(workout.copy(status = WorkoutStatus.DONE, endedAt = workout.endedAt ?: clock.nowMs()))
    }

    fun inProgress(): Flow<WorkoutLogEntity?> = dao.observeInProgress()

    fun inProgressForSession(sessionId: Long): Flow<WorkoutLogEntity?> = dao.observeInProgressForSession(sessionId)

    fun history(): Flow<List<WorkoutLogEntity>> = dao.observeHistory()

    fun observeWorkout(id: Long): Flow<WorkoutLogEntity?> = dao.observeWorkout(id)

    fun observeBlockLogs(workoutId: Long): Flow<List<BlockLogEntity>> = dao.observeBlockLogs(workoutId)

    suspend fun getBlockLogs(workoutId: Long): List<BlockLogEntity> = dao.getBlockLogs(workoutId)

    suspend fun getWorkout(id: Long): WorkoutLogEntity? = dao.getWorkout(id)

    suspend fun delete(id: Long) = dao.deleteWorkout(id)
}

fun BlockStart.toActiveRun(workoutId: Long) = ActiveRun(
    title = context.blockName,
    config = config,
    runState = TimerRunState(),
    workoutId = workoutId,
    blockLogIds = blockLogIds,
    context = context,
    blockId = blockId
)
