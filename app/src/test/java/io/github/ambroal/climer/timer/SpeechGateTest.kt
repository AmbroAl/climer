package io.github.ambroal.climer.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechGateTest {
    private val spoken = mutableListOf<String>()
    private val gate = SpeechGate { spoken += it }

    @Test
    fun `la frase detta prima che la voce sia pronta non va persa`() {
        gate.speak("Cambia, sinistra, 16,5 chili")
        assertEquals(emptyList<String>(), spoken)
        gate.onReady()
        assertEquals(listOf("Cambia, sinistra, 16,5 chili"), spoken)
    }

    @Test
    fun `in attesa resta solo l ultima frase`() {
        gate.speak("Prepara")
        gate.speak("Lavora")
        gate.onReady()
        assertEquals(listOf("Lavora"), spoken)
    }

    @Test
    fun `quando pronta parla subito`() {
        gate.onReady()
        gate.speak("Fine")
        assertEquals(listOf("Fine"), spoken)
    }
}
