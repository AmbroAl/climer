package io.github.ambroal.climer.ui.running

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.Side
import io.github.ambroal.climer.timer.PhaseType
import io.github.ambroal.climer.timer.phaseNameRes
import io.github.ambroal.climer.timer.totalDurationMs
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.IntervalConfigEditor
import io.github.ambroal.climer.ui.components.NotificationPermissionRequest
import io.github.ambroal.climer.ui.components.isLandscape
import io.github.ambroal.climer.ui.theme.FinishedColor
import io.github.ambroal.climer.ui.theme.contentColorFor
import io.github.ambroal.climer.ui.theme.phaseColor
import kotlinx.coroutines.launch

private val RoundsColor = Color(0xFF2F8CFF)
private val CyclesColor = Color(0xFFFFB800)

@Composable
fun RunningScreen(onExit: () -> Unit, onEditSounds: () -> Unit, viewModel: RunningViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.active) {
        if (state.active) {
            wasActive = true
        } else if (wasActive) {
            onExit()
        }
    }
    if (!state.active) return

    ScreenSettingsEffect(keepScreenOn = state.settings.keepScreenOn, allowRotation = state.settings.allowRotation)
    NotificationPermissionRequest()

    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var showIntervals by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Indietro da un timer mai avviato lo chiude (lo stato a null fa uscire); altrimenti resta "in corso".
    BackHandler { scope.launch { if (!viewModel.discardIfUnstarted()) onExit() } }
    val exit = {
        if (state.isFinished) {
            viewModel.stop()
        } else {
            confirmStop = true
        }
    }

    val phaseColor = state.phaseType?.let { phaseColor(it, state.settings) } ?: FinishedColor
    val onPhase = contentColorFor(phaseColor)

    val onShowIntervals = { showIntervals = true }
    val onRestart = { confirmRestart = true }
    if (isLandscape()) {
        // In orizzontale l'altezza è poca: fasce sottili sopra e sotto, il colore della fase a tutta larghezza in mezzo.
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            LandscapeHeader(state, onClose = exit, onEditTimer = { showEditor = true }, onEditSounds = onEditSounds)
            Box(Modifier.weight(1f).fillMaxWidth().background(phaseColor)) {
                PhaseFlash(state.currentIndex, enabled = state.settings.screenFlash)
                FittedPhaseClock(state, onPhase, Modifier.fillMaxSize())
            }
            LandscapeControls(state, onShowIntervals, onToggle = viewModel::togglePause, onDone = exit, onRestart = onRestart)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Header(state, onClose = exit, onEditTimer = { showEditor = true }, onEditSounds = onEditSounds)
            PhaseArea(state, phaseColor, onPhase, onShowIntervals, Modifier.weight(1f).fillMaxWidth())
            state.banner?.let { LoadBanner(it, state.exercise, state.equipment) }
            NextStrip(state)
            BottomControls(state, onToggle = viewModel::togglePause, onDone = exit, onRestart = onRestart)
        }
    }

    if (showEditor) {
        ConfigEditorSheet(state.config, onApply = { scope.launch { viewModel.applyConfig(it) } }, onDismiss = { showEditor = false })
    }
    if (showIntervals) {
        IntervalsSheet(state.phases, state.currentIndex, state.settings, viewModel::skipTo) { showIntervals = false }
    }
    if (confirmRestart) {
        ConfirmDialog(
            title = stringResource(R.string.restart_block_title),
            text = stringResource(R.string.restart_block_text),
            confirmLabel = stringResource(R.string.restart_block),
            onConfirm = viewModel::restart,
            onDismiss = { confirmRestart = false }
        )
    }
    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.stop_timer_title),
            text = stringResource(R.string.stop_timer_text),
            confirmLabel = stringResource(R.string.action_stop),
            onConfirm = viewModel::stop,
            onDismiss = { confirmStop = false }
        )
    }
}

@Composable
private fun PhaseArea(state: RunningUiState, phaseColor: Color, onPhase: Color, onShowIntervals: () -> Unit, modifier: Modifier) {
    Box(modifier.background(phaseColor)) {
        PhaseFlash(state.currentIndex, enabled = state.settings.screenFlash)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PhaseClock(state, onPhase, Modifier.weight(1f))
            if (!state.isFinished) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onShowIntervals).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.see_intervals).uppercase(), color = onPhase, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.KeyboardDoubleArrowRight, contentDescription = null, tint = onPhase)
                }
            }
        }
    }
}

@Composable
private fun Header(state: RunningUiState, onClose: () -> Unit, onEditTimer: () -> Unit, onEditSounds: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(96.dp)) {
        CloseButton(onClose, Modifier.width(120.dp).fillMaxHeight())
        MoreMenu(onEditTimer, onEditSounds, Modifier.fillMaxHeight())
        TotalInfo(state, 40.sp, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp))
    }
}

