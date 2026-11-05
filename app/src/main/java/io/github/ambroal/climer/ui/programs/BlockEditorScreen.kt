package io.github.ambroal.climer.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.totalDurationMs
import io.github.ambroal.climer.ui.components.EditorTopBar
import io.github.ambroal.climer.ui.components.IntervalConfigEditor
import io.github.ambroal.climer.ui.measures.DecimalField

@Composable
fun sideModeLabel(mode: SideMode): String = stringResource(
    when (mode) {
        SideMode.NONE -> R.string.side_mode_none
        SideMode.RIGHT -> R.string.side_right
        SideMode.LEFT -> R.string.side_left
        SideMode.BOTH -> R.string.side_mode_both
        SideMode.ALTERNATE_RIGHT_FIRST -> R.string.side_mode_alt_right
        SideMode.ALTERNATE_LEFT_FIRST -> R.string.side_mode_alt_left
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockEditorScreen(onClose: () -> Unit, viewModel: BlockEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    val form = state.form
    Scaffold(topBar = { EditorTopBar(stringResource(R.string.block), state.canSave, onClose, viewModel::save) }) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field(form.name, viewModel::onName, stringResource(R.string.name), stringResource(R.string.block_name_hint))
            Field(form.exercise, viewModel::onExercise, stringResource(R.string.exercise), stringResource(R.string.exercise_hint))
            Field(form.equipment, viewModel::onEquipment, stringResource(R.string.equipment), stringResource(R.string.equipment_hint))
            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotes,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth()
            )

            Section(stringResource(R.string.side))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SideMode.entries.forEach { mode ->
                    FilterChip(selected = form.sideMode == mode, onClick = {
                        viewModel.onSideMode(mode)
                    }, label = { Text(sideModeLabel(mode)) })
                }
            }
            if (form.sideMode == SideMode.ALTERNATE_RIGHT_FIRST || form.sideMode == SideMode.ALTERNATE_LEFT_FIRST) {
                Text(
                    stringResource(R.string.alternate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section(stringResource(R.string.load))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadKind.entries.forEach { kind ->
                    val label = when (kind) {
                        LoadKind.NONE -> R.string.load_none
                        LoadKind.FIXED -> R.string.load_fixed
                        LoadKind.FIXED_PER_SIDE -> R.string.load_fixed_per_side
                        LoadKind.PERCENT -> R.string.load_percent
                    }
                    FilterChip(selected = form.loadKind == kind, onClick = {
                        viewModel.onLoadKind(kind)
                    }, label = { Text(stringResource(label)) })
                }
            }
            when (form.loadKind) {
                LoadKind.NONE -> Unit
                LoadKind.FIXED -> DecimalField(form.fixedKg, viewModel::onFixedKg, "kg", form.fixedKgError, Modifier.fillMaxWidth())
                LoadKind.FIXED_PER_SIDE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(
                        form.fixedRight,
                        viewModel::onFixedRight,
                        "${stringResource(R.string.side_right)} kg",
                        form.fixedRightError,
                        Modifier.weight(1f)
                    )
                    DecimalField(
                        form.fixedLeft,
                        viewModel::onFixedLeft,
                        "${stringResource(R.string.side_left)} kg",
                        form.fixedLeftError,
                        Modifier.weight(1f)
                    )
                }
                LoadKind.PERCENT -> PercentFields(state, viewModel)
            }
            state.preview?.let { LoadPreview(it, form.sideMode, state.totalMs, hasLoad = form.loadKind != LoadKind.NONE) }

            Section(stringResource(R.string.timer))
            CopyFromTimer(state, viewModel)
            IntervalConfigEditor(form.config, viewModel::onConfig)
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun PercentFields(state: BlockEditorUiState, viewModel: BlockEditorViewModel) {
    var open by remember { mutableStateOf(false) }
    val selected = state.measureTypes.firstOrNull { it.id == state.form.measureTypeId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: stringResource(R.string.choose_measure_type))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.measureTypes.isEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.no_measure_types)) }, onClick = { open = false }, enabled = false)
            }
            state.measureTypes.forEach { type ->
                DropdownMenuItem(text = { Text(type.name) }, onClick = {
                    viewModel.onMeasureType(type.id)
                    open = false
                })
            }
        }
    }
    DecimalField(
        state.form.percent,
        viewModel::onPercent,
        stringResource(R.string.percent),
        state.form.percentError,
        Modifier.fillMaxWidth()
    )
}

@Composable
private fun CopyFromTimer(state: BlockEditorUiState, viewModel: BlockEditorViewModel) {
    if (state.savedTimers.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.copy_from_timer)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.savedTimers.forEach { timer ->
                DropdownMenuItem(text = { Text("${timer.name} · ${formatClock(timer.config.totalDurationMs())}") }, onClick = {
                    viewModel.copyFromTimer(timer)
                    open = false
                })
            }
        }
    }
}
