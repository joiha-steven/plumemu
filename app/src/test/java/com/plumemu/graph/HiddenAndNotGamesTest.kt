package com.plumemu.graph

import com.plumemu.settings.screen.SettingsGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hidden games joins two flows that emit out of step; a game must never be listed twice (the list is keyed by id). */
class HiddenAndNotGamesTest {
    @Test
    fun aGameInBothListsForOneEmissionIsShownOnceAsNotAGame() =
        runBlocking {
            val hidden = MutableStateFlow(listOf(SettingsGame(1, "A", "SNES"), SettingsGame(2, "B", "SNES")))
            val notGames = MutableStateFlow(listOf(SettingsGame(2, "B", "SNES", isNotAGame = true)))
            val joined = hiddenAndNotGames(hidden, notGames).first()
            assertEquals(listOf(1L, 2L), joined.map { it.id })
            assertEquals(true, joined.last().isNotAGame)
        }

    @Test
    fun theNormalCaseKeepsHiddenFirstThenNotAGame() =
        runBlocking {
            val hidden = MutableStateFlow(listOf(SettingsGame(1, "A", "SNES")))
            val notGames = MutableStateFlow(listOf(SettingsGame(3, "C", "SNES", isNotAGame = true)))
            assertEquals(listOf(1L, 3L), hiddenAndNotGames(hidden, notGames).first().map { it.id })
        }
}
