package io.github.ambroal.climer.data.backup

import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val BACKUP_VERSION = 1

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val timers: List<SavedTimerEntity> = emptyList(),
    val programs: List<ProgramEntity> = emptyList(),
    val sessions: List<SessionTemplateEntity> = emptyList(),
    val blocks: List<BlockEntity> = emptyList(),
    val measureTypes: List<MeasureTypeEntity> = emptyList(),
    val measureEntries: List<MeasureEntryEntity> = emptyList(),
    val workouts: List<WorkoutLogEntity> = emptyList(),
    val blockLogs: List<BlockLogEntity> = emptyList()
)
