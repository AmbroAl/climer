package io.github.ambroal.climer.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null
)

@Serializable
@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programId")]
)
data class SessionTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val position: Int,
    val name: String,
    val notes: String = ""
)

@Serializable
@Entity(
    tableName = "blocks",
    foreignKeys = [ForeignKey(SessionTemplateEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val name: String,
    val exercise: String = "",
    val equipment: String = "",
    val notes: String = "",
    val sideMode: SideMode = SideMode.NONE,
    val load: LoadSpec = LoadSpec.None,
    @Embedded(prefix = "cfg_") val config: IntervalConfig
)
