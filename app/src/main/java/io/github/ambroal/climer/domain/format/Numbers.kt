package io.github.ambroal.climer.domain.format

import java.math.BigDecimal

private val DECIMAL = Regex("""\d+(\.\d+)?""")

/** Accetta virgola o punto come separatore decimale; rifiuta negativi e testo. */
fun parseDecimalOrNull(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    return if (DECIMAL.matches(normalized)) normalized.toDouble() else null
}

fun formatKg(kg: Double): String = BigDecimal.valueOf(kg).stripTrailingZeros().toPlainString().replace('.', ',')

/** Arrotonda per eccesso: a 9,001 s residui il display mostra ancora 00:10. */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatDate(date: java.time.LocalDate): String = date.format(DATE_FORMAT)

/** Cronometro: per difetto, con i decimi dopo il punto ("01:05.4"); le ore compaiono solo se servono. */
fun formatStopwatch(ms: Long): String {
    val tenths = ms.coerceAtLeast(0) / 100
    val totalSeconds = tenths / 10
    val h = totalSeconds / 3_600
    val m = (totalSeconds % 3_600) / 60
    val s = totalSeconds % 60
    val d = tenths % 10
    return if (h > 0) "%d:%02d:%02d.%d".format(h, m, s, d) else "%02d:%02d.%d".format(m, s, d)
}

data class StopwatchSpeech(val minutes: Long, val seconds: Long)

/** Tempo trascorso da annunciare a voce, in minuti e secondi interi. */
fun stopwatchSpeech(ms: Long): StopwatchSpeech {
    val total = ms.coerceAtLeast(0) / 1_000
    return StopwatchSpeech(total / 60, total % 60)
}
