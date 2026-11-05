package io.github.ambroal.climer.timer

import io.github.ambroal.climer.domain.interval.IntervalConfig

fun buildPhases(config: IntervalConfig): List<Phase> {
    val phases = mutableListOf<Phase>()
    fun add(type: PhaseType, seconds: Int, round: Int, cycle: Int) {
        if (seconds > 0) phases += Phase(type, seconds * 1_000L, round, cycle)
    }
    add(PhaseType.PREPARE, config.prepareSec, 0, 0)
    for (cycle in 1..config.cycles) {
        for (round in 1..config.rounds) {
            add(PhaseType.WORK, config.workSec, round, cycle)
            // Il riposo è tra i round: dopo l'ultimo lavoro di un ciclo lo sostituiscono la pausa tra i cicli o il break.
            if (round < config.rounds) add(PhaseType.REST, config.restSec, round, cycle)
        }
        if (cycle < config.cycles) add(PhaseType.CYCLE_REST, config.restBetweenCyclesSec, 0, cycle)
    }
    add(PhaseType.COOLDOWN, config.cooldownSec, 0, 0)
    return phases
}

/** Calcolata con la formula, senza costruire le fasi: resta istantanea anche con valori enormi. */
fun IntervalConfig.totalDurationMs(): Long {
    val perCycle = rounds.toLong() * workSec + (rounds - 1L).coerceAtLeast(0) * restSec
    val seconds = prepareSec + cycles * perCycle + (cycles - 1L).coerceAtLeast(0) * restBetweenCyclesSec + cooldownSec
    return seconds * 1_000
}
