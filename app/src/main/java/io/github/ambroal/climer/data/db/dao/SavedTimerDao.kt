package io.github.ambroal.climer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTimerDao {
    @Query("SELECT * FROM saved_timers ORDER BY createdAt")
    fun observeAll(): Flow<List<SavedTimerEntity>>

    @Query("SELECT * FROM saved_timers WHERE id = :id")
    suspend fun get(id: Long): SavedTimerEntity?

    @Upsert
    suspend fun upsert(timer: SavedTimerEntity): Long

    @Query("DELETE FROM saved_timers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM saved_timers")
    suspend fun all(): List<SavedTimerEntity>

    @Query("DELETE FROM saved_timers")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(timers: List<SavedTimerEntity>)
}
