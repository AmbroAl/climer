package io.github.ambroal.climer.ui.measures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DecimalField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError) {
            { Text(stringResource(R.string.invalid_number)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureEntryDialog(
    form: EntryForm,
    unit: String,
    onDate: (LocalDate) -> Unit,
    onValue: (String) -> Unit,
    onRight: (String) -> Unit,
    onLeft: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (form.id == 0L) R.string.new_measure else R.string.edit_measure)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.date_value, formatDate(form.date)))
                }
                if (form.perSide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            form.right,
                            onRight,
                            "${stringResource(R.string.side_right)} ($unit)",
                            form.rightError,
                            Modifier.weight(1f)
                        )
                        DecimalField(
                            form.left,
                            onLeft,
                            "${stringResource(R.string.side_left)} ($unit)",
                            form.leftError,
                            Modifier.weight(1f)
                        )
                    }
                } else {
                    DecimalField(form.value, onValue, "${stringResource(R.string.value)} ($unit)", form.valueError, Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNote,
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = form.canSave) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    if (pickDate) {
        val pickerState =
            rememberDatePickerState(initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(pickerState) }
    }
}
