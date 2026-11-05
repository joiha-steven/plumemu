package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Sizes and shades of the on-screen keyboard's keys: a thin well round a cap (the Search board). */
object KeyboardCollarDimens {
    /** The well's width at the sides and top, and under the cap (where the cap's 2 dp edge sits): the board's 2 / 3 px at 1.5x. */
    val WELL_GROOVE = 1.33.dp
    val WELL_GROOVE_BOTTOM = 2.dp

    /** The cap's corner; with the well round it the key's corner is 8.67 dp (the board's 13 px). */
    val capCorner = 7.33.dp

    /** Where the tray's gradient reaches its own colour (0 top, 1 bottom). */
    const val TRAY_MID = 0.6f

    /** The tray's top: the board's #2A2C31 over a #1C1D21 middle. */
    fun trayTop(tray: Color): Color = lerp(tray, Color.White, 0.03f)

    /** The tray's bottom: the board's #17181B. */
    fun trayBottom(tray: Color): Color = lerp(tray, Color.Black, 0.04f)

    /** The height of the inset shadow along the well's top. */
    val WELL_INSET_BAND = 1.dp

    /** The inset shadow along the well's top: the board's on a dark theme, lighter on a light one where the well is pale. */
    fun wellInset(bg: Color): Color = Color.Black.copy(alpha = if (bg.luminance() < 0.5f) 0.45f else 0.10f)

    /** The hairline of light just under the well. */
    val wellLight = Color.White.copy(alpha = 0.06f)

    /** A letter cap: [FlatFace] with the board's 1 px white .14 highlight on top and a #101114-dark 2 dp edge. */
    fun letterCap(cap: FlatFace): FlatFace {
        // The collar cap's flat face is the board's letter cap (#26282D on #111214), so it is used as it is.
        val face = cap.face
        return FlatFace(face, lerp(face, Color.White, 0.14f), lerp(face, Color.Black, 0.55f), Color.Transparent)
    }

    /** A word or icon cap: a touch lighter than a letter's. */
    fun wordCap(cap: FlatFace): FlatFace = letterCap(FlatFace.of(lerp(cap.face, Color.White, 0.05f), 0f, 0f, 0f))

    /** The action cap: flat accent, a white .4 highlight, an edge half way to black. */
    fun actionCap(accent: Color): FlatFace = FlatFace(accent, lerp(accent, Color.White, 0.4f), lerp(accent, Color.Black, 0.5f), Color.Transparent)
}
