package io.github.ambroal.climer.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.ui.components.EditorTopBar

private val unitSuggestions = listOf("kg", "rip", "s")

@Composable
fun MeasureTypeEditorScreen(onClose: () -> Unit, viewModel: MeasureTypeEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    Scaffold(topBar = { EditorTopBar(stringResource(R.string.measure_type), state.canSave, onClose, viewModel::save) }) { padding ->
        if (!state.loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.measure_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.unit,
                onValueChange = viewModel::onUnit,
                label = { Text(stringResource(R.string.unit)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unitSuggestions.forEach { u ->
                    FilterChip(selected = state.unit == u, onClick = { viewModel.onUnit(u) }, label = { Text(u) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.per_side), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.perSideLocked) R.string.per_side_locked else R.string.per_side_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.perSide, onCheckedChange = viewModel::onPerSide, enabled = !state.perSideLocked)
            }
        }
    }
}
