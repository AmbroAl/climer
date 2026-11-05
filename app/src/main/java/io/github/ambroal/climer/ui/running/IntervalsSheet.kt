package io.github.ambroal.climer.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.timer.Phase
import io.github.ambroal.climer.timer.PhaseType
import io.github.ambroal.climer.timer.phaseNameRes
import io.github.ambroal.climer.ui.theme.phaseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalsSheet(phases: List<Phase>, currentIndex: Int, settings: AppSettings, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(state = rememberLazyListState((currentIndex - 2).coerceAtLeast(0))) {
            itemsIndexed(phases) { index, phase ->
                val isCurrent = index == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
                        .clickable {
                            onSelect(index)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(phaseColor(phase.type, settings)))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(phaseNameRes(phase.type)),
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (phase.type == PhaseType.WORK || phase.type == PhaseType.REST) {
                        Text(
                            stringResource(R.string.round_cycle, phase.round, phase.cycle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(formatClock(phase.durationMs))
                }
            }
        }
    }
}
