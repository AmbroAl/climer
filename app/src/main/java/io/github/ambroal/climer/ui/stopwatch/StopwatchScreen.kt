package io.github.ambroal.climer.ui.stopwatch

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatStopwatch
import io.github.ambroal.climer.ui.components.ConfirmDialog
import io.github.ambroal.climer.ui.components.DurationPickerDialog
import io.github.ambroal.climer.ui.components.NotificationPermissionRequest
import io.github.ambroal.climer.ui.components.isLandscape
import io.github.ambroal.climer.ui.theme.Palette
import io.github.ambroal.climer.ui.theme.contentColorFor

@Composable
fun StopwatchScreen(onOpenSettings: () -> Unit, viewModel: StopwatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickInterval by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(state.running, state.settings.keepScreenOn) {
        view.keepScreenOn = state.running && state.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    if (state.active) NotificationPermissionRequest()
    val landscape = isLandscape()
    Scaffold(
        topBar = {
            // In orizzontale l'elenco dei giri non ci sta: l'ultimo giro compare sotto il titolo.
            val lastLap = state.laps.firstOrNull()?.takeIf { landscape }?.let {
                stringResource(R.string.lap_summary, it.number, formatStopwatch(it.splitMs))
            }
            TopBar(state.active, compact = landscape, subtitle = lastLap, onClose = {
                confirmClose = true
            }, onOpenSettings = onOpenSettings) { open, onDismiss ->
                AlertMenu(state.settings, open, onDismiss, viewModel::update)
            }
        }
    ) { padding ->
        val controls = @Composable { modifier: Modifier ->
            Controls(state, onToggle = viewModel::toggle, onLap = viewModel::lap, onReset = viewModel::reset, modifier = modifier)
        }
        val alert = @Composable { modifier: Modifier ->
            AlertRow(state.settings, onPickInterval = { pickInterval = true }, onAlertOn = viewModel::setAlertOn, modifier = modifier)
        }
        if (landscape) {
            // Come il timer in orizzontale: tempo a tutta larghezza, comandi in una fascia sotto.
            Column(Modifier.fillMaxSize().padding(padding)) {
                TimeArea(state, Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    alert(Modifier.weight(1f).padding(start = 16.dp))
                    controls(Modifier.width(400.dp))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                TimeArea(state, Modifier.fillMaxWidth().height(200.dp))
                controls(Modifier.fillMaxWidth())
                alert(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Laps(state.laps, Modifier.weight(1f))
            }
        }
    }
    if (pickInterval) {
        DurationPickerDialog(stringResource(R.string.alert_every), state.settings.stopwatchAlertSec, { pickInterval = false }) {
            viewModel.setAlertEvery(it)
        }
    }
    if (confirmClose) {
        ConfirmDialog(
            stringResource(R.string.close_stopwatch_title),
            stringResource(R.string.close_stopwatch_text),
            stringResource(R.string.close),
            viewModel::reset
        ) { confirmClose = false }
    }
    if (state.confirmReplace) {
        ConfirmDialog(
            stringResource(R.string.replace_timer_title),
            stringResource(R.string.replace_timer_text),
            stringResource(R.string.start),
            viewModel::confirmReplace,
            viewModel::dismissReplace
        )
    }
}

/** Stessa testata dei timer: X rossa a sinistra per chiudere tutto, ⋮ accanto con le opzioni dell'avviso. */
@Composable
private fun TopBar(
    active: Boolean,
    compact: Boolean,
    subtitle: String?,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    menu: @Composable (open: Boolean, onDismiss: () -> Unit) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(if (compact) 64.dp else 96.dp)) {
        Box(
            Modifier
                .width(if (compact) 96.dp else 120.dp)
                .fillMaxHeight()
                .background(CloseRed.copy(alpha = if (active) 1f else 0.35f))
                .clickable(enabled = active, role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White.copy(alpha = if (active) 1f else 0.5f),
                modifier = Modifier.size(40.dp)
            )
        }
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            menu(menuOpen) { menuOpen = false }
        }
        Column(Modifier.weight(1f).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.stopwatch).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            subtitle?.let { Text(it, color = Color(0xFFBDBDBD), maxLines = 1) }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterVertically)) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
        }
    }
}

