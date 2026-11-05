package io.github.ambroal.climer.ui.measures

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import io.github.ambroal.climer.domain.format.formatKg

/** "59 / 55 kg" per i tipi per mano, "59 kg" altrimenti. */
@Composable
fun entryValueText(entry: MeasureEntryEntity, unit: String): String {
    val dash = stringResource(R.string.no_value)
    fun v(x: Double?) = x?.let(::formatKg) ?: dash
    val number = if (entry.value != null) {
        v(entry.value)
    } else {
        stringResource(R.string.right_left_values, v(entry.right), v(entry.left))
    }
    return "$number $unit".trim()
}
