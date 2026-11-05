package io.github.ambroal.climer.domain.load

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SideModeTest {
    @Test
    fun `alternato dispari`() {
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT), (1..3).map { SideMode.ALTERNATE_RIGHT_FIRST.sideForCycle(it) })
        assertEquals(mapOf(Side.RIGHT to 2, Side.LEFT to 1), SideMode.ALTERNATE_RIGHT_FIRST.cyclesPerSide(3))
        assertEquals(Side.LEFT, SideMode.ALTERNATE_LEFT_FIRST.sideForCycle(1))
    }

    @Test
    fun `lato fisso o nessuno`() {
        assertEquals(Side.LEFT, SideMode.LEFT.sideForCycle(4))
        assertNull(SideMode.NONE.sideForCycle(1))
        assertNull(SideMode.BOTH.sideForCycle(1))
        assertEquals(mapOf(Side.RIGHT to 6), SideMode.RIGHT.cyclesPerSide(6))
        assertEquals(emptyMap<Side, Int>(), SideMode.BOTH.cyclesPerSide(6))
    }
}