private val CloseRed = Color(0xFFE53935)

/** Il menu resta aperto a ogni scelta: di solito si accendono o spengono più opzioni di seguito. */
@Composable
private fun AlertMenu(s: AppSettings, open: Boolean, onDismiss: () -> Unit, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.alert_on_each),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AlertMenuItem(R.string.alert_sound, Icons.AutoMirrored.Filled.VolumeUp, s.stopwatchSound) {
            onUpdate { it.copy(stopwatchSound = !it.stopwatchSound) }
        }
        AlertMenuItem(R.string.alert_voice, Icons.Default.RecordVoiceOver, s.stopwatchVoice) {
            onUpdate { it.copy(stopwatchVoice = !it.stopwatchVoice) }
        }
        AlertMenuItem(R.string.vibration, Icons.Default.Vibration, s.stopwatchVibrate) {
            onUpdate { it.copy(stopwatchVibrate = !it.stopwatchVibrate) }
        }
        AlertMenuItem(R.string.alert_color, Icons.Default.Palette, s.stopwatchColor) {
            onUpdate { it.copy(stopwatchColor = !it.stopwatchColor) }
        }
    }
}

@Composable
private fun AlertMenuItem(@StringRes label: Int, icon: ImageVector, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = onToggle
    )
}

@Composable
private fun TimeArea(state: StopwatchUiState, modifier: Modifier) {
    // A ogni avviso lo sfondo passa al colore successivo della palette: il cambio si vede anche da lontano.
    val colored = state.settings.stopwatchColor && state.alertCount > 0
    val target = if (colored) {
        Color(Palette[(state.alertCount - 1) % Palette.size])
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val background by animateColorAsState(target, label = "stopwatchBackground")
    val content = if (colored) contentColorFor(background) else Color.White
    BoxWithConstraints(modifier.background(background), contentAlignment = Alignment.Center) {
        val text = formatStopwatch(state.elapsedMs)
        val size = (maxWidth.value / (text.length * 0.62f)).coerceAtMost(maxHeight.value * 0.6f).coerceAtMost(160f).sp
        Text(text, color = content, fontSize = size, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun Controls(state: StopwatchUiState, onToggle: () -> Unit, onLap: () -> Unit, onReset: () -> Unit, modifier: Modifier) {
    Row(modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.running) {
            OutlinedButton(onClick = onLap, modifier = Modifier.weight(1f).height(64.dp)) {
                Icon(Icons.Default.Flag, contentDescription = null)
                Text(stringResource(R.string.lap), Modifier.padding(start = 8.dp))
            }
        } else {
            OutlinedButton(onClick = onReset, enabled = state.active, modifier = Modifier.weight(1f).height(64.dp)) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Text(stringResource(R.string.reset), Modifier.padding(start = 8.dp))
            }
        }
        Button(onClick = onToggle, modifier = Modifier.weight(1f).height(64.dp)) {
            Icon(if (state.running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
            Text(
                stringResource(
                    when {
                        state.running -> R.string.action_pause
                        state.active -> R.string.action_resume
                        else -> R.string.start
                    }
                ),
                Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AlertRow(s: AppSettings, onPickInterval: () -> Unit, onAlertOn: (Boolean) -> Unit, modifier: Modifier) {
    Card(
        onClick = onPickInterval,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alert_every), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                formatClock(s.stopwatchAlertSec * 1_000L),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (s.stopwatchAlertOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(checked = s.stopwatchAlertOn, onCheckedChange = onAlertOn, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun Laps(laps: List<LapRow>, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(laps, key = { it.number }) { lap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lap_n, lap.number),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    formatStopwatch(lap.splitMs),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Text(formatStopwatch(lap.totalMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
    }
}
