package io.github.ambroal.climer.data.db

import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutStatus
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest : DbTestBase() {
    private val config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)

    private suspend fun seedProgram(): Triple<Long, Long, Long> {
        val programId = db.programDao().upsertProgram(ProgramEntity(name = "No-hang resistenza"))
        val sessionId = db.programDao().upsertSession(SessionTemplateEntity(programId = programId, position = 0, name = "Venerdì aerobico"))
        val blockId = db.programDao().upsertBlock(
            BlockEntity(
                sessionId = sessionId,
                position = 0,
                name = "Round 7:3",
                sideMode = SideMode.ALTERNATE_RIGHT_FIRST,
                load = LoadSpec.PercentOfMax(1, 30.0),
                config = config
            )
        )
        return Triple(programId, sessionId, blockId)
    }

    private suspend fun logWorkout(blockId: Long, status: WorkoutStatus, startedAt: Long, kg: Double): Long {
        val w = db.workoutDao().insertWorkout(
            WorkoutLogEntity(
                sourceSessionId = null,
                programName = "No-hang resistenza",
                sessionName = "Venerdì aerobico",
                startedAt = startedAt,
                status = status
            )
        )
        db.workoutDao().insertBlockLogs(
            listOf(
                BlockLogEntity(
                    workoutId = w,
                    sourceBlockId = blockId,
                    position = 0,
                    blockName = "Round 7:3",
                    side = Side.RIGHT,
                    plannedKg = kg,
                    plannedReps = 90,
                    completed = true
                ),
                BlockLogEntity(
                    workoutId = w,
                    sourceBlockId = blockId,
                    position = 1,
                    blockName = "Round 7:3",
                    side = Side.LEFT,
                    plannedKg =
                    kg - 1,
                    plannedReps = 90,
                    completed = true
                )
            )
        )
        return w
    }

    @Test
    fun `eliminare un programma elimina sedute e blocchi ma non i log`() = runTest {
        val (programId, sessionId, blockId) = seedProgram()
        val w = logWorkout(blockId, WorkoutStatus.DONE, 1_000, 17.5)
        db.programDao().deleteProgram(programId)
        assertTrue(db.programDao().observeSessions(programId).first().isEmpty())
        assertTrue(db.programDao().getBlocks(sessionId).isEmpty())
        val logs = db.workoutDao().getBlockLogs(w)
        assertEquals(listOf("Round 7:3", "Round 7:3"), logs.map { it.blockName })
        assertEquals("Venerdì aerobico", db.workoutDao().getWorkout(w)?.sessionName)
    }

    @Test
    fun `latestEntry sceglie la data piu recente`() = runTest {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro 20 mm", unit = "kg", perSide = true))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 11, 13), right = 61.0, left = 57.0))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        val latest = db.measureDao().latestEntry(type).first()
        assertEquals(61.0, latest?.right)
        assertEquals(57.0, latest?.left)
    }

    @Test
    fun `eliminare un tipo di massimale elimina le sue misure`() = runTest {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = false))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), value = 59.0))
        db.measureDao().deleteType(type)
        assertNull(db.measureDao().latestEntry(type).first())
    }

    @Test
    fun `lastCompletedFor ignora workout in corso`() = runTest {
        val (_, _, blockId) = seedProgram()
        logWorkout(blockId, WorkoutStatus.DONE, 1_000, 17.5)
        logWorkout(blockId, WorkoutStatus.IN_PROGRESS, 2_000, 18.5)
        val last = db.workoutDao().lastCompletedFor(blockId).first()
        assertEquals(listOf(17.5, 16.5), last.map { it.plannedKg })
    }

    @Test
    fun `lastCompletedFor prende il workout piu recente`() = runTest {
        val (_, _, blockId) = seedProgram()
        logWorkout(blockId, WorkoutStatus.DONE, 1_000, 17.5)
        logWorkout(blockId, WorkoutStatus.DONE, 3_000, 18.5)
        assertEquals(listOf(18.5, 17.5), db.workoutDao().lastCompletedFor(blockId).first().map { it.plannedKg })
    }

    @Test
    fun `round trip IntervalConfig e LoadSpec PercentOfMax`() = runTest {
        val (_, _, blockId) = seedProgram()
        val block = db.programDao().getBlock(blockId)
        assertEquals(config, block?.config)
        assertEquals(LoadSpec.PercentOfMax(1, 30.0), block?.load)
        assertEquals(SideMode.ALTERNATE_RIGHT_FIRST, block?.sideMode)
    }
}
