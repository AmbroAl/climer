package io.github.ambroal.climer.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatDate
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.ListCard
import io.github.ambroal.climer.ui.components.MenuAction

@Composable
fun MeasureDetailScreen(onBack: () -> Unit, onEditType: (Long) -> Unit, viewModel: MeasureDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDeleteType by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val type = state.type
    Scaffold(
        topBar = {
            AppTopBar(type?.name.orEmpty(), onBack = onBack) {
                if (type != null) {
                    IconButton(onClick = {
                        onEditType(type.id)
                    }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
                    IconButton(onClick = { confirmDeleteType = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openNewEntry() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_measure))
            }
        }
    ) { padding ->
        if (type == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MeasureChart(state.entries, type.perSide) }
            if (state.entries.isEmpty()) {
                item { Text(stringResource(R.string.no_measures_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.entries, key = { it.id }) { entry ->
                ListCard(
                    title = entryValueText(entry, type.unit),
                    accent = MaterialTheme.colorScheme.primary,
                    leading = formatDate(entry.date),
                    subtitle = entry.note.ifBlank { null },
                    onClick = { viewModel.openEditEntry(entry) },
                    menu = listOf(
                        MenuAction(stringResource(R.string.edit)) { viewModel.openEditEntry(entry) },
                        MenuAction(stringResource(R.string.delete)) { viewModel.deleteEntry(entry.id) }
                    )
                )
            }
        }
    }
    state.entryForm?.let { form ->
        MeasureEntryDialog(
            form = form,
            unit = type?.unit.orEmpty(),
            onDate = viewModel::onEntryDate,
            onValue = viewModel::onEntryValue,
            onRight = viewModel::onEntryRight,
            onLeft = viewModel::onEntryLeft,
            onNote = viewModel::onEntryNote,
            onSave = viewModel::saveEntry,
            onDismiss = viewModel::closeEntry
        )
    }
    if (confirmDeleteType && type != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_measure_type_title),
            text = stringResource(R.string.delete_measure_type_text, type.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = viewModel::deleteType,
            onDismiss = { confirmDeleteType = false }
        )
    }
}
