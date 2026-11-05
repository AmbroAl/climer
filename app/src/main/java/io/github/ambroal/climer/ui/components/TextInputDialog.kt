package io.github.ambroal.climer.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.ambroal.climer.R

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    singleLine: Boolean = true,
    allowBlank: Boolean = false,
    onConfirm: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val canConfirm = allowBlank || text.isNotBlank()
    val confirm = {
        if (canConfirm) {
            onConfirm(text.trim())
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                // Su una riga la spunta della tastiera conferma; sulle note multi-riga va a capo.
                keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { confirm() })
            )
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = confirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
