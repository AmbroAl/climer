package io.github.ambroal.climer.ui.timers

import androidx.lifecycle.SavedStateHandle
import io.github.ambroal.climer.MainDispatcherRule
import io.github.ambroal.climer.data.db.DbTestBase
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.domain.interval.IntervalConfig
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
class TimerEditorViewModelTest : DbTestBase() {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo by lazy { TimerRepository(db) { 42L } }

    private fun vm(id: Long = 0) = TimerEditorViewModel(SavedStateHandle(mapOf("id" to id)), repo)

    @Test
    fun `un nuovo timer ha solo il lavoro`() {
        val c = vm().state.value.config
        assertEquals(listOf(0, 0, 1, 1, 0, 0), listOf(c.prepareSec, c.restSec, c.rounds, c.cycles, c.restBetweenCyclesSec, c.cooldownSec))
        assertEquals(30, c.workSec)
    }

    @Test
    fun `nome vuoto non salvabile`() {
        val vm = vm()
        vm.onName("   ")
        assertFalse(vm.state.value.canSave)
        vm.onName("DITA VOLUME")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `round sotto 1 non accettato`() {
        val vm = vm()
        vm.onConfig(IntervalConfig(rounds = 0, cycles = 0))
        assertEquals(1, vm.state.value.config.rounds)
        assertEquals(1, vm.state.value.config.cycles)
    }

    @Test
    fun `round e cicli oltre il limite vengono ridotti`() {
        val vm = vm()
        vm.onConfig(IntervalConfig(rounds = 3_030, cycles = 6_661))
        assertEquals(999, vm.state.value.config.rounds)
        assertEquals(99, vm.state.value.config.cycles)
    }

    @Test
    fun `salvataggio crea il timer con la config inserita`() = runTest {
        val vm = vm()
        val config = IntervalConfig(10, 7, 3, 30, 6, 10, 0)
        vm.onName("DITA VOLUME")
        vm.onConfig(config)
        vm.save()
        vm.state.first { it.saved }
        val saved = repo.observeAll().first().single()
        assertEquals("DITA VOLUME", saved.name)
        assertEquals(config, saved.config)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `modifica di un timer esistente`() = runTest {
        val id = repo.save(
            io.github.ambroal.climer.data.db.entity.SavedTimerEntity(
                name = "A",
                colorArgb = 0xFF00FF00,
                config = IntervalConfig(),
                createdAt = 0
            )
        )
        val vm = vm(id)
        assertEquals("A", vm.state.first { it.loaded }.name)
        vm.onName("B")
        vm.save()
        vm.state.first { it.saved }
        assertEquals(listOf("B"), repo.observeAll().first().map { it.name })
    }
}
