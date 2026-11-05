package io.github.ambroal.climer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.ambroal.climer.R

private fun String.digits(max: Int) = filter { it.isDigit() }.take(max)

private fun selectedAll(text: String) = TextFieldValue(text, TextRange(0, text.length))

/** Campo numerico che, quando riceve il focus, seleziona tutto: scrivere sostituisce il valore invece di accodarsi. */
@Composable
private fun NumberField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    maxDigits: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    // Il tocco che dà il focus sposta poi il cursore dove si è toccato: quel primo spostamento va ignorato.
    var justFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (justFocused && it.text == value.text) {
                justFocused = false
                onValueChange(selectedAll(value.text))
            } else {
                justFocused = false
                onValueChange(it.copy(text = it.text.digits(maxDigits)))
            }
        },
        label = label?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        // La spunta della tastiera conferma come il pulsante OK.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        isError = isError,
        modifier = modifier.onFocusChanged {
            justFocused = it.isFocused
            if (it.isFocused) onValueChange(selectedAll(value.text))
        }
    )
}

@Composable
fun DurationPickerDialog(title: String, seconds: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var min by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(selectedAll((seconds / 60).toString())) }
    var sec by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(selectedAll((seconds % 60).toString())) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = {
        onConfirm((min.text.toIntOrNull() ?: 0) * 60 + (sec.text.toIntOrNull() ?: 0))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(min, { min = it }, 3, Modifier.weight(1f), stringResource(R.string.minutes), onDone = confirm)
                // I secondi sono il campo usato quasi sempre (7, 3, 10): è quello che parte attivo.
                NumberField(sec, {
                    sec = it
                }, 2, Modifier.weight(1f).focusRequester(focus), stringResource(R.string.seconds), onDone = confirm)
            }
        },
        confirmButton = {
            TextButton(onClick = confirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun CountPickerDialog(title: String, value: Int, min: Int, max: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(selectedAll(value.toString())) }
    val parsed = text.text.toIntOrNull()
    val valid = parsed != null && parsed in min..max
    val confirm = {
        if (valid) {
            onConfirm(parsed ?: min)
            onDismiss()
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NumberField(text, { text = it }, 3, Modifier.focusRequester(focus), isError = !valid, onDone = confirm)
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = confirm
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
