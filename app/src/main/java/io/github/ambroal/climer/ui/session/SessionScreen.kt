package io.github.ambroal.climer.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.BlockLogEntity
import io.github.ambroal.climer.data.repo.BlockLoads
import io.github.ambroal.climer.data.repo.BlockWithLoad
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.MenuAction
import io.github.ambroal.climer.ui.components.OverflowMenu
import io.github.ambroal.climer.ui.programs.loadSummary
import io.github.ambroal.climer.ui.programs.sideModeLabel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onAddBlock: (Long) -> Unit,
    onEditBlock: (sessionId: Long, blockId: Long) -> Unit,
    onBlockStarted: () -> Unit,
    onFinish: (workoutId: Long) -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Copia locale durante il trascinamento: il database si aggiorna solo al rilascio.
    var blocks by remember { mutableStateOf(state.blocks) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.blocks) { if (!dragging) blocks = state.blocks }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = blocks.indexOfFirst { it.block.id == from.key }
        val toIndex = blocks.indexOfFirst { it.block.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) blocks = blocks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    var toDelete by remember { mutableStateOf<BlockWithLoad?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    val session = state.session
    Scaffold(
        topBar = { AppTopBar(session?.name.orEmpty(), onBack = onBack) },
        floatingActionButton = {
            if (session != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddBlock(session.id) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_block)) }
                )
            }
        },
        bottomBar = {
            val workoutId = state.workoutId
            if (workoutId != null) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmRestart = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Text(stringResource(R.string.restart_session), Modifier.padding(start = 8.dp))
                    }
                    Button(onClick = { onFinish(workoutId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.finish_session))
                    }
                }
            }
        }
    ) { padding ->
        if (session == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.otherInProgress?.let { other ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.other_session_open, other.sessionName))
                            TextButton(onClick = viewModel::closeOther) { Text(stringResource(R.string.close_other_session)) }
                        }
                    }
                }
            }
            if (state.blocks.isNotEmpty()) {
                item {
                    val partlyDone =
                        state.blocks.any { it.block.id in state.doneBlockIds } && state.blocks.any { it.block.id !in state.doneBlockIds }
                    Button(
                        onClick = {
                            scope.launch { if (viewModel.startSession() != null) onBlockStarted() }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(
                            stringResource(if (partlyDone) R.string.continue_session else R.string.start_session),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (state.blocks.isEmpty() && !state.loading) {
                item { Text(stringResource(R.string.blocks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(blocks, key = { _, b -> b.block.id }) { index, row ->
                ReorderableItem(reorderState, key = row.block.id) { isDragging ->
                    BlockCard(
                        row = row,
                        dragging = isDragging,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { dragging = true },
                            onDragStopped = {
                                dragging = false
                                viewModel.reorderBlocks(blocks.map { it.block.id })
                            }
                        ),
                        done = row.block.id in state.doneBlockIds,
                        onStart = {
                            scope.launch {
                                viewModel.startBlock(row.block.id)
                                onBlockStarted()
                            }
                        },
                        menu = buildList {
                            add(MenuAction(stringResource(R.string.edit)) { onEditBlock(session.id, row.block.id) })
                            add(MenuAction(stringResource(R.string.duplicate)) { viewModel.duplicateBlock(row.block.id) })
                            if (index > 0) add(MenuAction(stringResource(R.string.move_up)) { viewModel.moveBlock(index, index - 1) })
                            if (index < state.blocks.lastIndex) {
                                add(MenuAction(stringResource(R.string.move_down)) { viewModel.moveBlock(index, index + 1) })
                            }
                            add(MenuAction(stringResource(R.string.delete)) { toDelete = row })
                        }
                    )
                }
            }
        }
    }
    if (confirmRestart) {
        ConfirmDialog(
            stringResource(R.string.restart_session_title),
            stringResource(R.string.restart_session_text),
            stringResource(R.string.restart_session),
            { scope.launch { viewModel.restartSession() } },
            { confirmRestart = false }
        )
    }
    toDelete?.let { row ->
        ConfirmDialog(
            stringResource(R.string.delete_block_title),
            stringResource(R.string.delete_block_text, row.block.name),
            stringResource(R.string.delete),
            { viewModel.deleteBlock(row.block.id) },
            { toDelete = null }
        )
    }
}

// handleModifier va sulla sola maniglia di trascinamento, non sulla card.
@Suppress("ModifierParameter")
@Composable
private fun BlockCard(
    row: BlockWithLoad,
    done: Boolean,
    dragging: Boolean,
    handleModifier: Modifier,
    onStart: () -> Unit,
    menu: List<MenuAction>
) {
    val block = row.block
    val container = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(if (dragging) 8.dp else 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = handleModifier.padding(end = 8.dp)
                )
                if (done) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.done),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    block.name.uppercase(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OverflowMenu(menu)
            }
            val detail = listOf(block.exercise, block.equipment).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val loads = BlockLoads(row.kgRight, row.kgLeft, row.kgSingle, row.missingMax)
            val summary = loadSummary(block.sideMode, loads, hasLoad = block.load != LoadSpec.None)
            Text(
                listOfNotNull(sideModeLabel(block.sideMode), summary, formatClock(row.totalDurationMs)).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge
            )
            if (row.missingMax) {
                Text(
                    stringResource(R.string.missing_max_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            lastTimeText(row.lastTime)?.let {
                Text(
                    stringResource(R.string.last_time, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (done) {
                    OutlinedButton(onClick = onStart) { Text(stringResource(R.string.repeat)) }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(stringResource(R.string.start), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/** "Dx 17,5 kg · 90/90 · ghisa 3 — Sx 16,5 kg · 90/90" */
@Composable
fun lastTimeText(logs: List<BlockLogEntity>): String? {
    if (logs.isEmpty()) return null
    val res = LocalResources.current
    return logs.joinToString(" — ") { log ->
        val side = when (log.side) {
            Side.RIGHT -> res.getString(R.string.right_short, "")
            Side.LEFT -> res.getString(R.string.left_short, "")
            null -> ""
        }.trim()
        val kg = (log.actualKg ?: log.plannedKg)?.let { res.getString(R.string.kg_value, formatKg(it)) }
        val reps = log.repsDone?.let { "$it/${log.plannedReps}" }
        val pump = log.pump?.let { res.getString(R.string.pump_value, it) }
        listOfNotNull(side.ifEmpty { null }, kg, reps, pump).joinToString(" · ")
    }
}
