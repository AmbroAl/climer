package io.github.ambroal.climer.domain.load

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadCalculatorTest {
    private val max = MaxValue(null, 59.0, 55.0)

    @Test
    fun `tabella carichi del programma`() = listOf(
        30 to (17.5 to 16.5),
        35 to (20.5 to 19.0),
        50 to (29.5 to 27.5),
        55 to (32.5 to 30.0),
        60 to (35.5 to 33.0),
        65 to (38.5 to 36.0),
        80 to (47.0 to 44.0),
        85 to (50.0 to 47.0),
        90 to (53.0 to 49.5)
    ).forEach { (pct, exp) ->
        val spec = LoadSpec.PercentOfMax(1, pct.toDouble())
        assertEquals("$pct% dx", exp.first, kgFor(spec, Side.RIGHT, max, 0.5))
        assertEquals("$pct% sx", exp.second, kgFor(spec, Side.LEFT, max, 0.5))
    }

    @Test
    fun `massimale mancante da null`() {
        assertNull(kgFor(LoadSpec.PercentOfMax(1, 30.0), Side.LEFT, MaxValue(null, 59.0, null), 0.5))
        assertNull(kgFor(LoadSpec.PercentOfMax(1, 30.0), Side.RIGHT, null, 0.5))
    }

    @Test
    fun `massimale unico vale per entrambe le mani`() =
        assertEquals(30.0, kgFor(LoadSpec.PercentOfMax(1, 50.0), Side.LEFT, MaxValue(60.0, null, null), 0.5))

    @Test
    fun `senza mano usa il valore unico`() {
        assertEquals(30.0, kgFor(LoadSpec.PercentOfMax(1, 50.0), null, MaxValue(60.0, null, null), 0.5))
        assertNull(kgFor(LoadSpec.PercentOfMax(1, 50.0), null, max, 0.5))
    }

    @Test
    fun `kg fissi non arrotondati`() {
        assertEquals(17.3, kgFor(LoadSpec.Fixed(17.3), Side.RIGHT, null, 0.5))
        assertEquals(16.5, kgFor(LoadSpec.FixedPerSide(17.5, 16.5), Side.LEFT, null, 0.5))
        assertNull(kgFor(LoadSpec.FixedPerSide(17.5, null), Side.LEFT, null, 0.5))
        assertNull(kgFor(LoadSpec.None, Side.LEFT, max, 0.5))
    }

    @Test
    fun `passo 1,25`() = assertEquals(17.5, roundToStep(17.7, 1.25), 0.0)

    @Test
    fun `contesto alternato per ciclo`() {
        val ctx = BlockRunContext("Round", "No-hang", "Travetto", SideMode.ALTERNATE_RIGHT_FIRST, 17.5, 16.5, null)
        assertEquals(SideLoad(Side.RIGHT, 17.5), ctx.loadForCycle(1))
        assertEquals(SideLoad(Side.LEFT, 16.5), ctx.loadForCycle(2))
        val both = ctx.copy(sideMode = SideMode.NONE, kgSingle = 20.0)
        assertEquals(SideLoad(null, 20.0), both.loadForCycle(1))
    }
}
