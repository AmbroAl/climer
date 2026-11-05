package io.github.ambroal.climer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: WorkoutLogEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutLogEntity)

    @Query("SELECT * FROM workout_logs WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutLogEntity?

    @Query("SELECT * FROM workout_logs WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutLogEntity?>

    @Query("SELECT * FROM workout_logs WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<WorkoutLogEntity?>

    @Query("SELECT * FROM workout_logs WHERE status = 'IN_PROGRESS' AND sourceSessionId = :sessionId ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgressForSession(sessionId: Long): Flow<WorkoutLogEntity?>

    @Query("SELECT * FROM workout_logs WHERE status = 'IN_PROGRESS' AND sourceSessionId = :sessionId ORDER BY startedAt DESC LIMIT 1")
    suspend fun findInProgressForSession(sessionId: Long): WorkoutLogEntity?

    @Query("SELECT * FROM workout_logs ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<WorkoutLogEntity>>

    @Query("DELETE FROM workout_logs WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert
    suspend fun insertBlockLogs(logs: List<BlockLogEntity>): List<Long>

    @Update
    suspend fun updateBlockLogs(logs: List<BlockLogEntity>)

    @Query("SELECT * FROM block_logs WHERE workoutId = :workoutId ORDER BY position, id")
    fun observeBlockLogs(workoutId: Long): Flow<List<BlockLogEntity>>

    @Query("SELECT * FROM block_logs WHERE id IN (:ids)")
    suspend fun getBlockLogsByIds(ids: List<Long>): List<BlockLogEntity>

    @Query("SELECT * FROM block_logs WHERE workoutId = :workoutId ORDER BY position, id")
    suspend fun getBlockLogs(workoutId: Long): List<BlockLogEntity>

    /** Tentativi di [blockId] mai completati e senza risultati: un nuovo avvio li sostituisce. */
    @Query(
        "DELETE FROM block_logs WHERE workoutId = :workoutId AND sourceBlockId = :blockId AND completed = 0 " +
            "AND repsDone IS NULL AND actualKg IS NULL AND pump IS NULL AND note = ''"
    )
    suspend fun deleteAbandoned(workoutId: Long, blockId: Long)

    /** Tentativi abbandonati di blocchi che nella stessa seduta sono poi stati completati. */
    @Query(
        "DELETE FROM block_logs WHERE workoutId = :workoutId AND completed = 0 " +
            "AND repsDone IS NULL AND actualKg IS NULL AND pump IS NULL AND note = '' " +
            "AND sourceBlockId IN (SELECT sourceBlockId FROM block_logs WHERE workoutId = :workoutId AND completed = 1)"
    )
    suspend fun deleteAbandonedOfCompleted(workoutId: Long)

    @Query("DELETE FROM block_logs WHERE id IN (:ids)")
    suspend fun deleteBlockLogs(ids: List<Long>)

    @Query("UPDATE block_logs SET completed = 1 WHERE id IN (:ids)")
    suspend fun markCompleted(ids: List<Long>)

    /** Righe di quel blocco nell'ultima seduta conclusa che lo contiene. */
    @Query(
        """
        SELECT * FROM block_logs WHERE sourceBlockId = :blockId AND workoutId = (
            SELECT b.workoutId FROM block_logs b JOIN workout_logs w ON w.id = b.workoutId
            WHERE b.sourceBlockId = :blockId AND w.status = 'DONE'
            ORDER BY w.startedAt DESC LIMIT 1
        ) ORDER BY position, id
        """
    )
    fun lastCompletedFor(blockId: Long): Flow<List<BlockLogEntity>>

    @Query("SELECT * FROM workout_logs")
    suspend fun allWorkouts(): List<WorkoutLogEntity>

    @Query("SELECT * FROM block_logs")
    suspend fun allBlockLogs(): List<BlockLogEntity>

    @Query("DELETE FROM workout_logs")
    suspend fun deleteAllWorkouts()

    @Insert
    suspend fun insertWorkouts(workouts: List<WorkoutLogEntity>)
}
