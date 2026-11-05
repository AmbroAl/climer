package io.github.ambroal.climer.timer

enum class PhaseType { PREPARE, WORK, REST, CYCLE_REST, COOLDOWN }

/** [round] e [cycle] sono 1-based; valgono 0 per PREPARE e COOLDOWN. CYCLE_REST porta il ciclo appena finito. */
data class Phase(val type: PhaseType, val durationMs: Long, val round: Int, val cycle: Int)
