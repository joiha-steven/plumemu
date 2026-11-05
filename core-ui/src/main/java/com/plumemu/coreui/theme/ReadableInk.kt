package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Black or white, whichever reads better on [fill] (a check on a swatch). */
internal fun readableInk(fill: Color): Color {
    val rgb = Rgb(fill.toArgb() and 0xFFFFFF)
    val onBlack = ColorMath.contrast(rgb, Rgb(0x000000))
    val onWhite = ColorMath.contrast(rgb, Rgb(0xFFFFFF))
    return if (onBlack >= onWhite) Color.Black else Color.White
}
