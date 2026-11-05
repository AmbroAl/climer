package io.github.ambroal.climer.timer

import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.domain.load.SideLoad

data class CueOutput(val sound: Boolean, val voice: Boolean, val vibrate: Boolean)

/** I suoni sono contenuti multimediali: seguono il volume multimediale, non la suoneria (si sentono anche in silenzioso). */
fun cueOutputFor(settings: AppSettings): CueOutput = CueOutput(sound = true, voice = settings.voice, vibrate = settings.vibration)

enum class ToneKind { SHORT, WORK, REST, TICK, FINISH }

sealed interface Speech {
    data class PhaseName(val type: PhaseType) : Speech

    data class Change(val side: Side?, val kg: Double?) : Speech

    data class BlockStart(val name: String) : Speech

    data class Elapsed(val ms: Long) : Speech

    data object TenSeconds : Speech

    data object Halfway : Speech

    data object Finished : Speech
}

sealed interface CueAction {
    data class Tone(val kind: ToneKind) : CueAction

    data class Speak(val speech: Speech) : CueAction

    data class Vibrate(val ms: Long) : CueAction
}

fun planCueActions(
    cues: List<Cue>,
    context: BlockRunContext?,
    next: SideLoad?,
    settings: AppSettings,
    output: CueOutput,
    blockStart: String? = null
): List<CueAction> {
    val actions = mutableListOf<CueAction>()
    fun tone(kind: ToneKind) {
        if (output.sound) actions += CueAction.Tone(kind)
    }
    fun speak(speech: Speech) {
        if (output.voice) actions += CueAction.Speak(speech)
    }
    fun vibrate(ms: Long) {
        if (output.vibrate) actions += CueAction.Vibrate(ms)
    }
    for (cue in cues) {
        when (cue) {
            is Cue.PhaseStarted -> {
                if (settings.intervalSound) tone(if (cue.phase.type == PhaseType.WORK) ToneKind.WORK else ToneKind.REST)
                val change = if (cue.phase.type == PhaseType.CYCLE_REST && context != null) next else null
                when {
                    blockStart != null && cue.index == 0 -> speak(Speech.BlockStart(blockStart))
                    change != null -> speak(Speech.Change(change.side, change.kg))
                    settings.intervalName -> speak(Speech.PhaseName(cue.phase.type))
                }
                vibrate(400)
            }
            is Cue.Countdown -> if (settings.countdown321) {
                tone(ToneKind.SHORT)
                vibrate(80)
            }
            Cue.TenSecondsLeft -> if (settings.tenSecondsLeft) {
                if (output.voice) speak(Speech.TenSeconds) else tone(ToneKind.SHORT)
            }
            Cue.Halfway -> if (settings.halfway) {
                if (output.voice) speak(Speech.Halfway) else tone(ToneKind.SHORT)
            }
            Cue.Tick -> if (settings.ticking) tone(ToneKind.TICK)
            // Gli avvisi del cronometro hanno regole proprie: planStopwatchActions.
            is Cue.Interval -> Unit
            Cue.Finished -> {
                tone(ToneKind.FINISH)
                speak(Speech.Finished)
                vibrate(700)
            }
        }
    }
    return actions
}

/** Avvisi del cronometro: le opzioni sono le sue, indipendenti da quelle del timer. */
fun planStopwatchActions(cues: List<Cue>, elapsedMs: Long, settings: AppSettings, output: CueOutput): List<CueAction> {
    if (cues.none { it is Cue.Interval }) return emptyList()
    return buildList {
        if (settings.stopwatchSound && output.sound) add(CueAction.Tone(ToneKind.WORK))
        if (settings.stopwatchVoice) add(CueAction.Speak(Speech.Elapsed(elapsedMs)))
        if (settings.stopwatchVibrate) add(CueAction.Vibrate(400))
    }
}
