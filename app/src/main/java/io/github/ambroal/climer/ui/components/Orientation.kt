package io.github.ambroal.climer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/** Finestra più larga che alta: le schermate a tutta altezza passano a una disposizione su due colonne. */
@Composable
fun isLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}
