package io.github.ambroal.climer.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.ambroal.climer.domain.interval.IntervalConfig
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_timers")
data class SavedTimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    @Embedded(prefix = "cfg_") val config: IntervalConfig,
    val createdAt: Long
)
