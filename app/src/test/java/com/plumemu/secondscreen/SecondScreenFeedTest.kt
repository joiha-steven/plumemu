package com.plumemu.secondscreen

import com.plumemu.secondscreen.SecondScreenFeed.Companion.FILL_WAIT_MS
import com.plumemu.secondscreen.SecondScreenFeed.Companion.SETTLE_MS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settle rule on a test clock: every move reaches the window only once the cursor has rested
 * [SETTLE_MS]; then the play facts are read, and a reset when the window goes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecondScreenFeedTest {
    private fun game(n: Int) = SecondScreenContent.Game(title = "Game $n", systemName = "Super Nintendo", gameId = n.toLong(), isPlayKnown = false)

    private fun played(n: Int) = game(n).copy(isPlayKnown = true, lastPlayedDaysAgo = 1, playTimeMinutes = 30)

    private val warnings = mutableListOf<String>()

    private fun TestScope.feed(facts: (suspend (SecondScreenContent) -> SecondScreenContent?)? = null) =
        SecondScreenFeed(backgroundScope, facts, io = StandardTestDispatcher(testScheduler), warn = { warnings += it })

    /** Runs the clock to [ms] after now and everything due then. */
    private fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun startsCalm() =
        runTest {
            assertEquals(SecondScreenContent.Calm, feed().content.value)
        }

    @Test
    fun aSingleMoveShowsOnlyOnceTheCursorHasRested() =
        runTest {
            val feed = feed()
            feed.offer(game(1))
            runCurrent()
            assertEquals("not during the top screen's slide and glide", SecondScreenContent.Calm, feed.content.value)
            after(SETTLE_MS - 1)
            assertEquals("not yet rested", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals(game(1), feed.content.value)
        }

    @Test
    fun singlePressesFasterThanTheSettleShowOnlyTheLast() =
        runTest {
            val feed = feed()
            feed.offer(game(0))
            after(SETTLE_MS)
            // A walk through the systems: one press every 200 ms, faster than the settle.
            for (n in 1..5) {
                after(200)
                feed.offer(game(n))
                runCurrent()
                assertEquals("press $n waits", game(0), feed.content.value)
            }
            after(SETTLE_MS)
            assertEquals(game(5), feed.content.value)
        }

    @Test
    fun aHeldDirectionWaitsUntilTheCursorRests() =
        runTest {
            val feed = feed()
            // Repeats every 80 ms, as core-input's HoldRepeat sends them.
            for (n in 1..10) {
                feed.offer(game(n))
                after(80)
                assertEquals("move $n waits", SecondScreenContent.Calm, feed.content.value)
            }
            after(SETTLE_MS - 80 - 1)
            assertEquals("not yet rested", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals("rested: the last move shows", game(10), feed.content.value)
            after(SETTLE_MS * 2)
            assertEquals("no late write of an older move", game(10), feed.content.value)
        }

    @Test
    fun theFactsAreReadAfterTheSettleAndShownWhenTheyCome() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            val asked = mutableListOf<SecondScreenContent>()
            val feed =
                feed { content ->
                    asked += content
                    answer.await()
                }
            feed.offer(game(1))
            after(SETTLE_MS - 1)
            assertTrue("nothing read during the move: $asked", asked.isEmpty())
            after(1)
            assertEquals(listOf<SecondScreenContent>(game(1)), asked)
            after(FILL_WAIT_MS)
            assertEquals("shown without its facts line while the read is pending", game(1), feed.content.value)
            answer.complete(played(1))
            runCurrent()
            assertEquals(played(1), feed.content.value)
        }

    @Test
    fun anAnswerForACardTheCursorHasLeftIsDropped() =
        runTest {
            val answers = mutableMapOf<SecondScreenContent, CompletableDeferred<SecondScreenContent?>>()
            val feed = feed { content -> answers.getOrPut(content) { CompletableDeferred() }.await() }
            feed.offer(game(1))
            after(SETTLE_MS + FILL_WAIT_MS)
            feed.offer(game(2))
            answers.getValue(game(1)).complete(played(1))
            runCurrent()
            assertEquals("game 1's facts came after the cursor left", game(1), feed.content.value)
            after(SETTLE_MS + FILL_WAIT_MS)
            assertEquals(game(2), feed.content.value)
            answers.getValue(game(2)).complete(played(2))
            runCurrent()
            assertEquals(played(2), feed.content.value)
        }

    @Test
    fun theSameCardOfferedAgainKeepsItsFactsUntilTheNewAnswer() =
        runTest {
            var minutes = 30
            val feed = feed { (it as SecondScreenContent.Game).copy(isPlayKnown = true, lastPlayedDaysAgo = 0, playTimeMinutes = minutes) }
            feed.offer(game(1))
            after(SETTLE_MS)
            assertEquals(30, (feed.content.value as SecondScreenContent.Game).playTimeMinutes)
            // Back from the game: the library's content changed, the same card is offered again.
            minutes = 75
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1))
            after(SETTLE_MS)
            watching.cancel()
            assertEquals(75, (feed.content.value as SecondScreenContent.Game).playTimeMinutes)
            assertTrue("never shown without its facts in between: $seen", seen.none { it == game(1) })
        }

    @Test
    fun aQuickReadMakesTheCardAppearOnceWithItsFactsAndCover() =
        runTest {
            val front = com.plumemu.home.ArtPicture("/media/1/front.webp", 1240, 886)
            val feed = feed { (it as SecondScreenContent.Game).copy(isPlayKnown = true, lastPlayedDaysAgo = 1, playTimeMinutes = 30, art = front) }
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            watching.cancel()
            assertEquals("Calm, then the one complete card: $seen", 2, seen.size)
            assertEquals(front, (seen.last() as SecondScreenContent.Game).art)
            assertTrue((seen.last() as SecondScreenContent.Game).isPlayKnown)
        }

    @Test
    fun aSlowReadShowsTheCardAtTheCapAndTheFactsWhenTheyCome() =
        runTest {
            val front = com.plumemu.home.ArtPicture("/media/1/front.webp", 1240, 886)
            val answer = CompletableDeferred<SecondScreenContent?>()
            val feed = feed { answer.await() }
            val offered = game(1).copy(scrapeKey = "crc:1")
            feed.offer(offered)
            after(SETTLE_MS + FILL_WAIT_MS - 1)
            assertEquals("still Calm just before the cap", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals("the card without facts or cover at the cap", offered, feed.content.value)
            answer.complete((offered).copy(isPlayKnown = true, art = front))
            runCurrent()
            assertEquals(front, (feed.content.value as SecondScreenContent.Game).art)
        }

    @Test
    fun aMoveDuringTheWaitForTheReadNeverShowsTheFirstCard() =
        runTest {
            val answers = mutableMapOf<SecondScreenContent, CompletableDeferred<SecondScreenContent?>>()
            val feed = feed { content -> answers.getOrPut(content) { CompletableDeferred() }.await() }
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1))
            after(SETTLE_MS + 50)
            feed.offer(game(2))
            answers.getValue(game(1)).complete(played(1))
            after(SETTLE_MS + FILL_WAIT_MS)
            watching.cancel()
            assertTrue("game 1 never shown: $seen", seen.none { it is SecondScreenContent.Game && it.title == "Game 1" })
            assertEquals(game(2), feed.content.value)
        }

    @Test
    fun aCoverOrDetailsAreNeverCarriedToAnotherResultKey() =
        runTest {
            val front = com.plumemu.home.ArtPicture("/media/1/front.webp", 1240, 886)
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed = feed { if (++reads == 1) (it as SecondScreenContent.Game).copy(isPlayKnown = true, art = front, details = FULL_DETAILS) else answer.await() }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            // Signed out now: the same game, no key.
            feed.offer(game(1).copy(scrapeKey = null, isFavorite = true))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertNull((feed.content.value as SecondScreenContent.Game).art)
            assertNull("nor its details", (feed.content.value as SecondScreenContent.Game).details)
        }

    @Test
    fun aGameOfferedAgainKeepsItsCoverAndDetailsUntilTheNewAnswer() =
        runTest {
            val front = com.plumemu.home.ArtPicture("/media/1/front.webp", 1240, 886)
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed =
                feed {
                    if (++reads == 1) (it as SecondScreenContent.Game).copy(isPlayKnown = true, art = front, details = FULL_DETAILS) else answer.await()
                }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            assertEquals(front, (feed.content.value as SecondScreenContent.Game).art)
            // The same game offered again (a favourite toggled), its second read slow: shown without waiting
            // for it, with the cover and details it had, never without them in between.
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1).copy(scrapeKey = "crc:1", isFavorite = true))
            after(SETTLE_MS + FILL_WAIT_MS)
            val kept = feed.content.value as SecondScreenContent.Game
            assertTrue("shown again before the answer", kept.isFavorite)
            assertEquals("its cover kept", front, kept.art)
            assertEquals("its details kept", FULL_DETAILS, kept.details)
            answer.complete(kept)
            runCurrent()
            watching.cancel()
            assertTrue("never shown without its cover in between: $seen", seen.filterIsInstance<SecondScreenContent.Game>().none { it.art == null })
            // Nor without its details: the details card never falls back to the picking card for a moment.
            assertTrue("never shown without its details in between: $seen", seen.filterIsInstance<SecondScreenContent.Game>().none { it.details == null })
        }

    @Test
    fun aFavouriteToggledOnAGameWithFactsNeverLosesItsFactsLine() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed =
                feed { content ->
                    if (++reads == 1) (content as SecondScreenContent.Game).copy(isPlayKnown = true, lastPlayedDaysAgo = 1, playTimeMinutes = 30, emulator = emulatorNamed("Snes9x"), fileName = "Super Metroid (USA).sfc") else answer.await()
                }
            feed.offer(game(1))
            after(SETTLE_MS)
            assertEquals(played(1).copy(emulator = emulatorNamed("Snes9x"), fileName = "Super Metroid (USA).sfc"), feed.content.value)
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            // Y on the game: the same card with isFavorite changed is offered.
            feed.offer(game(1).copy(isFavorite = true))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertEquals(
                "the known facts, emulator and file name stay while it is read again",
                played(1).copy(isFavorite = true, emulator = emulatorNamed("Snes9x"), fileName = "Super Metroid (USA).sfc"),
                feed.content.value,
            )
            answer.complete(played(1).copy(isFavorite = true, playTimeMinutes = 31))
            runCurrent()
            watching.cancel()
            assertEquals(31, (feed.content.value as SecondScreenContent.Game).playTimeMinutes)
            assertTrue("never without its facts line: $seen", seen.all { (it as SecondScreenContent.Game).isPlayKnown })
        }

    @Test
    fun aListWithAnotherCountKeepsItsLastGameUntilTheNewAnswer() =
        runTest {
            val favorites = SecondScreenContent.System("Favorites", 3, lastPlayFrom = LastPlayLookup.AmongFavorites)
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed = feed { content -> if (++reads == 1) (content as SecondScreenContent.System).copy(lastGameTitle = "Zelda", lastPlayedDaysAgo = 2) else answer.await() }
            feed.offer(favorites)
            after(SETTLE_MS)
            feed.offer(favorites.copy(gameCount = 4))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertEquals(favorites.copy(gameCount = 4, lastGameTitle = "Zelda", lastPlayedDaysAgo = 2), feed.content.value)
        }

    @Test
    fun anotherGameNeverTakesTheFactsOfThePreviousOne() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed = feed { content -> if (++reads == 1) played(1) else answer.await() }
            feed.offer(game(1))
            after(SETTLE_MS)
            feed.offer(game(2))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertEquals("no facts line until game 2's answer", game(2), feed.content.value)
        }

    @Test
    fun aFailedReadIsLoggedByClassAndLeavesNoFactsLine() =
        runTest {
            val feed = feed { throw IllegalStateException("closed database at /data/user/0/secret") }
            feed.offer(game(1))
            after(SETTLE_MS)
            assertEquals("the card without its facts line", game(1), feed.content.value)
            assertEquals(listOf("Second screen facts: could not be read (IllegalStateException)"), warnings)
        }

    @Test
    fun aCardWithNothingToReadStaysAsOffered() =
        runTest {
            val feed = feed { null }
            feed.offer(SecondScreenContent.Apps(12))
            after(SETTLE_MS)
            assertEquals(SecondScreenContent.Apps(12), feed.content.value)
        }

    @Test
    fun whenTheWindowGoesTheFeedIsCalmAndDropsWhatWasWaiting() =
        runTest {
            val feed = feed()
            feed.setFollowing(true)
            feed.offer(game(1))
            after(SETTLE_MS)
            assertEquals(game(1), feed.content.value)
            feed.offer(game(2))
            feed.setFollowing(false)
            assertEquals("a window shown again never flashes the old card", SecondScreenContent.Calm, feed.content.value)
            after(SETTLE_MS * 2)
            assertEquals("the waiting move was dropped", SecondScreenContent.Calm, feed.content.value)
        }

    @Test
    fun followingIsTheHostsToSet() =
        runTest {
            val feed = feed()
            assertEquals(false, feed.isFollowing.value)
            feed.setFollowing(true)
            assertEquals(true, feed.isFollowing.value)
            feed.setFollowing(false)
            assertEquals(false, feed.isFollowing.value)
        }
}
