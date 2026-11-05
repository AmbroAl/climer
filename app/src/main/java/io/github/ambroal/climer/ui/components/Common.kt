package io.github.ambroal.climer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ambroal.climer.R
import io.github.ambroal.climer.ui.theme.Accent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title.uppercase(), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions
    )
}

/** Barra degli editor: X per chiudere a sinistra, spunta verde per salvare a destra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(title: String, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title.uppercase(), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        },
        actions = {
            IconButton(onClick = onSave, enabled = canSave) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSave) Accent else MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save), tint = Color.Black)
                }
            }
        }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ColorChooser(colors: List<Long>, selectedColor: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEachIndexed { i, argb ->
            val isSelected = argb == selectedColor
            val label = stringResource(R.string.color_n, i + 1)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .semantics {
                        contentDescription = label
                        selected = isSelected
                    }
                    .clickable(role = Role.RadioButton) { onSelect(argb) }
            )
        }
    }
}
