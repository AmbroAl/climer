package io.github.ambroal.climer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.data.settings.CueTone
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.di.ApplicationScope
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.format.stopwatchSpeech
import io.github.ambroal.climer.domain.load.Side
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val FOCUS_RELEASE_DELAY_MS = 2_500L

// Contenuto multimediale: segue il volume multimediale e suona anche con il telefono in silenzioso.
private val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

fun toneRes(tone: CueTone): Int = when (tone) {
    CueTone.BEEP -> R.raw.cue_beep
    CueTone.PIP -> R.raw.cue_pip
    CueTone.CHIME -> R.raw.cue_chime
    CueTone.ALARM -> R.raw.cue_alarm
}

@Singleton
class CuePlayer @Inject constructor(
    private val context: Context,
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope
) : CueSink {
    @Volatile
    private var settings = AppSettings()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val sounds = CueSoundBank(
        context,
        MEDIA_ATTRIBUTES,
        CueTone.entries.map(::toneRes) + listOf(R.raw.cue_short, R.raw.cue_tick, R.raw.cue_finish)
    )
    private var tts: TextToSpeech? = null
    private val speech = SpeechGate { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue") }
    private var focusRequest: AudioFocusRequest? = null
    private val releaseFocus = Runnable { abandonFocus() }

    init {
        scope.launch { settingsRepository.settings.collect { settings = it } }
        // Creato subito, non al primo annuncio: l'inizializzazione richiede qualche istante.
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.setAudioAttributes(MEDIA_ATTRIBUTES)
                speech.onReady()
            }
        }
    }

    override fun onRunningChanged(running: Boolean) = sounds.keepAwake(running)

    override fun play(cues: List<Cue>, ui: RunningUi) {
        val s = settings
        val output = cueOutputFor(s)
        if (ui.mode == RunMode.STOPWATCH) {
            execute(planStopwatchActions(cues, ui.elapsedMs, s, output), s)
            return
        }
        // Primo segnale di ogni blocco di una seduta avviata per intero: si annuncia il blocco invece di "Prepara".
        val blockStart = ui.context?.blockName?.takeIf { ui.blockCount > 1 && cues.any { it is Cue.PhaseStarted && it.index == 0 } }
        execute(planCueActions(cues, ui.context, ui.next, s, output, blockStart), s)
    }

    /** Anteprima del tono scelto nelle impostazioni. */
    fun preview(tone: CueTone) {
        val s = settings.copy(workTone = tone)
        execute(listOf(CueAction.Tone(ToneKind.WORK)), s)
    }

    private fun execute(actions: List<CueAction>, s: AppSettings) {
        if (actions.isEmpty()) return
        if (s.duckMusic && actions.any { it !is CueAction.Vibrate }) requestFocus()
        for (action in actions) {
            when (action) {
                is CueAction.Tone -> playTone(action.kind, s)
                is CueAction.Speak -> speak(textFor(action.speech))
                is CueAction.Vibrate -> vibrate(action.ms)
            }
        }
    }

    private fun playTone(kind: ToneKind, s: AppSettings) {
        val id = when (kind) {
            ToneKind.SHORT -> R.raw.cue_short
            ToneKind.TICK -> R.raw.cue_tick
            ToneKind.FINISH -> R.raw.cue_finish
            ToneKind.WORK -> toneRes(s.workTone)
            ToneKind.REST -> toneRes(s.restTone)
        }
        val volume = s.volume.coerceIn(0f, 1f)
        sounds.play(id, volume)
    }

    private fun speak(text: String) = speech.speak(text)

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun requestFocus() {
        handler.removeCallbacks(releaseFocus)
        if (focusRequest == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audio.requestAudioFocus(request)
            focusRequest = request
        }
        handler.postDelayed(releaseFocus, FOCUS_RELEASE_DELAY_MS)
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun elapsedText(ms: Long): String {
        val (m, s) = stopwatchSpeech(ms)
        val res = context.resources
        val minutes = res.getQuantityString(R.plurals.speech_minutes, m.toInt(), m)
        val seconds = res.getQuantityString(R.plurals.speech_seconds, s.toInt(), s)
        return when {
            m == 0L -> seconds
            s == 0L -> minutes
            else -> context.getString(R.string.speech_minutes_and_seconds, minutes, seconds)
        }
    }

    private fun textFor(speech: Speech): String = when (speech) {
        is Speech.PhaseName -> context.getString(
            if (speech.type ==
                PhaseType.COOLDOWN
            ) {
                R.string.phase_cooldown_spoken
            } else {
                phaseNameRes(speech.type)
            }
        )
        is Speech.Change -> {
            val side = when (speech.side) {
                Side.RIGHT -> context.getString(R.string.side_right_spoken)
                Side.LEFT -> context.getString(R.string.side_left_spoken)
                null -> ""
            }
            val kg = speech.kg?.let { context.getString(R.string.kg_spoken, formatKg(it)) }.orEmpty()
            context.getString(R.string.speech_change, side, kg).trim()
        }
        is Speech.BlockStart -> context.getString(R.string.speech_block_start, speech.name)
        is Speech.Elapsed -> elapsedText(speech.ms)
        Speech.TenSeconds -> context.getString(R.string.speech_ten_seconds)
        Speech.Halfway -> context.getString(R.string.speech_halfway)
        Speech.Finished -> context.getString(R.string.speech_finished)
    }
}

fun phaseNameRes(type: PhaseType): Int = when (type) {
    PhaseType.PREPARE -> R.string.phase_prepare
    PhaseType.WORK -> R.string.phase_work
    PhaseType.REST -> R.string.phase_rest
    PhaseType.CYCLE_REST -> R.string.phase_cycle_rest
    PhaseType.COOLDOWN -> R.string.phase_cooldown
}
