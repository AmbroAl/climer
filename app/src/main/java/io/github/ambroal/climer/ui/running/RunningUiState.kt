package io.github.ambroal.climer.ui.running

import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.Phase
import io.github.ambroal.climer.timer.PhaseType
import io.github.ambroal.climer.timer.RunningUi

/**
 * Fascia mano/peso: [change] = siamo nel riposo tra i cicli e si prepara la mano successiva.
 * [rightKg]/[leftKg] valgono solo per "Entrambe" con pesi diversi per mano.
 */
data class Banner(val change: Boolean, val side: Side?, val kg: Double?, val rightKg: Double? = null, val leftKg: Double? = null)

data class RunningUiState(
    val active: Boolean = false,
    val title: String = "",
    val phaseType: PhaseType? = null,
    val phaseRemaining: String = "",
    val totalRemaining: String = "",
    val nextType: PhaseType? = null,
    val roundsLeft: Int = 0,
    val cyclesLeft: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val banner: Banner? = null,
    val exercise: String = "",
    val equipment: String = "",
    val phases: List<Phase> = emptyList(),
    val currentIndex: Int = 0,
    val savedTimerId: Long? = null,
    val workoutId: Long? = null,
    /** "2/4" quando si sta eseguendo una seduta intera. */
    val blockLabel: String? = null,
    val nextBlockName: String? = null,
    val config: IntervalConfig = IntervalConfig(),
    val settings: AppSettings = AppSettings()
)

fun toRunningUiState(ui: RunningUi?, settings: AppSettings): RunningUiState {
    if (ui == null) return RunningUiState(settings = settings)
    val snap = ui.snapshot
    val phase = snap.phase
    val rounds = ui.phases.maxOfOrNull { it.round } ?: 0
    val cycles = ui.phases.maxOfOrNull { it.cycle } ?: 0
    val (roundsLeft, cyclesLeft) = when (phase?.type) {
        null -> 0 to 0
        PhaseType.PREPARE -> rounds to cycles
        PhaseType.WORK -> rounds - phase.round + 1 to cycles - phase.cycle + 1
        PhaseType.REST -> rounds - phase.round to cycles - phase.cycle + 1
        PhaseType.CYCLE_REST -> rounds to cycles - phase.cycle
        PhaseType.COOLDOWN -> 0 to 0
    }
    val banner = when {
        ui.context == null -> null
        ui.context.sideMode == SideMode.BOTH && ui.context.kgSingle == null ->
            Banner(change = false, side = null, kg = null, rightKg = ui.context.kgRight, leftKg = ui.context.kgLeft)
        phase?.type == PhaseType.CYCLE_REST -> ui.next?.let { Banner(change = true, side = it.side, kg = it.kg) }
        else -> ui.current?.let { Banner(change = false, side = it.side, kg = it.kg) }
    }
    return RunningUiState(
        active = true,
        title = ui.title,
        phaseType = phase?.type,
        phaseRemaining = formatClock(snap.phaseRemainingMs),
        totalRemaining = formatClock(snap.totalRemainingMs),
        nextType = ui.phases.getOrNull(snap.phaseIndex + 1)?.type,
        roundsLeft = roundsLeft,
        cyclesLeft = cyclesLeft,
        isRunning = snap.isRunning,
        isFinished = snap.isFinished,
        banner = banner,
        exercise = ui.context?.exercise.orEmpty(),
        equipment = ui.context?.equipment.orEmpty(),
        phases = ui.phases,
        currentIndex = snap.phaseIndex,
        savedTimerId = ui.savedTimerId,
        workoutId = ui.workoutId,
        blockLabel = if (ui.blockCount > 1) "${ui.blockNumber}/${ui.blockCount}" else null,
        nextBlockName = ui.nextBlockName,
        config = ui.config,
        settings = settings
    )
}
