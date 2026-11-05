package io.github.ambroal.climer.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.settings.CueTone
import io.github.ambroal.climer.data.settings.DefaultPhaseColors
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.timer.PhaseType
import io.github.ambroal.climer.timer.phaseNameRes
import io.github.ambroal.climer.ui.components.AppTopBar
import io.github.ambroal.climer.ui.components.ColorChooser
import io.github.ambroal.climer.ui.theme.Palette

@Composable
fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun SwitchRow(@StringRes title: Int, checked: Boolean, @StringRes hint: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAudio: () -> Unit,
    backupSection: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.settings), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onAudio).padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Text(
                    stringResource(R.string.audio_settings),
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider()

            SectionTitle(R.string.general)
            SwitchRow(R.string.keep_screen_on, s.keepScreenOn, R.string.keep_screen_on_hint) { v ->
                viewModel.update { it.copy(keepScreenOn = v) }
            }
            SwitchRow(R.string.allow_rotation, s.allowRotation, R.string.allow_rotation_hint) { v ->
                viewModel.update { it.copy(allowRotation = v) }
            }
            SwitchRow(R.string.open_last_timer, s.openLastTimer, R.string.open_last_timer_hint) { v ->
                viewModel.update { it.copy(openLastTimer = v) }
            }
            SwitchRow(R.string.screen_flash, s.screenFlash, R.string.screen_flash_hint) { v ->
                viewModel.update { it.copy(screenFlash = v) }
            }
            SwitchRow(R.string.soft_colors, s.softColors) { v -> viewModel.update { it.copy(softColors = v) } }

            SectionTitle(R.string.rounding_step)
            Text(
                stringResource(R.string.rounding_step_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundingSteps.forEach { step ->
                    FilterChip(
                        selected = s.roundingStepKg == step,
                        onClick = { viewModel.setRoundingStep(step) },
                        label = { Text(stringResource(R.string.kg_value, formatKg(step))) }
                    )
                }
            }

            SectionTitle(R.string.phase_colors)
            PhaseType.entries.forEach { type ->
                Text(
                    stringResource(phaseNameRes(type)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                ColorChooser(Palette, s.phaseColors[type] ?: DefaultPhaseColors.getValue(type), { viewModel.setPhaseColor(type, it) })
            }
            TextButton(onClick = viewModel::resetPhaseColors) { Text(stringResource(R.string.reset_colors)) }

            backupSection()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.audio_settings), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(R.string.general_volume)
            Slider(value = s.volume, onValueChange = { v -> viewModel.update { it.copy(volume = v) } })

            SectionTitle(R.string.work_sound)
            ToneChooser(s.workTone, viewModel::setWorkTone)
            SectionTitle(R.string.rest_sound)
            ToneChooser(s.restTone, viewModel::setRestTone)

            SectionTitle(R.string.cues_section)
            SwitchRow(R.string.voice_cues, s.voice, R.string.voice_cues_hint) { v -> viewModel.update { it.copy(voice = v) } }
            SwitchRow(R.string.countdown_321, s.countdown321) { v -> viewModel.update { it.copy(countdown321 = v) } }
            SwitchRow(R.string.ten_seconds_left, s.tenSecondsLeft) { v -> viewModel.update { it.copy(tenSecondsLeft = v) } }
            SwitchRow(R.string.halfway, s.halfway) { v -> viewModel.update { it.copy(halfway = v) } }
            SwitchRow(R.string.interval_name, s.intervalName) { v -> viewModel.update { it.copy(intervalName = v) } }
            SwitchRow(R.string.ticking, s.ticking) { v -> viewModel.update { it.copy(ticking = v) } }
            SwitchRow(R.string.interval_sound, s.intervalSound) { v -> viewModel.update { it.copy(intervalSound = v) } }
            SwitchRow(R.string.vibration, s.vibration) { v -> viewModel.update { it.copy(vibration = v) } }
            SwitchRow(R.string.duck_music, s.duckMusic, R.string.duck_music_hint) { v -> viewModel.update { it.copy(duckMusic = v) } }
            Text(
                stringResource(R.string.silent_mode_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneChooser(selected: CueTone, onSelect: (CueTone) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CueTone.entries.forEach { tone ->
            val label = when (tone) {
                CueTone.BEEP -> R.string.tone_beep
                CueTone.PIP -> R.string.tone_pip
                CueTone.CHIME -> R.string.tone_chime
                CueTone.ALARM -> R.string.tone_alarm
            }
            FilterChip(selected = selected == tone, onClick = { onSelect(tone) }, label = { Text(stringResource(label)) })
        }
    }
}
