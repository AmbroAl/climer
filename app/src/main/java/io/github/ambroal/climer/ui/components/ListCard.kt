package io.github.ambroal.climer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ambroal.climer.R

data class MenuAction(val label: String, val onClick: () -> Unit)

/** Card con barra colorata a sinistra e menu ⋯, lo stile delle liste di Timer Plus. */
@Composable
fun ListCard(
    title: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    subtitle: String? = null,
    menu: List<MenuAction> = emptyList(),
    highlighted: Boolean = false
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(if (highlighted) 8.dp else 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        Text(leading, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box(Modifier.width(8.dp))
                    }
                    Text(title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (menu.isNotEmpty()) OverflowMenu(menu)
        }
    }
}

@Composable
fun OverflowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = {
                    open = false
                    action.onClick()
                })
            }
        }
    }
}
