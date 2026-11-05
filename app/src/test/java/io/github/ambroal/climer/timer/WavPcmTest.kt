package io.github.ambroal.climer.timer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavPcmTest {
    private fun wav(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val body = chunks.sumOf { 8 + it.second.size }
        val b = ByteBuffer.allocate(12 + body).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(4 + body).put("WAVE".toByteArray())
        chunks.forEach { (id, data) -> b.put(id.toByteArray()).putInt(data.size).put(data) }
        return b.array()
    }

    private fun pcm(vararg s: Short) = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        s.forEach { putShort(it) }
    }.array()

    @Test
    fun `legge i campioni dal chunk data saltando gli altri`() = assertArrayEquals(
        shortArrayOf(1, -2, 300),
        wavPcm(wav("fmt " to ByteArray(16), "LIST" to ByteArray(4), "data" to pcm(1, -2, 300)))
    )

    @Test
    fun `file senza dati da vuoto`() = assertArrayEquals(ShortArray(0), wavPcm(wav("fmt " to ByteArray(16))))

    private fun fmt(sampleRate: Int) = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16).array()

    @Test
    fun `legge la frequenza di campionamento dal chunk fmt`() =
        assertEquals(48_000, wavSampleRate(wav("fmt " to fmt(48_000), "data" to pcm(1))))

    @Test
    fun `senza chunk fmt la frequenza non c e`() = assertEquals(null, wavSampleRate(wav("data" to pcm(1))))

    @Test
    fun `alla stessa frequenza i campioni restano uguali`() =
        assertArrayEquals(shortArrayOf(5, -7, 9), resamplePcm(shortArrayOf(5, -7, 9), 48_000, 48_000))

    @Test
    fun `raddoppiando la frequenza interpola tra un campione e l altro`() = assertArrayEquals(
        shortArrayOf(0, 50, 100, 150, 200, 250, 300, 300),
        resamplePcm(shortArrayOf(0, 100, 200, 300), 24_000, 48_000)
    )

    @Test
    fun `la durata non cambia da 48 a 44,1 kHz`() = assertEquals(441, resamplePcm(ShortArray(480), 48_000, 44_100).size)
}
