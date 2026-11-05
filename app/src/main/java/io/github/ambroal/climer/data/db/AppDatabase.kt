package io.github.ambroal.climer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.ambroal.climer.data.db.dao.MeasureDao
import io.github.ambroal.climer.data.db.dao.ProgramDao
import io.github.ambroal.climer.data.db.dao.SavedTimerDao
import io.github.ambroal.climer.data.db.dao.WorkoutDao
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity

@Database(
    entities = [
        SavedTimerEntity::class,
        ProgramEntity::class,
        SessionTemplateEntity::class,
        BlockEntity::class,
        MeasureTypeEntity::class,
        MeasureEntryEntity::class,
        WorkoutLogEntity::class,
        BlockLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedTimerDao(): SavedTimerDao

    abstract fun programDao(): ProgramDao

    abstract fun measureDao(): MeasureDao

    abstract fun workoutDao(): WorkoutDao
}
