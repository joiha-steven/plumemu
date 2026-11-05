package com.plumemu.coreui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ButtonDimens
import com.plumemu.coreui.theme.ChoiceDimens
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.theme.HintKeyDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SwitchSliderDimens
import org.junit.Assert.assertEquals
import org.junit.Test

/** The outer sizes of the V2 parts, from the board's pixels (1.5 px per dp), each within 0.05 dp. */
class PartSizesBoardTest {
    private fun assertDp(label: String, expected: Float, actual: Dp) = assertEquals(label, expected, actual.value, 0.05f)

    private fun CollarSize.vertical(): Dp = topPadding + bottomPadding

    private fun CollarSize.horizontal(): Dp = sidePadding * 2

    @Test
    fun buttonIs52_7High() = assertDp("primary button outer height (board 79 px)", 52.67f, PlumDimens.setupButtonHeight + CollarSize.Control.vertical())

    @Test
    fun hintKeyIs29_3By28_7() {
        assertDp("hint key width", 29.33f, PlumDimens.keyCapMinWidth + CollarSize.Hint.horizontal())
        assertDp("hint key height", 28.67f, PlumDimens.keyCapHeight + CollarSize.Hint.vertical())
    }

    @Test
    fun swatchIs34_7By36() {
        assertDp("swatch width", 34.67f, ChoiceDimens.swatchSize + CollarSize.Key.horizontal())
        assertDp("swatch height", 36f, ChoiceDimens.swatchSize + CollarSize.Key.vertical())
    }

    @Test
    fun choiceMarkIs24By25_3() {
        assertDp("choice width", 24f, ChoiceDimens.choiceMark + CollarSize.Key.horizontal())
        assertDp("choice height", 25.33f, ChoiceDimens.choiceMark + CollarSize.Key.vertical())
    }

    @Test
    fun segmentedIs45_3High() {
        val tray = ChoiceDimens.segmentTrayPad * 2
        assertDp("segmented height (board 68 px)", 45.33f, ChoiceDimens.segmentHeight + tray + CollarSize.Segment.vertical())
    }

    @Test
    fun textFieldIs49_3High() = assertDp("field height (board 74 px)", 49.33f, FieldDimens.wellHeight + CollarSize.Field.vertical())

    @Test
    fun sliderIs18High() = assertDp("slider outer height (board 27 px)", 18f, SwitchSliderDimens.sliderTrack + SwitchSliderDimens.sliderLipTop + SwitchSliderDimens.sliderLipBottom)

    @Test
    fun buttonAndHintKeyKeepTheBoardsPadding() {
        assertDp("button label padding each side (board 30 px)", 20f, ButtonDimens.padding)
        assertDp("wide hint key label padding each side (board 13 px)", 8.67f, HintKeyDimens.capPadding)
    }

    @Test
    fun partGlowAndLightShadowFollowTheBoardsBlurs() {
        assertDp("collar glow sigma (board 0 0 22px = sigma 11 px)", 7.33f, CollarDimens.glowSigma)
        assertDp("slider knob glow sigma (board 0 0 14px = sigma 7 px)", 4.67f, CollarDimens.knobGlowSigma)
        assertDp("light drop shadow sigma (board 0 10px 22px)", 7.33f, CollarDimens.softShadowSigma)
        assertDp("light drop shadow drop (board 10 px)", 6.67f, CollarDimens.softShadowDrop)
    }
}
