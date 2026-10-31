package com.plumemu.settings

import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.hintsFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** A confirms and B goes back out of the box; with "Confirm with B" on they swap, on every hint. */
class ButtonLettersTest {
    @Test
    fun lettersFollowTheConfirmButton() {
        assertEquals(ButtonLetters(R.string.home_key_a, R.string.home_key_b), ButtonLetters.of(ConfirmButtonSetting.Right))
        assertEquals(ButtonLetters(R.string.home_key_b, R.string.home_key_a), ButtonLetters.of(ConfirmButtonSetting.Bottom))
    }

    @Test
    fun theMainScreensHintsTakeTheirLettersFromTheSetting() {
        val content = FakeHomeContent.board()
        val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]
        val swapped = ButtonLetters.of(ConfirmButtonSetting.Bottom)

        val games = hintsFor(content, Column.Games, snes, letters = swapped).associate { it.action to it.key }
        assertEquals(R.string.home_key_b, games[Action.Play])
        assertEquals(R.string.home_key_a, games[Action.Back])
        val systems = hintsFor(content, Column.Systems, snes, letters = swapped).associate { it.action to it.key }
        assertEquals(R.string.home_key_b, systems[Action.Enter])

        val byDefault = hintsFor(content, Column.Games, snes).associate { it.action to it.key }
        assertEquals(R.string.home_key_a, byDefault[Action.Play])
        assertEquals(R.string.home_key_b, byDefault[Action.Back])
    }
}
