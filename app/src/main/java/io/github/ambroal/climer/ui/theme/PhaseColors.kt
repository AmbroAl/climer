package io.github.ambroal.climer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.data.settings.DefaultPhaseColors
import io.github.ambroal.climer.timer.PhaseType

/** Palette dei colori selezionabili per timer e fasi, come in Timer Plus. */
val Palette: List<Long> = listOf(0xFFFF3B30, 0xFFFF9500, 0xFFF2F23A, 0xFF4CD964, 0xFF34AADC, 0xFFAF52DE)

val FinishedColor = Color(0xFF9CFF3D)

fun phaseColor(type: PhaseType, settings: AppSettings): Color {
    val argb = settings.phaseColors[type] ?: DefaultPhaseColors.getValue(type)
    val base = Color(argb)
    return if (settings.softColors) lerp(base, Color(0xFF3A3A3A), 0.35f) else base
}

/** Testo nero sui colori chiari (giallo, verde), bianco sugli scuri. */
fun contentColorFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}
