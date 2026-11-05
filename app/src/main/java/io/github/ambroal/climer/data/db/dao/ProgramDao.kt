package io.github.ambroal.climer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY name COLLATE NOCASE")
    fun observePrograms(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observeProgram(id: Long): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getProgram(id: Long): ProgramEntity?

    @Upsert
    suspend fun upsertProgram(program: ProgramEntity): Long

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("SELECT * FROM sessions WHERE programId = :programId ORDER BY position, id")
    fun observeSessions(programId: Long): Flow<List<SessionTemplateEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionTemplateEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionTemplateEntity?

    @Upsert
    suspend fun upsertSession(session: SessionTemplateEntity): Long

    @Update
    suspend fun updateSessions(sessions: List<SessionTemplateEntity>)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM blocks WHERE sessionId = :sessionId ORDER BY position, id")
    fun observeBlocks(sessionId: Long): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE sessionId = :sessionId ORDER BY position, id")
    suspend fun getBlocks(sessionId: Long): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getBlock(id: Long): BlockEntity?

    @Upsert
    suspend fun upsertBlock(block: BlockEntity): Long

    @Update
    suspend fun updateBlocks(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long)

    @Query("SELECT * FROM programs")
    suspend fun allPrograms(): List<ProgramEntity>

    @Query("SELECT * FROM sessions")
    suspend fun allSessions(): List<SessionTemplateEntity>

    @Query("SELECT * FROM blocks")
    suspend fun allBlocks(): List<BlockEntity>

    @Query("DELETE FROM programs")
    suspend fun deleteAllPrograms()

    @Insert
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    @Insert
    suspend fun insertSessions(sessions: List<SessionTemplateEntity>)

    @Insert
    suspend fun insertBlocks(blocks: List<BlockEntity>)
}
