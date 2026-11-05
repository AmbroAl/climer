package io.github.ambroal.climer.data.backup

import androidx.room.withTransaction
import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.domain.time.WallClock
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface ImportResult {
    data class Success(val workouts: Int, val programs: Int) : ImportResult

    data object Invalid : ImportResult
}

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

class BackupRepository @Inject constructor(private val db: AppDatabase, private val clock: WallClock) {
    suspend fun export(out: OutputStream) {
        val data = BackupData(
            exportedAt = clock.nowMs(),
            timers = db.savedTimerDao().all(),
            programs = db.programDao().allPrograms(),
            sessions = db.programDao().allSessions(),
            blocks = db.programDao().allBlocks(),
            measureTypes = db.measureDao().allTypes(),
            measureEntries = db.measureDao().allEntries(),
            workouts = db.workoutDao().allWorkouts(),
            blockLogs = db.workoutDao().allBlockLogs()
        )
        withContext(Dispatchers.IO) {
            out.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    /** Sostituisce tutti i dati in un'unica transazione; se il file non è valido non tocca nulla. */
    suspend fun import(input: InputStream): ImportResult {
        val data = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(BackupData.serializer(), input.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
        }
        if (data == null || data.version != BACKUP_VERSION) return ImportResult.Invalid
        return runCatching {
            db.withTransaction {
                // Le FK in cascata svuotano sedute, blocchi, misure e righe di log.
                db.savedTimerDao().deleteAll()
                db.programDao().deleteAllPrograms()
                db.measureDao().deleteAllTypes()
                db.workoutDao().deleteAllWorkouts()
                db.savedTimerDao().insertAll(data.timers)
                db.programDao().insertPrograms(data.programs)
                db.programDao().insertSessions(data.sessions)
                db.programDao().insertBlocks(data.blocks)
                db.measureDao().insertTypes(data.measureTypes)
                db.measureDao().insertEntries(data.measureEntries)
                db.workoutDao().insertWorkouts(data.workouts)
                db.workoutDao().insertBlockLogs(data.blockLogs)
            }
            ImportResult.Success(data.workouts.size, data.programs.size)
        }.getOrDefault(ImportResult.Invalid)
    }
}
