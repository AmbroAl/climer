package io.github.ambroal.climer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ambroal.climer.domain.load.Side
import kotlinx.serialization.Serializable

enum class WorkoutStatus { IN_PROGRESS, DONE }

/** Copia dei nomi, senza FK verso i template: lo storico non cambia se il programma viene modificato o eliminato. */
@Serializable
@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSessionId: Long?,
    val programName: String,
    val sessionName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
    val note: String = ""
)

@Serializable
@Entity(
    tableName = "block_logs",
    foreignKeys = [ForeignKey(WorkoutLogEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId"), Index("sourceBlockId")]
)
data class BlockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val sourceBlockId: Long?,
    val position: Int,
    val blockName: String,
    val exercise: String = "",
    val equipment: String = "",
    val side: Side? = null,
    val plannedKg: Double? = null,
    val plannedReps: Int,
    val completed: Boolean = false,
    val repsDone: Int? = null,
    val actualKg: Double? = null,
    val pump: Int? = null,
    val note: String = ""
)
