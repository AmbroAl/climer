package io.github.ambroal.climer.domain.load

import kotlinx.serialization.Serializable

@Serializable
data class BlockRunContext(
    val blockName: String,
    val exercise: String,
    val equipment: String,
    val sideMode: SideMode,
    val kgRight: Double?,
    val kgLeft: Double?,
    val kgSingle: Double?
)

data class SideLoad(val side: Side?, val kg: Double?)

fun BlockRunContext.loadForCycle(cycle: Int): SideLoad {
    val side = sideMode.sideForCycle(cycle)
    val kg = when (side) {
        Side.RIGHT -> kgRight
        Side.LEFT -> kgLeft
        null -> kgSingle
    }
    return SideLoad(side, kg)
}
