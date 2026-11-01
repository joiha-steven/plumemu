package com.plumemu.coreui.theme

/** A panel's edge and shadow alphas (decision 0011, point 5): a faint hairline, a soft shadow lighter on a light theme. */
object PanelLook {
    fun edgeAlpha(darkTheme: Boolean) = if (darkTheme) EDGE_DARK else EDGE_LIGHT

    fun shadowAlpha(darkTheme: Boolean) = if (darkTheme) SHADOW_DARK else SHADOW_LIGHT

    const val EDGE_DARK = 0.16f
    const val EDGE_LIGHT = 0.14f
    const val SHADOW_DARK = 0.45f
    const val SHADOW_LIGHT = 0.14f
}
