package io.github.ambroal.climer.ui.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.ListCard
import io.github.ambroal.climer.ui.components.MenuAction

@Composable
fun TimerListScreen(
    onOpenSettings: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onStarted: () -> Unit,
    viewModel: TimerListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SavedTimerEntity?>(null) }
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.my_timers)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_timer))
            }
        }
    ) { padding ->
        if (!state.loading && state.timers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.timers_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.timers, key = { it.timer.id }) { row ->
                ListCard(
                    title = row.timer.name,
                    accent = Color(row.timer.colorArgb),
                    leading = formatClock(row.totalMs),
                    onClick = {
                        viewModel.start(row.timer)
                        onStarted()
                    },
                    menu = listOf(
                        MenuAction(stringResource(R.string.edit)) { onEdit(row.timer.id) },
                        MenuAction(stringResource(R.string.duplicate)) { viewModel.duplicate(row.timer.id) },
                        MenuAction(stringResource(R.string.delete)) { toDelete = row.timer }
                    )
                )
            }
        }
    }
    toDelete?.let { timer ->
        ConfirmDialog(
            title = stringResource(R.string.delete_timer_title),
            text = stringResource(R.string.delete_timer_text, timer.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { viewModel.delete(timer.id) },
            onDismiss = { toDelete = null }
        )
    }
}
