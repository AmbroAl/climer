package io.github.ambroal.climer.data.repo

import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgramRepositoryTest : DbTestBase() {
    private val settings = FakeSettingsRepository()
    private val repo by lazy { ProgramRepository(db, settings) }

    private suspend fun block(sessionId: Long, typeId: Long, name: String = "Round 7:3") = repo.saveBlock(
        BlockEntity(
            sessionId = sessionId,
            position = 0,
            name = name,
            sideMode = SideMode.ALTERNATE_RIGHT_FIRST,
            load = LoadSpec.PercentOfMax(typeId, 30.0),
            config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
        )
    )

    @Test
    fun `blocksWithLoads segnala massimale mancante`() = runTest {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        val program = repo.saveProgram(ProgramEntity(name = "P"))
        val session = repo.addSession(program, "S")
        block(session, type)
        val row = repo.blocksWithLoads(session).first().single()
        assertTrue(row.missingMax)
        assertNull(row.kgRight)
        assertEquals(1_842_000L, row.totalDurationMs)
    }

    @Test
    fun `blocksWithLoads calcola i kg per mano`() = runTest {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        val session = repo.addSession(repo.saveProgram(ProgramEntity(name = "P")), "S")
        block(session, type)
        val row = repo.blocksWithLoads(session).first().single()
        assertFalse(row.missingMax)
        assertEquals(17.5, row.kgRight)
        assertEquals(16.5, row.kgLeft)
    }

    @Test
    fun `ultima misura con una sola mano non cancella l altra`() = runTest {
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 11, 13), right = 61.0))
        val session = repo.addSession(repo.saveProgram(ProgramEntity(name = "P")), "S")
        block(session, type)
        val row = repo.blocksWithLoads(session).first().single()
        assertEquals(18.5, row.kgRight)
        assertEquals(16.5, row.kgLeft)
        assertFalse(row.missingMax)
    }

    @Test
    fun `riordino trascinando salva il nuovo ordine`() = runTest {
        val program = repo.saveProgram(ProgramEntity(name = "P"))
        val session = repo.addSession(program, "S")
        val a = block(session, 0, "A")
        val b = block(session, 0, "B")
        val c = block(session, 0, "C")
        repo.reorderBlocks(session, listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), db.programDao().getBlocks(session).map { it.name })
        val s2 = repo.addSession(program, "S2")
        repo.reorderSessions(program, listOf(s2, session))
        assertEquals(listOf("S2", "S"), repo.observeSessions(program).first().map { it.name })
    }

    @Test
    fun `nuovi blocchi in coda e riordino`() = runTest {
        val session = repo.addSession(repo.saveProgram(ProgramEntity(name = "P")), "S")
        block(session, 0, "A")
        block(session, 0, "B")
        val c = block(session, 0, "C")
        repo.moveBlock(session, from = 2, to = 0)
        assertEquals(listOf("C", "A", "B"), db.programDao().getBlocks(session).map { it.name })
        repo.duplicateBlock(c)
        assertEquals(listOf("C", "A", "B", "C (copia)"), db.programDao().getBlocks(session).map { it.name })
    }
}
