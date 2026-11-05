package io.github.ambroal.climer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ambroal.climer.data.backup.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "measure_types")
data class MeasureTypeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val unit: String, val perSide: Boolean)

@Serializable
@Entity(
    tableName = "measure_entries",
    foreignKeys = [ForeignKey(MeasureTypeEntity::class, ["id"], ["typeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("typeId")]
)
data class MeasureEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Long,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val value: Double? = null,
    val right: Double? = null,
    val left: Double? = null,
    val note: String = ""
)
