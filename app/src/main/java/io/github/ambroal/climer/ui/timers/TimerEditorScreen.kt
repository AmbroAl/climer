package io.github.ambroal.climer.ui.timers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.ui.components.ColorChooser
import io.github.ambroal.climer.ui.components.EditorTopBar
import io.github.ambroal.climer.ui.components.IntervalConfigEditor
import io.github.ambroal.climer.ui.theme.Palette

@Composable
fun TimerEditorScreen(onClose: () -> Unit, viewModel: TimerEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    Scaffold(
        topBar = { EditorTopBar(stringResource(R.string.timer), state.canSave, onClose, viewModel::save) }
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            ColorChooser(Palette, state.colorArgb, viewModel::onColor)
            Spacer(Modifier.height(16.dp))
            IntervalConfigEditor(state.config, viewModel::onConfig)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.total_duration, formatClock(state.totalMs)),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
