package io.github.ambroal.climer.data.repo

import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
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
class WorkoutRepositoryTest : DbTestBase() {
    private val settings = FakeSettingsRepository()
    private val repo by lazy { WorkoutRepository(db, settings) { 1_000L } }

    private suspend fun seed(sideMode: SideMode = SideMode.ALTERNATE_RIGHT_FIRST): Pair<Long, Long> {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro 20 mm", unit = "kg", perSide = true))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        val program = db.programDao().upsertProgram(ProgramEntity(name = "No-hang resistenza"))
        val session = db.programDao().upsertSession(SessionTemplateEntity(programId = program, position = 0, name = "Venerdì aerobico"))
        val block = db.programDao().upsertBlock(
            BlockEntity(
                sessionId = session,
                position = 0,
                name = "Round 7:3",
                exercise = "No-hang",
                equipment = "Travetto 20 mm",
                sideMode = sideMode,
                load = LoadSpec.PercentOfMax(type, 30.0),
                config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
            )
        )
        return session to block
    }

    @Test
    fun `startBlock alternato congela kg e ripetute per mano`() = runTest {
        val (session, block) = seed()
        val workout = repo.startWorkout(session)
        val start = repo.startBlock(workout, block)
        val rows = db.workoutDao().getBlockLogs(workout)
        assertEquals(listOf(Side.RIGHT, Side.LEFT), rows.map { it.side })
        assertEquals(listOf(17.5, 16.5), rows.map { it.plannedKg })
        assertEquals(listOf(90, 90), rows.map { it.plannedReps })
        assertEquals(rows.map { it.id }, start.blockLogIds)
        assertEquals(17.5, start.context.kgRight)
        assertEquals(16.5, start.context.kgLeft)
        assertEquals("Travetto 20 mm", start.context.equipment)
    }

    @Test
    fun `cambiare il massimale dopo startBlock non cambia il previsto`() = runTest {
        val (session, block) = seed()
        val workout = repo.startWorkout(session)
        repo.startBlock(workout, block)
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = 1, date = LocalDate.of(2026, 11, 13), right = 61.0, left = 57.0))
        assertEquals(listOf(17.5, 16.5), db.workoutDao().getBlockLogs(workout).map { it.plannedKg })
    }

    @Test
    fun `startBlock usa l ultimo valore per ciascuna mano`() = runTest {
        val (session, block) = seed()
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = 1, date = LocalDate.of(2026, 11, 13), right = 61.0))
        val start = repo.startBlock(repo.startWorkout(session), block)
        assertEquals(18.5, start.context.kgRight)
        assertEquals(16.5, start.context.kgLeft)
    }

    @Test
    fun `startWorkout riusa la seduta in corso`() = runTest {
        val (session, _) = seed()
        val first = repo.startWorkout(session)
        assertEquals(first, repo.startWorkout(session))
        assertEquals("Venerdì aerobico", db.workoutDao().getWorkout(first)?.sessionName)
        assertEquals("No-hang resistenza", db.workoutDao().getWorkout(first)?.programName)
    }

    @Test
    fun `senza mano una sola riga con tutte le ripetute`() = runTest {
        val (session, block) = seed(SideMode.NONE)
        val workout = repo.startWorkout(session)
        repo.startBlock(workout, block)
        val rows = db.workoutDao().getBlockLogs(workout)
        assertEquals(1, rows.size)
        assertNull(rows[0].side)
        assertEquals(180, rows[0].plannedReps)
    }

    @Test
    fun `entrambe le mani con massimale per mano registra una riga per mano`() = runTest {
        val (session, block) = seed(SideMode.BOTH)
        val workout = repo.startWorkout(session)
        val start = repo.startBlock(workout, block)
        val rows = db.workoutDao().getBlockLogs(workout)
        assertEquals(listOf(Side.RIGHT, Side.LEFT), rows.map { it.side })
        assertEquals(listOf(17.5, 16.5), rows.map { it.plannedKg })
        assertEquals(listOf(180, 180), rows.map { it.plannedReps })
        assertEquals(rows.map { it.id }, start.blockLogIds)
    }

    @Test
    fun `riavviare un blocco non completato non duplica le righe`() = runTest {
        val (session, block) = seed()
        val workout = repo.startWorkout(session)
        repo.startBlock(workout, block)
        repo.startBlock(workout, block)
        val second = repo.startBlock(workout, block)
        val rows = db.workoutDao().getBlockLogs(workout)
        assertEquals(2, rows.size)
        assertEquals(second.blockLogIds, rows.map { it.id })
    }

    @Test
    fun `un blocco completato e poi ripetuto conserva entrambe le volte`() = runTest {
        val (session, block) = seed()
        val workout = repo.startWorkout(session)
        repo.markCompleted(repo.startBlock(workout, block).blockLogIds)
        repo.startBlock(workout, block)
        assertEquals(4, db.workoutDao().getBlockLogs(workout).size)
    }

    @Test
    fun `tentativi abbandonati tolti se il blocco e stato completato`() = runTest {
        val (session, block) = seed()
        val workout = repo.startWorkout(session)
        val abandoned = repo.startBlock(workout, block).blockLogIds
        db.workoutDao().insertBlockLogs(
            db.workoutDao().getBlockLogs(workout).map { it.copy(id = 0, completed = true, position = it.position + 10) }
        )
        repo.pruneAbandoned(workout)
        val rows = db.workoutDao().getBlockLogs(workout)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.completed })
        assertTrue(rows.none { it.id in abandoned })
    }

    @Test
    fun `finish chiude la seduta`() = runTest {
        val (session, _) = seed()
        val workout = repo.startWorkout(session)
        repo.finish(workout)
        assertEquals(WorkoutStatus.DONE, db.workoutDao().getWorkout(workout)?.status)
        assertNull(repo.inProgress().first())
    }
}
