package io.github.ambroal.climer.ui.measures

import androidx.lifecycle.SavedStateHandle
import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.repo.MeasureRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MeasureDetailViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo by lazy { MeasureRepository(db) }

    private suspend fun vm(perSide: Boolean): MeasureDetailViewModel {
        val id = repo.saveType(MeasureTypeEntity(name = "Dinamometro 20 mm", unit = "kg", perSide = perSide))
        val vm = MeasureDetailViewModel(SavedStateHandle(mapOf("typeId" to id)), repo)
        vm.state.first { it.type != null }
        return vm
    }

    @Test
    fun `inserimento con virgola salvato come numero`() = runTest {
        val vm = vm(perSide = true)
        vm.openNewEntry(LocalDate.of(2026, 10, 9))
        vm.onEntryRight("59,5")
        vm.onEntryLeft("55")
        assertTrue(vm.state.value.entryForm?.canSave == true)
        vm.saveEntry()
        val entry = vm.state.first { it.entries.isNotEmpty() }.entries.single()
        assertEquals(59.5, entry.right)
        assertEquals(55.0, entry.left)
    }

    @Test
    fun `testo non numerico da errore di campo`() = runTest {
        val vm = vm(perSide = false)
        vm.openNewEntry(LocalDate.of(2026, 10, 9))
        vm.onEntryValue("abc")
        val form = vm.state.value.entryForm
        assertNotNull(form)
        assertTrue(form?.valueError == true)
        assertFalse(form?.canSave == true)
    }

    @Test
    fun `per mano serve almeno un lato`() = runTest {
        val vm = vm(perSide = true)
        vm.openNewEntry(LocalDate.of(2026, 10, 9))
        assertFalse(vm.state.value.entryForm?.canSave == true)
        vm.onEntryLeft("55")
        assertTrue(vm.state.value.entryForm?.canSave == true)
    }
}
