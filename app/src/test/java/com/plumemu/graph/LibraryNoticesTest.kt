package com.plumemu.graph

import com.plumemu.coredata.db.AddedSession
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.library.scan.ScanStats
import com.plumemu.library.scan.UnknownFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a change check tells the home: nothing for no change, a notice and dots for new games, a notice alone for removals. */
class LibraryNoticesTest {
    private fun stats(
        added: Map<String, Int> = emptyMap(),
        removed: Map<String, Int> = emptyMap(),
    ) = ScanStats(1, 0, 10, added.values.sum(), 0, removed.values.sum(), 0, 0, 0, added, removed)

    @Test
    fun noNewAndNoRemovedGamesMeansNoNotice() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(), stats()))
        assertNull(notices.notice.value)
        assertTrue(notices.newGameSystems.value.isEmpty())
    }

    @Test
    fun newGamesInOneSystemGiveItsNameAndADot() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 24))))
        val notice = notices.notice.value!!
        assertEquals(24, notice.added)
        assertEquals(0, notice.removed)
        assertEquals(listOf("n64"), notice.addedSystems)
        assertEquals(setOf("n64"), notices.newGameSystems.value)
    }

    @Test
    fun severalSystemsAndSeveralFoldersAreSummedAndEveryOneGetsADot() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 20, "snes" to 2)), stats(added = mapOf("snes" to 2, "gba" to 1))))
        val notice = notices.notice.value!!
        assertEquals(25, notice.added)
        assertEquals(listOf("n64", "snes", "gba"), notice.addedSystems)
        assertEquals(setOf("n64", "snes", "gba"), notices.newGameSystems.value)
    }

    @Test
    fun anEmulatorClosedAtOnceIsKeptUntilTheHomeShowsItAndTwoInARowAreTwo() {
        val notices = LibraryNotices()
        assertNull(notices.closedAtOnce.value)
        notices.emulatorClosedAtOnce("7", "Chrono Trigger", "Snes9x")
        val first = notices.closedAtOnce.value!!
        assertEquals("Snes9x", first.emulatorName)
        notices.emulatorClosedAtOnce("7", "Chrono Trigger", "Snes9x")
        val second = notices.closedAtOnce.value!!
        notices.closedAtOnceShown(first)
        assertEquals("an old message's answer does not clear a newer one", second, notices.closedAtOnce.value)
        notices.closedAtOnceShown(second)
        assertNull(notices.closedAtOnce.value)
    }

    @Test
    fun aSecondCoreQuestionWaitsForTheFirstAndKeepsItsSessionToTakeBack() {
        val notices = LibraryNotices()
        val core = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
        val firstPlay = AddedSession(sessionId = 1, gameId = 7, endedAt = 1, seconds = 60, previousLastPlayedAt = null)
        val secondPlay = AddedSession(sessionId = 2, gameId = 8, endedAt = 2, seconds = 60, previousLastPlayedAt = null)
        notices.askAboutCore("7", "Zork", "retroarch-mesen", "RetroArch: Mesen", core, firstPlay)
        notices.askAboutCore("8", "Quest", "retroarch-mesen", "RetroArch: Mesen", core, secondPlay)

        val first = notices.coreQuestion.value!!
        assertEquals("the first is not replaced", firstPlay, first.added)
        notices.coreQuestionShown(first)
        val second = notices.coreQuestion.value!!
        assertEquals("the second follows with its own session", secondPlay, second.added)
        notices.coreQuestionShown(first)
        assertEquals("an old answer does not skip the waiting one", second, notices.coreQuestion.value)
        notices.coreQuestionShown(second)
        assertNull(notices.coreQuestion.value)
    }

    @Test
    fun questionsForTheSameGameAndCoreWaitingToBeShownAreOneAndOtherGamesOrCoresStaySeparate() {
        val core = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
        fun play(id: Long) = AddedSession(sessionId = id, gameId = 7, endedAt = id, seconds = 60, previousLastPlayedAt = null)
        val notices = LibraryNotices()
        fun ask(game: String, c: CoreToConfirm, id: Long) =
            notices.askAboutCore(game, "Zork", "retroarch-mesen", "RetroArch: Mesen", c, play(id), PlaySessionOutcome.Recorded(7, 60, "retroarch-mesen", c, play(id)))
        ask("7", core, 1)
        ask("7", core, 2) // joins the one on screen's turn: not yet shown
        ask("8", core, 3)
        ask("7", core.copy(core = "other_libretro_android.so"), 4)
        ask("7", core, 5) // the first is still waiting to be shown, so this joins it too
        val first = notices.coreQuestion.value!!
        assertEquals(listOf(1L, 2L, 5L), first.plays.map { it.sessionId })
        notices.coreQuestionShown(first)
        assertEquals("the other game", "8", notices.coreQuestion.value!!.gameKey)
        notices.coreQuestionShown(notices.coreQuestion.value!!)
        assertEquals("the other core", "other_libretro_android.so", notices.coreQuestion.value!!.core.core)
        notices.coreQuestionShown(notices.coreQuestion.value!!)
        assertNull("nothing for the same game and core again", notices.coreQuestion.value)
        assertEquals("Yes or B sends every collapsed play", listOf(1L, 2L, 5L), notices.releaseHeldSession(first).map { it.added!!.sessionId })
        assertEquals("and only once", emptyList<Any>(), notices.releaseHeldSession(first))
    }

    @Test
    fun noDropsEveryCollapsedPlayAndAHeldPlayIsNeverReleasedOffScreenBeforeItsQuestion() {
        val core = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
        fun play(id: Long) = AddedSession(sessionId = id, gameId = 7, endedAt = id, seconds = 60, previousLastPlayedAt = null)
        val notices = LibraryNotices()
        for (id in 1L..2L) {
            notices.askAboutCore("7", "Zork", "retroarch-mesen", "RetroArch: Mesen", core, play(id), PlaySessionOutcome.Recorded(7, 60, "retroarch-mesen", core, play(id)))
            // A home made again right after the post finds the play still asked about.
            assertEquals(emptyList<Any>(), notices.releaseHeldOffScreen())
        }
        val question = notices.coreQuestion.value!!
        notices.coreQuestionShown(question)
        question.plays.forEach(notices::dropHeldSession) // No takes both back
        assertEquals(emptyList<Any>(), notices.releaseHeldSession(question))
        assertEquals(emptyList<Any>(), notices.releaseHeldOffScreen())
    }

    @Test
    fun aHeldSessionIsReleasedOnceAndNotAfterADrop() {
        val notices = LibraryNotices()
        val core = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
        val play = AddedSession(sessionId = 3, gameId = 7, endedAt = 1, seconds = 60, previousLastPlayedAt = null)
        val recorded = PlaySessionOutcome.Recorded(7, 60, "retroarch-mesen", core, play)
        notices.askAboutCore("7", "Zork", "retroarch-mesen", "RetroArch: Mesen", core, play)
        val question = notices.coreQuestion.value!!
        notices.holdSession(play, recorded)
        assertEquals(listOf(recorded), notices.releaseHeldSession(question))
        assertEquals("once", emptyList<Any>(), notices.releaseHeldSession(question))
        notices.holdSession(play, recorded)
        notices.dropHeldSession(play)
        assertEquals("No dropped it", emptyList<Any>(), notices.releaseHeldSession(question))
    }

    @Test
    fun aHomeMadeAgainReleasesThePlaysOfQuestionsAlreadyShownOnceAndKeepsTheWaitingOnes() {
        val notices = LibraryNotices()
        val core = CoreToConfirm("com.retroarch", "mesen_libretro_android.so")
        fun play(id: Long) = AddedSession(sessionId = id, gameId = 7, endedAt = id, seconds = 60, previousLastPlayedAt = null)
        fun recorded(id: Long) = PlaySessionOutcome.Recorded(7, 60, "retroarch-mesen", core, play(id))
        listOf(1L, 2L, 3L).forEach { id ->
            notices.holdSession(play(id), recorded(id))
            notices.askAboutCore("$id", "Zork $id", "retroarch-mesen", "RetroArch: Mesen", core, play(id))
        }
        val shown = notices.coreQuestion.value!!
        notices.coreQuestionShown(shown) // on screen when the Activity was made again

        assertEquals("the shown one counts as B", listOf(recorded(1)), notices.releaseHeldOffScreen())
        assertEquals("once", emptyList<PlaySessionOutcome.Recorded>(), notices.releaseHeldOffScreen())
        assertEquals("its question's over finds nothing more", emptyList<Any>(), notices.releaseHeldSession(shown))
        val next = notices.coreQuestion.value!!
        assertEquals("the waiting ones keep theirs", listOf(recorded(2)), notices.releaseHeldSession(next))
    }

    @Test
    fun removalsGiveANoticeButNoDot() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(removed = mapOf("snes" to 3))))
        val notice = notices.notice.value!!
        assertEquals(0, notice.added)
        assertEquals(3, notice.removed)
        assertTrue(notice.addedSystems.isEmpty())
        assertTrue(notices.newGameSystems.value.isEmpty())
    }

    @Test
    fun theDotGoesWhenTheSystemIsOpenedAndOthersStay() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 1, "snes" to 1))))
        notices.opened("n64")
        assertEquals(setOf("snes"), notices.newGameSystems.value)
        notices.opened("not-there")
        assertEquals(setOf("snes"), notices.newGameSystems.value)
    }

    @Test
    fun theNoticeIsClearedOnceShownAndASecondEqualOneIsANewNotice() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 1))))
        val first = notices.notice.value!!
        notices.noticeShown(first)
        assertNull(notices.notice.value)
        notices.checked(listOf(stats(added = mapOf("n64" to 1))))
        assertNotEquals(first.serial, notices.notice.value!!.serial)
        // A late "shown" for the earlier notice must not clear the later one.
        notices.noticeShown(first)
        assertEquals(1, notices.notice.value!!.added)
    }

    private fun unknown(vararg names: String) = ScanStats(1, 0, 10, 0, 0, 0, 0, 0, 0, newUnknownFolders = names.map { UnknownFolder(it, 3) })

    @Test
    fun anUnknownFolderGivesANoticeWithItsNameAndNoDot() {
        val notices = LibraryNotices()
        notices.checked(listOf(unknown("Xbox 360")))
        val notice = notices.notice.value!!
        assertEquals(listOf("Xbox 360"), notice.unknownFolders)
        assertEquals(0, notice.added)
        assertTrue(notices.newGameSystems.value.isEmpty())
    }

    @Test
    fun severalUnknownFoldersOfSeveralFoldersAreOneNoticeWithoutRepeats() {
        val notices = LibraryNotices()
        notices.checked(listOf(unknown("Xbox 360", "Wii U"), unknown("Xbox 360")))
        assertEquals(listOf("Xbox 360", "Wii U"), notices.notice.value!!.unknownFolders)
    }

    @Test
    fun unknownFoldersFoundWithNewGamesAreSaidAfterTheGamesNotice() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 2)), unknown("Xbox 360")))
        val games = notices.notice.value!!
        assertEquals(2, games.added)
        assertTrue(games.unknownFolders.isEmpty())
        notices.noticeShown(games)
        val next = notices.notice.value!!
        assertEquals(listOf("Xbox 360"), next.unknownFolders)
        assertNotEquals(games.serial, next.serial)
        notices.noticeShown(next)
        assertNull(notices.notice.value)
    }

    @Test
    fun anUnknownOnlyNoticeDoesNotReplaceAGamesNoticeStillUp() {
        val notices = LibraryNotices()
        notices.checked(listOf(stats(added = mapOf("n64" to 30))))
        val games = notices.notice.value!!
        notices.checked(listOf(unknown("Xbox 360")))
        assertEquals(games, notices.notice.value)
        notices.noticeShown(games)
        assertEquals(listOf("Xbox 360"), notices.notice.value!!.unknownFolders)
    }

    @Test
    fun aGamesNoticeWinsOverAnUnknownOnlyOneAndKeepsItsFoldersForAfter() {
        val notices = LibraryNotices()
        notices.checked(listOf(unknown("Xbox 360")))
        notices.checked(listOf(stats(added = mapOf("n64" to 2))))
        val games = notices.notice.value!!
        assertEquals(2, games.added)
        notices.noticeShown(games)
        assertEquals(listOf("Xbox 360"), notices.notice.value!!.unknownFolders)
    }
}
