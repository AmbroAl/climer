package io.github.ambroal.climer.ui.programs

import androidx.lifecycle.SavedStateHandle
import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.MeasureRepository
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.interval.NewIntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockEditorViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val programs by lazy { ProgramRepository(db, settings) }
    private val measures by lazy { MeasureRepository(db) }

    private suspend fun vm(): Pair<BlockEditorViewModel, Long> {
        val session = programs.addSession(programs.saveProgram(ProgramEntity(name = "P")), "S")
        val vm = BlockEditorViewModel(
            SavedStateHandle(mapOf("sessionId" to session, "blockId" to 0L)),
            programs,
            measures,
            TimerRepository(db) { 0L },
            settings
        )
        vm.state.first { it.loaded }
        return vm to session
    }

    private suspend fun typeWithMax(): Long {
        val type = measures.saveType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        measures.saveEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        return type
    }

    @Test
    fun `un nuovo blocco ha solo il lavoro`() = runTest {
        val (vm, _) = vm()
        assertEquals(NewIntervalConfig, vm.state.value.form.config)
    }

    @Test
    fun `30 percento del massimale 59 55`() = runTest {
        val type = typeWithMax()
        val (vm, _) = vm()
        vm.onSideMode(SideMode.ALTERNATE_RIGHT_FIRST)
        vm.onLoadKind(LoadKind.PERCENT)
        vm.onMeasureType(type)
        vm.onPercent("30")
        val s = vm.state.first { it.preview?.kgRight != null }
        assertEquals(17.5, s.preview?.kgRight)
        assertEquals(16.5, s.preview?.kgLeft)
        assertFalse(s.preview?.missingMax == true)
    }

    @Test
    fun `tipo senza misure segnala massimale mancante`() = runTest {
        val type = measures.saveType(MeasureTypeEntity(name = "Vuoto", unit = "kg", perSide = true))
        val (vm, _) = vm()
        vm.onSideMode(SideMode.RIGHT)
        vm.onLoadKind(LoadKind.PERCENT)
        vm.onMeasureType(type)
        vm.onPercent("30")
        assertTrue(vm.state.first { it.preview != null }.preview?.missingMax == true)
    }

    @Test
    fun `percentuale non numerica non salvabile`() = runTest {
        val type = typeWithMax()
        val (vm, _) = vm()
        vm.onName("Round 7:3")
        vm.onLoadKind(LoadKind.PERCENT)
        vm.onMeasureType(type)
        vm.onPercent("abc")
        assertFalse(vm.state.value.canSave)
        vm.onPercent("30,5")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `salvataggio del blocco`() = runTest {
        val type = typeWithMax()
        val (vm, session) = vm()
        val config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
        vm.onName("Round 7:3")
        vm.onExercise("No-hang")
        vm.onSideMode(SideMode.ALTERNATE_RIGHT_FIRST)
        vm.onLoadKind(LoadKind.PERCENT)
        vm.onMeasureType(type)
        vm.onPercent("30")
        vm.onConfig(config)
        vm.save()
        vm.state.first { it.saved }
        val block = db.programDao().getBlocks(session).single()
        assertEquals(LoadSpec.PercentOfMax(type, 30.0), block.load)
        assertEquals(config, block.config)
        assertEquals("No-hang", block.exercise)
    }
}
