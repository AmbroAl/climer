package io.github.ambroal.climer.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {
    @Test
    fun `parse decimali`() {
        assertEquals(17.5, parseDecimalOrNull("17,5"))
        assertEquals(17.5, parseDecimalOrNull("17.5"))
        assertEquals(17.0, parseDecimalOrNull(" 17 "))
        listOf("", "abc", "1,2,3", "-3", "1.2.3", ",").forEach { assertNull(it, parseDecimalOrNull(it)) }
    }

    @Test
    fun `formato kg`() {
        assertEquals("17,5", formatKg(17.5))
        assertEquals("47", formatKg(47.0))
        assertEquals("1,25", formatKg(1.25))
    }

    @Test
    fun `formato orologio`() {
        assertEquals("30:42", formatClock(1_842_000))
        assertEquals("00:10", formatClock(10_000))
        assertEquals("1:05:00", formatClock(3_900_000))
    }

    @Test
    fun `data italiana`() = assertEquals("09/10/2026", formatDate(java.time.LocalDate.of(2026, 10, 9)))

    @Test
    fun `formato cronometro con i decimi`() {
        assertEquals("00:00.0", formatStopwatch(0))
        assertEquals("01:05.4", formatStopwatch(65_480))
        assertEquals("1:02:03.9", formatStopwatch(3_723_999))
    }

    @Test
    fun `tempo detto a voce`() {
        assertEquals(StopwatchSpeech(1, 0), stopwatchSpeech(60_000))
        assertEquals(StopwatchSpeech(2, 30), stopwatchSpeech(150_000))
        assertEquals(StopwatchSpeech(0, 30), stopwatchSpeech(30_000))
    }

    @Test
    fun `orologio arrotonda per eccesso`() {
        assertEquals("00:10", formatClock(9_001))
        assertEquals("00:00", formatClock(0))
    }
}
