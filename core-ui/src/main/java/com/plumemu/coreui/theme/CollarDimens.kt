package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * The collar around a touchable part (docs/design/rules.md › Collar): the lip, then the groove, outside the part, more at the
 * bottom. A control (button, switch, slider, segment, swatch, field), a small key (hint, keyboard) and a box cover.
 */
object CollarDimens {
    val lip = (16f / 3f).dp * Depth.LIP
    val lipBottom = 6.dp * Depth.LIP
    val groove = (4f / 3f).dp
    val grooveBottom = 2.dp
    // Swatch, choice mark, small key (board: lip 4 px, groove 2 px, 5.3 dp under the cap at 1.5 px per dp).
    val keyLip = (8f / 3f).dp * Depth.LIP
    val keyLipBottom = (10f / 3f).dp * Depth.LIP
    val keyGroove = (4f / 3f).dp
    val keyGrooveBottom = 2.dp

    // Hint key (board: 44 x 43 px: lip 4 px, groove 1 px).
    val hintLip = (8f / 3f).dp * Depth.LIP
    val hintLipBottom = (10f / 3f).dp * Depth.LIP
    val hintGroove = (2f / 3f).dp
    val hintGrooveBottom = (4f / 3f).dp

    // Segmented tray (board: lip 6 px, 7 px under; the well inside is the tray's own padding).
    val segmentLip = 4.dp * Depth.LIP
    val segmentLipBottom = (14f / 3f).dp * Depth.LIP
    val segmentGroove = (4f / 3f).dp
    val segmentGrooveBottom = 2.dp

    // Text field (board: lip 6 px, groove 2 px, 10 px under the well).
    val fieldLip = 4.dp * Depth.LIP
    val fieldLipBottom = (14f / 3f).dp * Depth.LIP
    val fieldGroove = (4f / 3f).dp
    val fieldGrooveBottom = 2.dp
    // The Covers board (1.5 px per dp): lip 4 px (5 at the bottom), groove 2 px.
    val coverLip = (8f / 3f).dp
    val coverLipBottom = (10f / 3f).dp
    val coverGroove = (4f / 3f).dp
    val coverGrooveBottom = (4f / 3f).dp

    /** The box's own corner inside a grid cover's collar (the board's 4 px). */
    val coverBoxCorner = (8f / 3f).dp

    /** The cover lip's corner past its groove's: the board's 9 px lip round a 6 px groove, 3 px = 2 dp (not the 4 px lip, so 6 dp outside, not 6.67). */
    val coverLipCornerAdd = 2.dp

    /** The box's own drop shadow in its collar (the board's `0 2px 0` hard one, `0 3px 4px` soft one: sigma 1.33 dp). */
    val boxShadowHardDrop = (4f / 3f).dp * Depth.THICKNESS
    val boxShadowSoftDrop = 2.dp * Depth.SHADOW_REACH
    val boxShadowSoftSigma = (4f / 3f).dp * Depth.SHADOW_REACH

    /** The crest: the lighter hairline along the lip's top edge (the board's 2 px at 1.5x dp, 1.33 dp). */
    val crest = (4f / 9f).dp

    /** The drop shadow: how far wider it grows over its layers, and how far its widest layer sits lower. */
    val shadowReach = 8.dp * Depth.SHADOW_REACH
    val shadowDrop = 4.dp * Depth.SHADOW_REACH

    /** The light themes' soft drop shadow (board: `0 10px 22px`, a Gaussian of sigma 11 px = 7.3 dp, 6.7 dp lower), drawn as [SOFT_SHADOW_LAYERS] stacked rounded rects. */
    val softShadowSigma = (22f / 3f).dp * Depth.SHADOW_REACH
    val softShadowDrop = (20f / 3f).dp * Depth.SHADOW_REACH
    const val SOFT_SHADOW_LAYERS = 16

    /** Strength left (share of the Gaussian's) at the outermost soft-shadow layer's edge: puts the shadow's visible reach at the board's 8.7 dp (measured 9.4 on buttons, 8.0 on the tray: pixel steps). */
    const val SOFT_SHADOW_OUTER_LEVEL = 0.09f

    /** The focus glow of a part (board: `0 0 22px` at 0.3 around a collar, sigma 7.3 dp; `0 0 14px` around a slider knob, sigma 4.7 dp), drawn as [GLOW_BANDS] bands out to [GLOW_REACH_SIGMAS] sigmas. */
    val glowSigma = (22f / 3f).dp * Depth.GLOW_REACH
    val knobGlowSigma = (14f / 3f).dp * Depth.GLOW_REACH

    /** The focus glow as CSS blurs: `0 0 22px` around a lit collar, `0 0 12px` around its lit groove (1.5 px per dp). */
    val glowBlur = (44f / 3f).dp * Depth.GLOW_REACH
    val grooveGlowBlur = 8.dp * Depth.GLOW_REACH

    /** Around a focused slider's track on a light theme the board's glow reaches 11-13 dp, a wider blur than the knob's: 7.8 dp puts the reach within 1 dp of it. */
    val sliderLightGlowSigma = 7.8.dp * Depth.GLOW_REACH
    const val GLOW_BANDS = 8

    /** The haze round a focused cover on a light theme: accent at 0.4, sigma 11 dp (peak about 35 of 255 at the edge, reach about 20 dp, as the board). */
    val coverLightGlowSigma = 11.dp
    const val COVER_LIGHT_GLOW_ALPHA = 0.4f
    const val GLOW_REACH_SIGMAS = 2.2f
}
