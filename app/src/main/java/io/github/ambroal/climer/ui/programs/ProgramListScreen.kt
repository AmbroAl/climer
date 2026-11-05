package io.github.ambroal.climer.ui.programs

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ListCard
import io.github.ambroal.climer.ui.components.TextInputDialog
import kotlinx.coroutines.launch

@Composable
fun ProgramListScreen(onOpenSettings: () -> Unit, onOpen: (Long) -> Unit, viewModel: ProgramListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tab_programs)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_program))
            }
        }
    ) { padding ->
        if (!state.loading && state.programs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.programs_empty),
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
            items(state.programs, key = { it.id }) { program ->
                ListCard(
                    title = program.name,
                    accent = MaterialTheme.colorScheme.primary,
                    subtitle = program.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
                    onClick = { onOpen(program.id) }
                )
            }
        }
    }
    if (creating) {
        TextInputDialog(stringResource(R.string.new_program), "", onDismiss = { creating = false }) { name ->
            scope.launch { onOpen(viewModel.create(name)) }
        }
    }
}
