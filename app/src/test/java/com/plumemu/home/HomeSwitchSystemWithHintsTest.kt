package com.plumemu.home

import com.plumemu.R
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coreinput.Action
import com.plumemu.settings.ShoulderKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The roles and key caps of the two Switch system with choices, without a screen. */
class HomeSwitchSystemWithHintsTest {
    private val content = FakeHomeContent.withArtwork()
    private val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]

    private fun gridHints(setting: SwitchSystemWithSetting) =
        hintsFor(content, com.plumemu.coreinput.Column.Games, snes, showsGrid = true, shoulders = ShoulderKeys.of(setting))

    private fun listHints(setting: SwitchSystemWithSetting) =
        hintsFor(content, com.plumemu.coreinput.Column.Games, snes, showsGrid = false, shoulders = ShoulderKeys.of(setting))

    @Test
    fun theDefaultIsL2R2ForTheSystemAndL1R1ForSize() {
        assertFalse(ShoulderKeys.Default.bumpersSwitch)
        assertEquals(SwitchSystemWithSetting.Triggers, com.plumemu.coredata.settings.ControlsSettings().switchSystemWith)
        val hints = gridHints(SwitchSystemWithSetting.Triggers)
        assertEquals(R.string.home_key_l2_r2, hints.single { it.action == Action.JumpLetterNext }.key)
        assertEquals(R.string.home_key_l1_r1, hints.single { it.cyclesGridSize }.key)
        assertEquals(hints, hintsFor(content, com.plumemu.coreinput.Column.Games, snes, showsGrid = true))
    }

    @Test
    fun withL1R1TheHintsTradeTheirKeyCaps() {
        val grid = gridHints(SwitchSystemWithSetting.Bumpers)
        val switch = grid.single { it.action == Action.JumpLetterNext }
        assertEquals(R.string.home_key_l1_r1, switch.key)
        assertEquals("a tap sends L1 / R1's name, which the screen reads as the next system", Action.NextSystem, switch.tapAction)
        assertEquals(R.string.home_key_l2_r2, grid.single { it.cyclesGridSize }.key)
        assertEquals(R.string.home_key_l1_r1, listHints(SwitchSystemWithSetting.Bumpers).single { it.action == Action.JumpLetterNext }.key)
    }

    @Test
    fun theDefaultTapKeepsTheOldAction() {
        val switch = listHints(SwitchSystemWithSetting.Triggers).single { it.action == Action.JumpLetterNext }
        assertEquals(Action.JumpLetterNext, switch.tapAction)
    }

    @Test
    fun theMappingTradesBothPairsAndLeavesOtherActionsAlone() {
        val default = ShoulderKeys.of(SwitchSystemWithSetting.Triggers)
        val swapped = ShoulderKeys.of(SwitchSystemWithSetting.Bumpers)
        val pairs = listOf(Action.PreviousSystem, Action.NextSystem, Action.JumpLetterPrevious, Action.JumpLetterNext)
        pairs.forEach { assertEquals(it, default.read(it)) }
        assertEquals(Action.JumpLetterPrevious, swapped.read(Action.PreviousSystem))
        assertEquals(Action.JumpLetterNext, swapped.read(Action.NextSystem))
        assertEquals(Action.PreviousSystem, swapped.read(Action.JumpLetterPrevious))
        assertEquals(Action.NextSystem, swapped.read(Action.JumpLetterNext))
        assertTrue(listOf(Action.Enter, Action.Back, Action.Search, Action.ToggleView).all { swapped.read(it) == it })
        pairs.forEach { assertEquals("swapping twice is the identity", it, swapped.read(swapped.read(it))) }
    }
}
