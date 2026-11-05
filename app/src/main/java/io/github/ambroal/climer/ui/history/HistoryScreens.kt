package io.github.ambroal.climer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutLogEntity
import io.github.ambroal.climer.data.db.entity.WorkoutStatus
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatDate
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.ListCard
import java.time.Instant
import java.time.ZoneId

@Composable
private fun workoutSubtitle(w: WorkoutLogEntity): String {
    val date = formatDate(Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()).toLocalDate())
    val duration = w.endedAt?.let { formatClock(it - w.startedAt) }
    val status = if (w.status == WorkoutStatus.IN_PROGRESS) stringResource(R.string.workout_in_progress) else null
    return listOfNotNull(w.programName.ifBlank { null }, date, duration, status).joinToString(" · ")
}

@Composable
fun HistoryListScreen(onOpenSettings: () -> Unit, onOpen: (WorkoutLogEntity) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tab_history)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
    ) { padding ->
        if (!state.loading && state.workouts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.workouts, key = { it.id }) { w ->
                ListCard(
                    title = w.sessionName,
                    accent = if (w.status ==
                        WorkoutStatus.IN_PROGRESS
                    ) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    subtitle = workoutSubtitle(w),
                    onClick = { onOpen(w) }
                )
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: WorkoutDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val workout = state.workout
    Scaffold(
        topBar = {
            AppTopBar(workout?.sessionName.orEmpty(), onBack = onBack) {
                if (workout != null) {
                    IconButton(onClick = {
                        onEdit(workout.id)
                    }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    ) { padding ->
        if (workout == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(workoutSubtitle(workout), style = MaterialTheme.typography.titleMedium) }
            if (workout.note.isNotBlank()) item { Text(workout.note) }
            items(state.logs, key = { it.id }) { log ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(blockLogTitle(log).uppercase(), fontWeight = FontWeight.Bold)
                        Text(
                            plannedText(log),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val done = listOfNotNull(
                            log.actualKg?.let { stringResource(R.string.kg_value, formatKg(it)) },
                            log.repsDone?.let { stringResource(R.string.reps_value, it) },
                            log.pump?.let { stringResource(R.string.pump_value, it) }
                        )
                        Text(
                            if (done.isEmpty()) {
                                stringResource(if (log.completed) R.string.done else R.string.not_completed)
                            } else {
                                stringResource(R.string.done_value, done.joinToString(" · "))
                            }
                        )
                        if (log.note.isNotBlank()) Text(log.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_workout_title),
            stringResource(R.string.delete_workout_text),
            stringResource(R.string.delete),
            viewModel::delete,
            { confirmDelete = false }
        )
    }
}

@Composable
fun blockLogTitle(log: BlockLogEntity): String {
    val side = when (log.side) {
        Side.RIGHT -> stringResource(R.string.side_right)
        Side.LEFT -> stringResource(R.string.side_left)
        null -> null
    }
    return listOfNotNull(log.blockName, side).joinToString(" · ")
}

@Composable
fun plannedText(log: BlockLogEntity): String {
    val kg = log.plannedKg?.let { stringResource(R.string.kg_value, formatKg(it)) }
    return stringResource(R.string.planned, listOfNotNull(kg, stringResource(R.string.reps_value, log.plannedReps)).joinToString(" · "))
}
