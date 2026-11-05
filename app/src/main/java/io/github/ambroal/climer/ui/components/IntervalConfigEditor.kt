package io.github.ambroal.climer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.settings.DefaultPhaseColors
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.interval.MAX_CYCLES
import io.github.ambroal.climer.domain.interval.MAX_ROUNDS
import io.github.ambroal.climer.timer.PhaseType

/**
 * Fasi facoltative: [absent] è il valore che equivale a "fase non usata",
 * [added] il valore proposto quando la si aggiunge.
 */
private enum class Field(
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val dot: Color,
    val isCount: Boolean,
    val absent: Int,
    val added: Int
) {
    PREPARE(R.string.phase_prepare, R.string.prepare_hint, phaseDot(PhaseType.PREPARE), false, 0, 10),
    WORK(R.string.phase_work, R.string.work_hint, phaseDot(PhaseType.WORK), false, 0, 30),
    REST(R.string.phase_rest, R.string.rest_hint, phaseDot(PhaseType.REST), false, 0, 10),
    ROUNDS(R.string.rounds, R.string.rounds_hint, Color(0xFF34AADC), true, 1, 5),
    CYCLES(R.string.cycles, R.string.cycles_hint, Color(0xFFFF9500), true, 1, 2),
    CYCLE_REST(R.string.phase_cycle_rest, R.string.cycle_rest_hint, phaseDot(PhaseType.CYCLE_REST), false, 0, 60),
    COOLDOWN(R.string.phase_cooldown, R.string.cooldown_hint, phaseDot(PhaseType.COOLDOWN), false, 0, 60);

    fun get(c: IntervalConfig): Int = when (this) {
        PREPARE -> c.prepareSec
        WORK -> c.workSec
        REST -> c.restSec
        ROUNDS -> c.rounds
        CYCLES -> c.cycles
        CYCLE_REST -> c.restBetweenCyclesSec
        COOLDOWN -> c.cooldownSec
    }

    fun set(c: IntervalConfig, v: Int): IntervalConfig = when (this) {
        PREPARE -> c.copy(prepareSec = v)
        WORK -> c.copy(workSec = v.coerceAtLeast(1))
        REST -> c.copy(restSec = v)
        ROUNDS -> c.copy(rounds = v)
        CYCLES -> c.copy(cycles = v)
        CYCLE_REST -> c.copy(restBetweenCyclesSec = v)
        COOLDOWN -> c.copy(cooldownSec = v)
    }
}

private fun phaseDot(type: PhaseType) = Color(DefaultPhaseColors.getValue(type))

private val DeleteRed = Color(0xFFE53935)

private fun secondsLabel(s: Int): String = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else ":%02d".format(s)

/**
 * Editor delle fasi: "Lavora" c'è sempre, le altre si aggiungono con "+" e si tolgono trascinandole verso destra.
 * Una fase resta visibile anche se le dai il valore "vuoto", finché non la togli.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalConfigEditor(config: IntervalConfig, onChange: (IntervalConfig) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf<Field?>(null) }
    var shown by rememberSaveable { mutableStateOf(emptyList<Field>()) }
    val visible = Field.entries.filter { it == Field.WORK || it.get(config) != it.absent || it in shown }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        visible.forEach { field ->
            val value = field.get(config)
            // Chiave per fase: lo stato di trascinamento resta legato alla sua riga quando le altre spariscono.
            key(field) {
                ConfigRow(
                    field = field,
                    value = if (field.isCount) value.toString() else secondsLabel(value),
                    onClick = { editing = field },
                    onRemove = if (field == Field.WORK) {
                        null
                    } else {
                        {
                            shown = shown - field
                            onChange(field.set(config, field.absent))
                        }
                    }
                )
            }
        }
        val addable = Field.entries.filter { it !in visible }
        if (addable.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                addable.forEach { field ->
                    AssistChip(
                        onClick = {
                            shown = shown + field
                            onChange(field.set(config, field.added))
                            editing = field
                        },
                        label = { Text(stringResource(field.title)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }

    val field = editing ?: return
    val close = { editing = null }
    val title = stringResource(field.title)
    when (field) {
        Field.ROUNDS -> CountPickerDialog(title, config.rounds, 1, MAX_ROUNDS, close) { onChange(field.set(config, it)) }
        Field.CYCLES -> CountPickerDialog(title, config.cycles, 1, MAX_CYCLES, close) { onChange(field.set(config, it)) }
        else -> DurationPickerDialog(title, field.get(config), close) { onChange(field.set(config, it)) }
    }
}

@Composable
private fun ConfigRow(field: Field, value: String, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    if (onRemove == null) {
        ConfigCard(field, value, onClick)
        return
    }
    // Trascinando verso destra compare "Elimina": al rilascio oltre la soglia la fase viene tolta.
    // Soglia al 75% della larghezza: con la metà si eliminava troppo facilmente.
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.75f })
    val removeLabel = stringResource(R.string.remove_phase, stringResource(field.title))
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        // Chiamato al rilascio e solo oltre la soglia: currentValue invece cambia già a metà trascinamento.
        onDismiss = { if (it == SwipeToDismissBoxValue.StartToEnd) onRemove() },
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(removeLabel) {
                    onRemove()
                    true
                }
            )
        },
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(CardDefaults.shape).background(DeleteRed).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    ) { ConfigCard(field, value, onClick) }
}

@Composable
private fun ConfigCard(field: Field, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(field.dot))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(field.title).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(
                    stringResource(field.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
