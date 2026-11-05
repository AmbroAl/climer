package io.github.ambroal.climer.ui.settings

import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.ProgramEntity
import io.github.ambroal.climer.data.repo.FakeSettingsRepository
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.settings.DefaultPhaseColors
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.PhaseType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val vm by lazy { SettingsViewModel(settings) { } }

    @Test
    fun `cambio passo ricalcola i pesi dei blocchi`() = runTest {
        val programs = ProgramRepository(db, settings)
        val type = db.measureDao().upsertType(MeasureTypeEntity(name = "Dinamometro", unit = "kg", perSide = true))
        db.measureDao().upsertEntry(MeasureEntryEntity(typeId = type, date = LocalDate.of(2026, 10, 9), right = 59.0, left = 55.0))
        val session = programs.addSession(programs.saveProgram(ProgramEntity(name = "P")), "S")
        programs.saveBlock(
            BlockEntity(
                sessionId = session,
                position = 0,
                name = "Aerobico",
                sideMode = SideMode.RIGHT,
                load = LoadSpec.PercentOfMax(type, 35.0),
                config = IntervalConfig()
            )
        )
        assertEquals(20.5, programs.blocksWithLoads(session).first().single().kgRight)
        vm.setRoundingStep(1.25)
        assertEquals(21.25, programs.blocksWithLoads(session).first().single().kgRight)
    }

    @Test
    fun `ripristina colori torna ai default`() = runTest {
        vm.setPhaseColor(PhaseType.WORK, 0xFFAF52DE)
        assertEquals(0xFFAF52DE, settings.state.value.phaseColors[PhaseType.WORK])
        vm.resetPhaseColors()
        assertEquals(DefaultPhaseColors, settings.state.value.phaseColors)
    }

    @Test
    fun `passo non valido ignorato`() = runTest {
        vm.setRoundingStep(0.0)
        assertEquals(0.5, settings.state.value.roundingStepKg, 0.0)
    }
}
