package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Sizes of the V2 switch and slider (the board's 1.5x px divided by 1.5; docs/design/rules.md). */
object SwitchSliderDimens {
    /** The hue track's stops: the board's softened spectrum, hues at matched lightness (coral, amber, green, teal, blue, violet, back to coral). */
    val hueStops = listOf(Color(0xFFE5484D), Color(0xFFF2B630), Color(0xFF5FD38D), Color(0xFF3CC4C4), Color(0xFF3E7BE0), Color(0xFFB583E8), Color(0xFFE5484D))

    val switchTrackWidth = 40.dp
    val switchTrackHeight = (68f / 3f).dp
    val switchKnob = (56f / 3f).dp
    /** The enabled knob sits this far above the track's centre line: half its solid edge (`SeatLook.cap` thick, 2 dp), so body plus edge is centred. */
    val switchKnobLift = 1.dp
    val switchLip = 4.dp
    val switchLipBottom = (14f / 3f).dp

    /** The focus ring drawn on the outside of the lip (the board's 2 px). */
    val switchFocusRing = (4f / 3f).dp



    val sliderTrack = 8.dp
    val sliderLip = (16f / 3f).dp
    /** The lip above the track (the board's `padding: 7px 8px 8px`). */
    val sliderLipTop = (14f / 3f).dp
    /** The lip's corner (16 px). */
    val sliderLipCorner = (32f / 3f).dp
    val sliderLipBottom = (16f / 3f).dp

    val sliderKnob = 20.dp
    /** The fill's `0 0 10px` glow and the focused knob's `0 0 14px` (1.5 px per dp). */
    val fillGlowBlur = (20f / 3f).dp
    val knobGlowBlur = (28f / 3f).dp

    /** The knob's groove ring (lit in the accent when focused). */
    val sliderKnobRing = (4f / 3f).dp
    val sliderDefaultWidth = 200.dp

    val bubbleWidth = 32.dp
    val bubbleHeight = 20.dp
    val bubbleCorner = 6.7.dp

    /** From the top of the knob's ring to the bubble's bottom. */
    val bubbleGap = 6.dp
}
