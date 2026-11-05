package io.github.ambroal.climer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasureDao {
    @Query("SELECT * FROM measure_types ORDER BY name COLLATE NOCASE")
    fun observeTypes(): Flow<List<MeasureTypeEntity>>

    @Query("SELECT * FROM measure_types WHERE id = :id")
    fun observeType(id: Long): Flow<MeasureTypeEntity?>

    @Query("SELECT * FROM measure_types WHERE id = :id")
    suspend fun getType(id: Long): MeasureTypeEntity?

    @Upsert
    suspend fun upsertType(type: MeasureTypeEntity): Long

    @Query("DELETE FROM measure_types WHERE id = :id")
    suspend fun deleteType(id: Long)

    @Query("SELECT * FROM measure_entries WHERE typeId = :typeId ORDER BY date DESC, id DESC")
    fun observeEntries(typeId: Long): Flow<List<MeasureEntryEntity>>

    @Query("SELECT * FROM measure_entries WHERE typeId = :typeId ORDER BY date DESC, id DESC LIMIT 1")
    fun latestEntry(typeId: Long): Flow<MeasureEntryEntity?>

    @Query("SELECT value FROM measure_entries WHERE typeId = :typeId AND value IS NOT NULL ORDER BY date DESC, id DESC LIMIT 1")
    fun latestValue(typeId: Long): Flow<Double?>

    @Query("SELECT `right` FROM measure_entries WHERE typeId = :typeId AND `right` IS NOT NULL ORDER BY date DESC, id DESC LIMIT 1")
    fun latestRight(typeId: Long): Flow<Double?>

    @Query("SELECT `left` FROM measure_entries WHERE typeId = :typeId AND `left` IS NOT NULL ORDER BY date DESC, id DESC LIMIT 1")
    fun latestLeft(typeId: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM measure_entries WHERE typeId = :typeId")
    suspend fun countEntries(typeId: Long): Int

    @Upsert
    suspend fun upsertEntry(entry: MeasureEntryEntity): Long

    @Query("DELETE FROM measure_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM measure_types")
    suspend fun allTypes(): List<MeasureTypeEntity>

    @Query("SELECT * FROM measure_entries")
    suspend fun allEntries(): List<MeasureEntryEntity>

    @Query("DELETE FROM measure_types")
    suspend fun deleteAllTypes()

    @Insert
    suspend fun insertTypes(types: List<MeasureTypeEntity>)

    @Insert
    suspend fun insertEntries(entries: List<MeasureEntryEntity>)
}
