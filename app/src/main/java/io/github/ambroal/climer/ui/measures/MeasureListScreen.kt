package io.github.ambroal.climer.ui.measures

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatDate
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ListCard

@Composable
fun MeasureListScreen(
    onOpenSettings: () -> Unit,
    onNewType: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: MeasureListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tab_measures)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewType, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_measure_type))
            }
        }
    ) { padding ->
        if (!state.loading && state.types.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.measures_empty),
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
            items(state.types, key = { it.type.id }) { row ->
                val latest = row.latest
                ListCard(
                    title = row.type.name,
                    accent = MaterialTheme.colorScheme.primary,
                    subtitle = if (latest == null) {
                        stringResource(R.string.no_measures_yet)
                    } else {
                        stringResource(R.string.latest_measure, entryValueText(latest, row.type.unit), formatDate(latest.date))
                    },
                    onClick = { onOpen(row.type.id) }
                )
            }
        }
    }
}
