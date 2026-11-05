package io.github.ambroal.climer.data.settings

import io.github.ambroal.climer.timer.PhaseType
import kotlinx.serialization.Serializable

/** Suoni in res/raw, generati da tools/gen_sounds.js. */
@Serializable
enum class CueTone { BEEP, PIP, CHIME, ALARM }

val DefaultPhaseColors: Map<PhaseType, Long> = mapOf(
    PhaseType.PREPARE to 0xFFF2F23A,
    PhaseType.WORK to 0xFF4CD964,
    PhaseType.REST to 0xFFFF3B30,
    PhaseType.CYCLE_REST to 0xFFFF9500,
    PhaseType.COOLDOWN to 0xFF34AADC
)

@Serializable
data class AppSettings(
    val volume: Float = 1f,
    val workTone: CueTone = CueTone.BEEP,
    val restTone: CueTone = CueTone.PIP,
    val voice: Boolean = true,
    val countdown321: Boolean = true,
    val tenSecondsLeft: Boolean = true,
    val halfway: Boolean = false,
    val intervalName: Boolean = true,
    val ticking: Boolean = false,
    val intervalSound: Boolean = true,
    val vibration: Boolean = false,
    val duckMusic: Boolean = true,
    val keepScreenOn: Boolean = true,
    val allowRotation: Boolean = true,
    val openLastTimer: Boolean = false,
    val softColors: Boolean = false,
    val screenFlash: Boolean = false,
    val phaseColors: Map<PhaseType, Long> = DefaultPhaseColors,
    val roundingStepKg: Double = 0.5,
    // Cronometro: avviso ogni N secondi (si può spegnere tenendo l'intervallo) e cosa fare a ogni avviso.
    val stopwatchAlertOn: Boolean = true,
    val stopwatchAlertSec: Int = 60,
    val stopwatchSound: Boolean = true,
    val stopwatchVoice: Boolean = false,
    val stopwatchVibrate: Boolean = false,
    val stopwatchColor: Boolean = true,
    val lastTimerId: Long? = null
)

/** Intervallo degli avvisi del cronometro da usare davvero: 0 se l'avviso è spento. */
val AppSettings.stopwatchAlertEverySec: Int get() = if (stopwatchAlertOn) stopwatchAlertSec.coerceAtLeast(0) else 0
