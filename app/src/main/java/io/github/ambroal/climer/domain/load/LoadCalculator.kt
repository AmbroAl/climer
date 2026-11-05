package io.github.ambroal.climer.domain.load

import java.math.BigDecimal
import java.math.RoundingMode

private val HUNDRED = BigDecimal(100)

/** HALF_EVEN riproduce la tabella del programma (19,25 → 19; 35,75 → 36). */
fun roundToStep(value: Double, stepKg: Double): Double = roundToStep(BigDecimal.valueOf(value), stepKg)

private fun roundToStep(value: BigDecimal, stepKg: Double): Double {
    val step = BigDecimal.valueOf(stepKg)
    return value.divide(step, 10, RoundingMode.HALF_EVEN)
        .setScale(0, RoundingMode.HALF_EVEN)
        .multiply(step)
        .toDouble()
}

fun kgFor(spec: LoadSpec, side: Side?, max: MaxValue?, stepKg: Double): Double? = when (spec) {
    LoadSpec.None -> null
    is LoadSpec.Fixed -> spec.kg
    is LoadSpec.FixedPerSide -> when (side) {
        Side.RIGHT -> spec.rightKg
        Side.LEFT -> spec.leftKg
        null -> null
    }
    is LoadSpec.PercentOfMax -> {
        val base = when (side) {
            Side.RIGHT -> max?.right ?: max?.single
            Side.LEFT -> max?.left ?: max?.single
            null -> max?.single
        }
        // In BigDecimal: 55 × 0,55 in double vale 30,250000000000004 e romperebbe l'arrotondamento.
        base?.let {
            roundToStep(BigDecimal.valueOf(it).multiply(BigDecimal.valueOf(spec.percent)).divide(HUNDRED), stepKg)
        }
    }
}
