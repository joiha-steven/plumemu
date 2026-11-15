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
        // B Back is hidden on the games list; the grid's bar still says Back, with the swapped letter
        assertEquals(null, games[Action.Back])
        val grid = hintsFor(content, Column.Games, snes, letters = swapped, showsGrid = true).associate { it.action to it.key }
        assertEquals(R.string.home_key_a, grid[Action.Back])
        val systems = hintsFor(content, Column.Systems, snes, letters = swapped).associate { it.action to it.key }
        assertEquals(R.string.home_key_b, systems[Action.Enter])

        val byDefault = hintsFor(content, Column.Games, snes).associate { it.action to it.key }
        assertEquals(R.string.home_key_a, byDefault[Action.Play])
        assertEquals(null, byDefault[Action.Back])
        assertEquals(R.string.home_key_b, hintsFor(content, Column.Games, snes, showsGrid = true).associate { it.action to it.key }[Action.Back])
    }
}
