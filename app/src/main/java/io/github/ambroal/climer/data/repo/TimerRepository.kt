package io.github.ambroal.climer.data.repo

import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.domain.time.WallClock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class TimerRepository @Inject constructor(db: AppDatabase, private val clock: WallClock) {
    private val dao = db.savedTimerDao()

    fun observeAll(): Flow<List<SavedTimerEntity>> = dao.observeAll()

    suspend fun get(id: Long): SavedTimerEntity? = dao.get(id)

    suspend fun save(timer: SavedTimerEntity): Long {
        val toSave = if (timer.id == 0L) timer.copy(createdAt = clock.nowMs()) else timer
        val id = dao.upsert(toSave)
        return if (timer.id == 0L) id else timer.id
    }

    suspend fun duplicate(id: Long): Long? {
        val source = dao.get(id) ?: return null
        return save(source.copy(id = 0, name = "${source.name} (copia)"))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
