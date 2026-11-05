package io.github.ambroal.climer.ui.programs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.SessionTemplateEntity
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.ListCard
import io.github.ambroal.climer.ui.components.MenuAction
import io.github.ambroal.climer.ui.components.OverflowMenu
import io.github.ambroal.climer.ui.components.TextInputDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface ProgramDialog {
    data object Rename : ProgramDialog

    data object Notes : ProgramDialog

    data object NewSession : ProgramDialog

    data object DeleteProgram : ProgramDialog

    data class RenameSession(val session: SessionTemplateEntity) : ProgramDialog

    data class DeleteSession(val session: SessionTemplateEntity) : ProgramDialog
}

@Composable
fun ProgramDetailScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit, viewModel: ProgramDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ProgramDialog?>(null) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Tieni premuta una seduta e trascinala; l'ordine si salva al rilascio.
    var sessions by remember { mutableStateOf(state.sessions) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessions) { if (!dragging) sessions = state.sessions }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = sessions.indexOfFirst { it.id == from.key }
        val toIndex = sessions.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) sessions = sessions.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    val noApp = stringResource(R.string.no_app_for_document)
    val missing = stringResource(R.string.attachment_missing)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::attach) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val program = state.program

    Scaffold(
        topBar = {
            AppTopBar(program?.name.orEmpty(), onBack = onBack) {
                OverflowMenu(
                    listOf(
                        MenuAction(stringResource(R.string.rename)) { dialog = ProgramDialog.Rename },
                        MenuAction(stringResource(R.string.edit_notes)) { dialog = ProgramDialog.Notes },
                        MenuAction(stringResource(R.string.delete)) { dialog = ProgramDialog.DeleteProgram }
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = ProgramDialog.NewSession },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_session)) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        if (program == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (program.notes.isNotBlank()) {
                item {
                    Text(program.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        val name = program.attachmentName
                        if (name == null) {
                            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Default.AttachFile, contentDescription = null)
                                Text(stringResource(R.string.attach_document), Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Text(name, Modifier.weight(1f).padding(horizontal = 8.dp))
                            }
                            Row {
                                TextButton(onClick = {
                                    val uri = viewModel.attachmentUri()
                                    if (uri == null) {
                                        Toast.makeText(context, missing, Toast.LENGTH_LONG).show()
                                        return@TextButton
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, program.attachmentMime ?: "*/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Text(stringResource(R.string.open), Modifier.padding(start = 4.dp))
                                }
                                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.replace)) }
                                TextButton(onClick = viewModel::removeAttachment) { Text(stringResource(R.string.remove)) }
                            }
                        }
                        if (state.attachmentError) {
                            Text(stringResource(R.string.attachment_error), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (state.sessions.isEmpty()) {
                item { Text(stringResource(R.string.sessions_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                ReorderableItem(reorderState, key = session.id) { isDragging ->
                    ListCard(
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { dragging = true },
                            onDragStopped = {
                                dragging = false
                                viewModel.reorderSessions(sessions.map { it.id })
                            }
                        ),
                        highlighted = isDragging,
                        title = session.name,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { onOpenSession(session.id) },
                        menu = buildList {
                            add(MenuAction(stringResource(R.string.rename)) { dialog = ProgramDialog.RenameSession(session) })
                            if (index > 0) add(MenuAction(stringResource(R.string.move_up)) { viewModel.moveSession(index, index - 1) })
                            if (index < state.sessions.lastIndex) {
                                add(MenuAction(stringResource(R.string.move_down)) { viewModel.moveSession(index, index + 1) })
                            }
                            add(MenuAction(stringResource(R.string.delete)) { dialog = ProgramDialog.DeleteSession(session) })
                        }
                    )
                }
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        ProgramDialog.Rename -> TextInputDialog(
            stringResource(R.string.rename),
            program?.name.orEmpty(),
            close,
            onConfirm = viewModel::rename
        )
        ProgramDialog.Notes -> TextInputDialog(
            stringResource(R.string.edit_notes),
            program?.notes.orEmpty(),
            close,
            singleLine = false,
            allowBlank = true,
            onConfirm = viewModel::setNotes
        )
        ProgramDialog.NewSession -> TextInputDialog(stringResource(R.string.new_session), "", close, onConfirm = viewModel::addSession)
        ProgramDialog.DeleteProgram -> ConfirmDialog(
            stringResource(R.string.delete_program_title),
            stringResource(R.string.delete_program_text, program?.name.orEmpty()),
            stringResource(R.string.delete),
            viewModel::deleteProgram,
            close
        )
        is ProgramDialog.RenameSession -> TextInputDialog(stringResource(R.string.rename), d.session.name, close) {
            viewModel.renameSession(d.session, it)
        }
        is ProgramDialog.DeleteSession -> ConfirmDialog(
            stringResource(R.string.delete_session_title),
            stringResource(R.string.delete_session_text, d.session.name),
            stringResource(R.string.delete),
            { viewModel.deleteSession(d.session.id) },
            close
        )
        null -> Unit
    }
}
