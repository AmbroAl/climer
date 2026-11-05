package io.github.ambroal.climer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import io.github.ambroal.climer.ui.components.EditorTopBar

@Composable
fun ResultsScreen(onClose: () -> Unit, onSaved: () -> Unit, viewModel: ResultsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    Scaffold(topBar = { EditorTopBar(stringResource(R.string.results), state.loaded, onClose, viewModel::save) }) { padding ->
        if (!state.loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.workout?.let { w -> Text("${w.programName} · ${w.sessionName}", style = MaterialTheme.typography.titleMedium) }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.session_note)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
            )
        }
    }
}
