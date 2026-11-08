package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.GamePanelOverlay
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelKeyboardTray

/** The game panel's card and its keyboard tray: the one scope that reads the open panel, so it alone recomposes when it opens. */
@Composable
internal fun PanelOverlayAndTray(
    panel: () -> OpenPanel?,
    onAction: (Action) -> Unit,
    onKeyboardEvent: (KeyboardEvent) -> Unit,
    below: Dp,
    slide: Animatable<Float, AnimationVector1D>,
) {
    val open = panel()
    GamePanelOverlay(open, onAction, keyboardCovers = PlumTheme.layout.keyboardTrayHeight - below, progress = slide)
    // A panel's field typed with Plumemu's keyboard: its tray over the panel, the same place as Search's.
    PanelKeyboardTray(open, onKeyboardEvent, belowContent = below)
}

/**
 * Reads the open panel in its own scope for the hint bar, whose keys change with it. Opening, they change at once; closing,
 * a frame after the press (every key's text is measured again), so the panel's slide starts in a frame of its own.
 */
@Composable
internal fun HintsOf(
    panel: () -> OpenPanel?,
    hints: @Composable (OpenPanel?) -> Unit,
) {
    val now = panel()
    var shown by remember { mutableStateOf(now) }
    LaunchedEffect(now) { shown = now }
    hints(now ?: shown)
}
