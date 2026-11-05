package io.github.ambroal.climer.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.ambroal.climer.timer.PhaseType
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `settings round trip`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(tmp.root, "s.preferences_pb") }
        val repo = DataStoreSettingsRepository(store)
        assertEquals(AppSettings(), repo.settings.first())
        repo.update { it.copy(roundingStepKg = 1.25, voice = false, phaseColors = it.phaseColors + (PhaseType.WORK to 0xFF00FF00)) }
        val read = repo.settings.first()
        assertEquals(1.25, read.roundingStepKg, 0.0)
        assertEquals(false, read.voice)
        assertEquals(0xFF00FF00, read.phaseColors[PhaseType.WORK])
    }
}
