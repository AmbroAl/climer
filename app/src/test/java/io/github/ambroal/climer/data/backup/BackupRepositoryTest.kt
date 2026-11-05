package io.github.ambroal.climer.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ambroal.climer.data.db.AppDatabase
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest : DbTestBase() {
    private suspend fun populate(target: AppDatabase) {
        target.savedTimerDao().upsert(
            SavedTimerEntity(name = "DITA VOLUME", colorArgb = 0xFF4CD964, config = IntervalConfig(10, 7, 3, 30, 6, 10, 0), createdAt = 1)
        )
        val type = target.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        target.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        val program = target.programDao().upsertProgram(ProgramEntity(name = "No-hang", notes = "note"))
        val session = target.programDao().upsertSession(SessionTemplateEntity(programId = program, position = 0, name = "Venerdì"))
        val block = target.programDao().upsertBlock(
            BlockEntity(
                sessionId = session,
                position = 0,
                name = "Round",
                sideMode = SideMode.ALTERNATE_RIGHT_FIRST,
                load = LoadSpec.PercentOfMax(type, 30.0),
                config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
            )
        )
        val w = target.workoutDao().insertWorkout(
            WorkoutLogEntity(sourceSessionId = session, programName = "No-hang", sessionName = "Venerdì", startedAt = 5)
        )
        target.workoutDao().insertBlockLogs(
            listOf(
                BlockLogEntity(
                    workoutId = w,
                    sourceBlockId = block,
                    position = 0,
                    blockName = "Round",
                    side = Side.RIGHT,
                    plannedKg = 17.5,
                    plannedReps = 90
                )
            )
        )
    }

    private suspend fun snapshot(target: AppDatabase) = listOf(
        target.savedTimerDao().all(),
        target.measureDao().allTypes(),
        target.measureDao().allEntries(),
        target.programDao().allPrograms(),
        target.programDao().allSessions(),
        target.programDao().allBlocks(),
        target.workoutDao().allWorkouts(),
        target.workoutDao().allBlockLogs()
    )

    @Test
    fun `round trip completo`() = runTest {
        populate(db)
        val out = ByteArrayOutputStream()
        BackupRepository(db) { 99L }.export(out)

        val other = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            other.savedTimerDao().upsert(SavedTimerEntity(name = "da sostituire", colorArgb = 0, config = IntervalConfig(), createdAt = 0))
            val result = BackupRepository(other) { 0L }.import(ByteArrayInputStream(out.toByteArray()))
            assertTrue(result is ImportResult.Success)
            assertEquals(snapshot(db), snapshot(other))
        } finally {
            other.close()
        }
    }

    @Test
    fun `file troncato non tocca i dati`() = runTest {
        populate(db)
        val out = ByteArrayOutputStream()
        BackupRepository(db) { 99L }.export(out)
        val before = snapshot(db)
        val truncated = out.toByteArray().copyOf(out.size() / 2)
        val result = BackupRepository(db) { 0L }.import(ByteArrayInputStream(truncated))
        assertEquals(ImportResult.Invalid, result)
        assertEquals(before, snapshot(db))
    }

    @Test
    fun `versione sconosciuta rifiutata`() = runTest {
        val result = BackupRepository(db) { 0L }.import(ByteArrayInputStream("""{"version":99,"exportedAt":0}""".toByteArray()))
        assertEquals(ImportResult.Invalid, result)
    }
}
