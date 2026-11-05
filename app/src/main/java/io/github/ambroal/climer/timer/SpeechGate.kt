package io.github.ambroal.climer.timer

/**
 * TextToSpeech si inizializza in modo asincrono: finché non è pronto si tiene l'ultima frase
 * (le precedenti sarebbero ormai superate) e la si pronuncia appena possibile.
 */
class SpeechGate(private val speakNow: (String) -> Unit) {
    private var ready = false
    private var pending: String? = null

    @Synchronized
    fun speak(text: String) {
        if (ready) speakNow(text) else pending = text
    }

    @Synchronized
    fun onReady() {
        ready = true
        pending?.let(speakNow)
        pending = null
    }
}
