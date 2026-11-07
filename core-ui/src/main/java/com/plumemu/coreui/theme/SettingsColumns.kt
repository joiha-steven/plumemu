package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * Where Settings keeps its three columns on a wide screen (docs/design/rules.md › Settings): the sections column is a fixed
 * 212.5 dp, so a section label ("Box art & accounts") stays whole on one line only up to some text size, and a wide font
 * (Mono, Serif) fits less. Pinned by `SettingsColumnsFitTest` in English, Vietnamese, French and Spanish.
 */
object SettingsColumns {
    /** Largest text size at which three columns fit, by font; null where not even the smallest does. */
    private val threeUpTo: Map<PlumFont, TextSize?> =
        mapOf(
            PlumFont.Jost to TextSize.Large,
            PlumFont.Sans to TextSize.Small,
            PlumFont.Serif to null,
            PlumFont.Mono to null,
        )

    /** True where three columns fit at [size] in [font]; the width class decides separately ([PlumLayout.isSettingsThreeColumns]). */
    fun fits(
        size: TextSize,
        font: PlumFont,
    ): Boolean = threeUpTo[font]?.let { size.ordinal <= it.ordinal } ?: false
}

/** Settings in three columns (the board's 768 dp measurements). */
object SettingsThreeDimens {
    /**
     * The sections column up to its divider. Sized from the longest label: every section's label starts at
     * [LeftColumnDimens.sectionLabelStart] (the slab's left edge 16 + 23.33, after the accent bar), and keeps [LeftColumnDimens.systemLabelEndClear] clear of the divider; the widest label measured in any
     * language at the largest size that keeps three columns is 165 dp (English "Box art & accounts" in Jost at 1.2), so the column is 212.5 with the label start and the clear.
     * `SettingsSectionsFitLabelsTest` measures every label and fails if this is too small.
     */
    val sectionsWidth = 212.5.dp

    /** The most of an options row a value takes: under half, so "ScreenScraper" stays on one line beside "Signed in as …" at 768. */
    const val VALUE_SHARE = 0.47f

    /** The divider to the options column, and the options to the right column. */
    val optionsGap = 10.dp
    val panelGap = 18.dp

    /** The right column, which ends at the page margin. */
    val panelWidth = 205.33.dp
}