/** Testata orizzontale: come quella verticale ma più bassa, con il carico al centro. */
@Composable
private fun LandscapeHeader(state: RunningUiState, onClose: () -> Unit, onEditTimer: () -> Unit, onEditSounds: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        CloseButton(onClose, Modifier.width(96.dp).fillMaxHeight())
        MoreMenu(onEditTimer, onEditSounds, Modifier.fillMaxHeight())
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            state.banner?.let { LoadSummary(it, state.exercise, state.equipment) }
        }
        TotalInfo(state, 30.sp, Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier) {
    Box(
        modifier.background(Color(0xFFE53935)).clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.action_stop),
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun MoreMenu(onEditTimer: () -> Unit, onEditSounds: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(64.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_timer)) },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEditTimer()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_sounds)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEditSounds()
                }
            )
        }
    }
}

@Composable
private fun TotalInfo(state: RunningUiState, fontSize: TextUnit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        Text(state.totalRemaining, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black)
        val title = state.blockLabel?.let { stringResource(R.string.block_of, state.title, it) } ?: state.title
        Text(title.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Nome della fase sopra, tempo grande quanto lo spazio lascia anche in altezza (in orizzontale ce n'è poca). */
@Composable
private fun FittedPhaseClock(state: RunningUiState, color: Color, modifier: Modifier) {
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        val name = state.phaseType?.let { stringResource(phaseNameRes(it)) } ?: stringResource(R.string.speech_finished)
        val nameSize = (maxHeight.value * 0.16f).coerceIn(18f, 40f)
        val digits = state.phaseRemaining.length.coerceAtLeast(5)
        val clockSize = (maxWidth.value / (digits * 0.6f)).coerceAtMost((maxHeight.value - nameSize * 1.3f) * 0.82f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                name.uppercase(),
                color = color,
                fontSize = nameSize.sp,
                lineHeight = (nameSize * 1.1f).sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.semantics { heading() }
            )
            if (!state.isFinished) {
                Text(
                    state.phaseRemaining,
                    color = color,
                    fontSize = clockSize.sp,
                    lineHeight = (clockSize * 1.05f).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PhaseClock(state: RunningUiState, color: Color, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Le cifre occupano circa 0,6 em ciascuna: "00:10" sta nella larghezza disponibile.
        val digits = state.phaseRemaining.length.coerceAtLeast(5)
        val clockSize = (maxWidth.value / (digits * 0.6f)).coerceAtMost(maxHeight.value * 0.55f).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val name = state.phaseType?.let { stringResource(phaseNameRes(it)) } ?: stringResource(R.string.speech_finished)
            Text(
                name.uppercase(),
                color = color,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            if (!state.isFinished) {
                Text(state.phaseRemaining, color = color, fontSize = clockSize, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

/** Mano e peso da mostrare ("DESTRA · 22,5 KG", o i pesi di entrambe le mani); vuoto se il blocco non ne ha. */
@Composable
private fun loadText(banner: Banner): String {
    val side = when (banner.side) {
        Side.RIGHT -> stringResource(R.string.side_right)
        Side.LEFT -> stringResource(R.string.side_left)
        null -> null
    }
    val kg = banner.kg?.let { stringResource(R.string.kg_value, formatKg(it)) }
        ?: if (banner.side != null) stringResource(R.string.no_value) else null
    val bothHands = if (banner.rightKg != null || banner.leftKg != null) {
        val dash = stringResource(R.string.no_value)
        val right = stringResource(R.string.right_short, banner.rightKg?.let { stringResource(R.string.kg_value, formatKg(it)) } ?: dash)
        val left = stringResource(R.string.left_short, banner.leftKg?.let { stringResource(R.string.kg_value, formatKg(it)) } ?: dash)
        "$right · $left"
    } else {
        null
    }
    return (bothHands ?: listOfNotNull(side, kg).joinToString(" · ")).uppercase()
}

private fun detailText(exercise: String, equipment: String) = listOf(exercise, equipment).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun LoadBanner(banner: Banner, exercise: String, equipment: String) {
    val main = loadText(banner)
    if (main.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().background(
            if (banner.change) Color(0xFF2A2A2A) else Color(0xFF151515)
        ).padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Cambia" su una riga a sé: mano e peso restano su una riga grande, leggibile da lontano.
        if (banner.change) {
            Text(stringResource(R.string.change_label).uppercase(), color = FinishedColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(main, color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        val detail = detailText(exercise, equipment)
        if (detail.isNotEmpty()) Text(detail, color = Color(0xFFBDBDBD))
    }
}

/** Il carico in una riga, per la testata orizzontale; al cambio mano "CAMBIA" sta sopra, in evidenza. */
@Composable
private fun LoadSummary(banner: Banner, exercise: String, equipment: String) {
    val main = loadText(banner)
    if (main.isEmpty()) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (banner.change) {
            Text(
                stringResource(R.string.change_label).uppercase(),
                color = FinishedColor,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(main, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
        val detail = detailText(exercise, equipment)
        if (!banner.change && detail.isNotEmpty()) {
            Text(detail, color = Color(0xFFBDBDBD), fontSize = 13.sp, lineHeight = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NextStrip(state: RunningUiState) {
    val next = state.nextType
    if (next == null) {
        state.nextBlockName?.let { NextBlockStrip(it, state) }
        return
    }
    val color = phaseColor(next, state.settings)
    Column(
        Modifier.fillMaxWidth().background(color).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val content = contentColorFor(color)
        Text(stringResource(R.string.next).uppercase(), color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(phaseNameRes(next)).uppercase(), color = content, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BottomControls(state: RunningUiState, onToggle: () -> Unit, onDone: () -> Unit, onRestart: () -> Unit) {
    // Pulsanti contenuti: ai contatori resta spazio per etichetta e numero anche sui telefoni più stretti.
    Row(
        Modifier.fillMaxWidth().height(ControlSize + 20.dp).background(Color.Black).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Counter(stringResource(R.string.rounds_left), state.roundsLeft, RoundsColor, Modifier.weight(1f))
        PlayButton(state, onToggle, onDone, Modifier.size(ControlSize))
        Spacer(Modifier.width(8.dp))
        RestartButton(onRestart, Modifier.size(width = 56.dp, height = ControlSize))
        Counter(stringResource(R.string.cycles_left), state.cyclesLeft, CyclesColor, Modifier.weight(1f))
    }
}

/** Riga dei comandi in orizzontale: il "Prossimo" sta tra i contatori e, toccato, apre gli intervalli. */
@Composable
private fun LandscapeControls(
    state: RunningUiState,
    onShowIntervals: () -> Unit,
    onToggle: () -> Unit,
    onDone: () -> Unit,
    onRestart: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Counter(stringResource(R.string.rounds_left), state.roundsLeft, RoundsColor, Modifier.weight(1f))
        NextBox(state, onShowIntervals, Modifier.width(220.dp).height(80.dp))
        Spacer(Modifier.width(12.dp))
        PlayButton(state, onToggle, onDone, Modifier.size(80.dp))
        Spacer(Modifier.width(8.dp))
        RestartButton(onRestart, Modifier.size(width = 52.dp, height = 80.dp))
        Counter(stringResource(R.string.cycles_left), state.cyclesLeft, CyclesColor, Modifier.weight(1f))
    }
}

@Composable
private fun NextBox(state: RunningUiState, onShowIntervals: () -> Unit, modifier: Modifier) {
    val next = state.nextType
    val blockName = state.nextBlockName
    val (color, label, name) = when {
        next != null -> Triple(phaseColor(next, state.settings), stringResource(R.string.next), stringResource(phaseNameRes(next)))
        blockName != null -> Triple(phaseColor(PhaseType.PREPARE, state.settings), stringResource(R.string.next_block), blockName)
        else -> Triple(Color(0xFF2A2A2A), stringResource(R.string.see_intervals), "")
    }
    val content = contentColorFor(color)
    Column(
        modifier.background(color).clickable(onClick = onShowIntervals).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("${label.uppercase()}  »", color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (name.isNotEmpty()) Text(name.uppercase(), color = content, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PlayButton(state: RunningUiState, onToggle: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Box(
        modifier.background(FinishedColor).clickable(role = Role.Button, onClick = if (state.isFinished) onDone else onToggle),
        contentAlignment = Alignment.Center
    ) {
        val (icon, label) = when {
            state.isFinished -> Icons.Default.Close to stringResource(R.string.close)
            state.isRunning -> Icons.Default.Pause to stringResource(R.string.action_pause)
            else -> Icons.Default.PlayArrow to stringResource(R.string.action_resume)
        }
        Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun RestartButton(onRestart: () -> Unit, modifier: Modifier) {
    Box(modifier.background(Color(0xFF2A2A2A)).clickable(role = Role.Button, onClick = onRestart), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Replay,
            contentDescription = stringResource(R.string.restart_block),
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

private val ControlSize = 96.dp

/** Etichetta sempre su due righe ("ROUND / RIMASTI"): i due contatori restano allineati su ogni telefono. */
@Composable
private fun Counter(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            color = Color(0xFFBDBDBD),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            minLines = 2,
            maxLines = 2
        )
        Text(value.toString(), color = color, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun PhaseFlash(index: Int, enabled: Boolean) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (!enabled) return@LaunchedEffect
        alpha.snapTo(0.8f)
        alpha.animateTo(0f, tween(400))
    }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alpha.value)))
}

@Composable
private fun ScreenSettingsEffect(keepScreenOn: Boolean, allowRotation: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(keepScreenOn, allowRotation) {
        view.keepScreenOn = keepScreenOn
        activity?.requestedOrientation =
            if (allowRotation) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/** Ultima fase di un blocco in una seduta avviata per intero: dopo parte il blocco indicato. */
@Composable
private fun NextBlockStrip(name: String, state: RunningUiState) {
    val color = phaseColor(PhaseType.PREPARE, state.settings)
    val content = contentColorFor(color)
    Column(Modifier.fillMaxWidth().background(color).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.next_block).uppercase(), color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(name.uppercase(), color = content, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Modifica del timer in corso: vale subito e viene salvata nel timer o nel blocco di origine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditorSheet(initial: IntervalConfig, onApply: (IntervalConfig) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.edit_timer).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.edit_timer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            IntervalConfigEditor(draft, { draft = it })
            Text(
                stringResource(R.string.total_duration, formatClock(draft.totalDurationMs())),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(stringResource(R.string.apply)) }
        }
    }
}
