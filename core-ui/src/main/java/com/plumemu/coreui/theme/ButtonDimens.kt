package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/** [com.plumemu.coreui.components.PlumButton]: the cap's height is `PlumTheme.layout.setupButtonHeight`, the collar is added outside it. */
object ButtonDimens {
    /** Between the cap's edge and its label (board: 30 px at 1.5x). */
    val padding = 20.dp

    /** Above and below a label that wraps to two lines, inside the cap. */
    val wrapPadding = 4.dp

    /** The least gap between a sign-in field and the drawn collar of the button under it while Plumemu's tray is up (the collar then hangs over the gap). */
    val signInCollarFloor = 4.dp
}
