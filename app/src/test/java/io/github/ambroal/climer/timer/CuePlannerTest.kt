package io.github.ambroal.climer.timer

import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideLoad
import io.github.ambroal.climer.domain.load.SideMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuePlannerTest {
    private val all = CueOutput(sound = true, voice = true, vibrate = true)
    private val ctx = BlockRunContext("Round", "No-hang", "Travetto", SideMode.ALTERNATE_RIGHT_FIRST, 17.5, 16.5, null)
    private val cycleRest = Phase(PhaseType.CYCLE_REST, 10_000, 0, 1)

    @Test
    fun `nel riposo tra i cicli annuncia il cambio mano e peso`() {
        val actions = planCueActions(listOf(Cue.PhaseStarted(60, cycleRest)), ctx, SideLoad(Side.LEFT, 16.5), AppSettings(), all)
        assertTrue(CueAction.Speak(Speech.Change(Side.LEFT, 16.5)) in actions)
    }

    @Test
    fun `inizio lavoro e inizio riposo hanno suoni diversi`() {
        val work = planCueActions(listOf(Cue.PhaseStarted(1, Phase(PhaseType.WORK, 7_000, 1, 1))), null, null, AppSettings(), all)
        val rest = planCueActions(listOf(Cue.PhaseStarted(2, Phase(PhaseType.REST, 3_000, 1, 1))), null, null, AppSettings(), all)
        assertTrue(CueAction.Tone(ToneKind.WORK) in work)
        assertTrue(CueAction.Tone(ToneKind.REST) in rest)
        assertTrue(CueAction.Tone(ToneKind.REST) in planCueActions(listOf(Cue.PhaseStarted(60, cycleRest)), ctx, null, AppSettings(), all))
    }

    @Test
    fun `all inizio di un blocco della seduta annuncia il blocco`() {
        val prepare = Phase(PhaseType.PREPARE, 10_000, 0, 0)
        val actions = planCueActions(listOf(Cue.PhaseStarted(0, prepare)), ctx, null, AppSettings(), all, blockStart = "Ripetute 7:3")
        assertTrue(CueAction.Speak(Speech.BlockStart("Ripetute 7:3")) in actions)
        assertTrue(CueAction.Speak(Speech.PhaseName(PhaseType.PREPARE)) !in actions)
    }

    @Test
    fun `senza voce niente parlato`() {
        val actions =
            planCueActions(listOf(Cue.PhaseStarted(60, cycleRest)), ctx, SideLoad(Side.LEFT, 16.5), AppSettings(), all.copy(voice = false))
        assertTrue(actions.none { it is CueAction.Speak })
    }

    @Test
    fun `3-2-1 disattivato non suona`() {
        val actions = planCueActions(listOf(Cue.Countdown(3)), null, null, AppSettings(countdown321 = false), all)
        assertEquals(emptyList<CueAction>(), actions)
    }

    @Test
    fun `ticchettio solo se attivo`() {
        assertEquals(emptyList<CueAction>(), planCueActions(listOf(Cue.Tick), null, null, AppSettings(ticking = false), all))
        assertEquals(listOf(CueAction.Tone(ToneKind.TICK)), planCueActions(listOf(Cue.Tick), null, null, AppSettings(ticking = true), all))
    }

    @Test
    fun `cronometro segue le opzioni scelte`() {
        val s = AppSettings(stopwatchSound = true, stopwatchVoice = true, stopwatchVibrate = false)
        assertEquals(
            listOf(CueAction.Tone(ToneKind.WORK), CueAction.Speak(Speech.Elapsed(120_000))),
            planStopwatchActions(listOf(Cue.Interval(2)), 120_000, s, all)
        )
        assertEquals(
            listOf(CueAction.Vibrate(400)),
            planStopwatchActions(listOf(Cue.Interval(1)), 60_000, AppSettings(stopwatchSound = false, stopwatchVibrate = true), all)
        )
    }
}
