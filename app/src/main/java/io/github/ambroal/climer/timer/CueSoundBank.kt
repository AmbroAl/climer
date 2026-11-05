package io.github.ambroal.climer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FALLBACK_SAMPLE_RATE = 48_000

/** Estrae i campioni PCM 16 bit dal chunk "data" di un WAV. */
fun wavPcm(bytes: ByteArray): ShortArray {
    val (pos, size) = wavChunk(bytes, "data") ?: return ShortArray(0)
    val samples = ShortArray(size / 2)
    ByteBuffer.wrap(bytes, pos, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
    return samples
}

/** Frequenza di campionamento dichiarata nel chunk "fmt " di un WAV. */
fun wavSampleRate(bytes: ByteArray): Int? {
    val (pos, size) = wavChunk(bytes, "fmt ") ?: return null
    if (size < 8) return null
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(pos + 4)
}

/** Inizio e lunghezza dei dati del chunk [id], se c'è. */
private fun wavChunk(bytes: ByteArray, id: String): Pair<Int, Int>? {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val size = buf.getInt(pos + 4)
        if (String(bytes, pos, 4, Charsets.US_ASCII) == id) return pos + 8 to size
        pos += 8 + size + (size and 1)
    }
    return null
}

/** Ricampionamento lineare: basta per toni semplici e lascia invariata la durata. */
fun resamplePcm(pcm: ShortArray, from: Int, to: Int): ShortArray {
    if (from == to || pcm.isEmpty()) return pcm
    val n = (pcm.size.toLong() * to / from).toInt()
    return ShortArray(n) { i ->
        val pos = i.toDouble() * from / to
        val idx = pos.toInt().coerceAtMost(pcm.lastIndex)
        val next = pcm[(idx + 1).coerceAtMost(pcm.lastIndex)]
        val frac = pos - idx
        (pcm[idx] + (next - pcm[idx]) * frac).toInt().toShort()
    }
}

/**
 * Suoni dei segnali con AudioTrack statiche a bassa latenza, più un flusso silenzioso che tiene sveglia
 * l'uscita audio mentre il timer corre: alcuni telefoni la mettono in standby dopo 1 s di silenzio
 * e il primo suono dopo il risveglio esce in ritardo, in modo irregolare.
 * Tutto alla frequenza nativa del telefono: su alcuni (es. Honor) la conversione nel percorso
 * a bassa latenza fa gracchiare l'audio.
 */
class CueSoundBank(context: Context, private val attributes: AudioAttributes, rawResources: List<Int>) {
    private val sampleRate = context.getSystemService(AudioManager::class.java)
        ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        ?.takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE

    private val tracks: Map<Int, AudioTrack> = rawResources.associateWith { res ->
        val bytes = context.resources.openRawResource(res).use { it.readBytes() }
        val pcm = resamplePcm(wavPcm(bytes), wavSampleRate(bytes) ?: sampleRate, sampleRate)
        staticTrack(pcm).apply { write(pcm, 0, pcm.size) }
    }

    // Silenzio in loop sia sull'uscita a bassa latenza (bip) sia su quella normale (voce).
    private val keepAlive = listOf(true, false).map { lowLatency ->
        val silence = ShortArray(sampleRate / 10)
        staticTrack(silence, lowLatency).apply {
            write(silence, 0, silence.size)
            setLoopPoints(0, silence.size, -1)
        }
    }

    private fun staticTrack(pcm: ShortArray, lowLatency: Boolean = true): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(maxOf(pcm.size * 2, 2))
        .setPerformanceMode(if (lowLatency) AudioTrack.PERFORMANCE_MODE_LOW_LATENCY else AudioTrack.PERFORMANCE_MODE_NONE)
        .build()

    /** [rawRes] è l'id della risorsa in res/raw; restituisce la traccia avviata. */
    fun play(rawRes: Int, volume: Float): AudioTrack? {
        val track = tracks[rawRes] ?: return null
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.setVolume(volume)
            track.play()
        }
        return track
    }

    fun keepAwake(active: Boolean) {
        keepAlive.forEach { track ->
            runCatching {
                if (active && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.reloadStaticData()
                    track.play()
                } else if (!active && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
            }
        }
    }
}
