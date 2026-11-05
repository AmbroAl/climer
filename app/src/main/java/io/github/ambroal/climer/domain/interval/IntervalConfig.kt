package io.github.ambroal.climer.domain.interval

import kotlinx.serialization.Serializable

@Serializable
data class IntervalConfig(
    val prepareSec: Int = 10,
    val workSec: Int = 7,
    val restSec: Int = 3,
    val rounds: Int = 1,
    val cycles: Int = 1,
    val restBetweenCyclesSec: Int = 0,
    val cooldownSec: Int = 0
)

/** Limiti dell'editor: oltre si costruirebbero centinaia di migliaia di fasi in memoria. */
const val MAX_ROUNDS = 999
const val MAX_CYCLES = 99

/** Configurazione di un nuovo timer o blocco: solo il lavoro, le altre fasi si aggiungono a mano. */
val NewIntervalConfig = IntervalConfig(prepareSec = 0, workSec = 30, restSec = 0)
