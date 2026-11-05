package io.github.ambroal.climer.domain.load

enum class Side { RIGHT, LEFT }

enum class SideMode { NONE, RIGHT, LEFT, BOTH, ALTERNATE_RIGHT_FIRST, ALTERNATE_LEFT_FIRST }

/** Con l'alternanza ogni ciclo è una mano: il riposo tra i cicli serve a cambiare peso e mano. */
fun SideMode.sideForCycle(cycle: Int): Side? = when (this) {
    SideMode.NONE, SideMode.BOTH -> null
    SideMode.RIGHT -> Side.RIGHT
    SideMode.LEFT -> Side.LEFT
    SideMode.ALTERNATE_RIGHT_FIRST -> if (cycle % 2 == 1) Side.RIGHT else Side.LEFT
    SideMode.ALTERNATE_LEFT_FIRST -> if (cycle % 2 == 1) Side.LEFT else Side.RIGHT
}

fun SideMode.cyclesPerSide(cycles: Int): Map<Side, Int> = (1..cycles)
    .mapNotNull { sideForCycle(it) }
    .groupingBy { it }
    .eachCount()
