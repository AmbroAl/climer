package io.github.ambroal.climer.data.repo

import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.data.db.dao.MeasureDao
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.domain.load.MaxValue
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class MeasureTypeWithLatest(val type: MeasureTypeEntity, val latest: MeasureEntryEntity?)

/** Ultimo valore registrato per ciascuna mano (e per il valore unico), anche se in misure diverse. */
fun MeasureDao.latestMax(typeId: Long): Flow<MaxValue?> = combine(latestValue(typeId), latestRight(typeId), latestLeft(typeId)) { v, r, l ->
    if (v == null && r == null && l == null) null else MaxValue(single = v, right = r, left = l)
}

class MeasureRepository @Inject constructor(db: AppDatabase) {
    private val dao = db.measureDao()

    fun observeTypes(): Flow<List<MeasureTypeEntity>> = dao.observeTypes()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTypesWithLatest(): Flow<List<MeasureTypeWithLatest>> = dao.observeTypes().flatMapLatest { types ->
        if (types.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(types.map { t -> dao.latestEntry(t.id).map { MeasureTypeWithLatest(t, it) } }) { it.toList() }
        }
    }

    fun observeType(id: Long): Flow<MeasureTypeEntity?> = dao.observeType(id)

    suspend fun getType(id: Long): MeasureTypeEntity? = dao.getType(id)

    suspend fun saveType(type: MeasureTypeEntity): Long {
        val id = dao.upsertType(type)
        return if (type.id == 0L) id else type.id
    }

    suspend fun deleteType(id: Long) = dao.deleteType(id)

    suspend fun hasEntries(typeId: Long): Boolean = dao.countEntries(typeId) > 0

    fun observeEntries(typeId: Long): Flow<List<MeasureEntryEntity>> = dao.observeEntries(typeId)

    fun latestMax(typeId: Long): Flow<MaxValue?> = dao.latestMax(typeId)

    suspend fun saveEntry(entry: MeasureEntryEntity): Long {
        val id = dao.upsertEntry(entry)
        return if (entry.id == 0L) id else entry.id
    }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)
}
