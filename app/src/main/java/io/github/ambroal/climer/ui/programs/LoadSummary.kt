package io.github.ambroal.climer.ui.programs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.repo.BlockLoads
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.load.SideMode

/** "Dx 17,5 kg · Sx 16,5 kg", "20 kg" o null se il blocco non ha carico. */
@Composable
fun loadSummary(sideMode: SideMode, loads: BlockLoads, hasLoad: Boolean): String? {
    if (!hasLoad) return null
    val dash = stringResource(R.string.no_value)
    val resources = LocalResources.current
    fun kg(v: Double?) = v?.let { resources.getString(R.string.kg_value, formatKg(it)) } ?: dash
    val right = stringResource(R.string.right_short, kg(loads.kgRight))
    val left = stringResource(R.string.left_short, kg(loads.kgLeft))
    return when (sideMode) {
        SideMode.RIGHT -> right
        SideMode.LEFT -> left
        SideMode.NONE -> kg(loads.kgSingle)
        SideMode.BOTH -> if (loads.kgSingle != null) kg(loads.kgSingle) else "$right · $left"
        SideMode.ALTERNATE_RIGHT_FIRST -> "$right · $left"
        SideMode.ALTERNATE_LEFT_FIRST -> "$left · $right"
    }
}

@Composable
fun LoadPreview(loads: BlockLoads, sideMode: SideMode, totalMs: Long, hasLoad: Boolean) {
    Column {
        val summary = loadSummary(sideMode, loads, hasLoad)
        Text(
            listOfNotNull(summary, formatClock(totalMs)).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium
        )
        if (loads.missingMax) {
            Text(
                stringResource(R.string.missing_max_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
