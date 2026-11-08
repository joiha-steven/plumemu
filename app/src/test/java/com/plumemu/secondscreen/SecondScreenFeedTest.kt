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
    fun aSteadyPressEveryQuarterSecondNeverReachesTheWindowUntilTheLast() =
        runTest {
            val feed = feed()
            val seen = mutableListOf<SecondScreenContent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            // The benchmark's pace: a settle of exactly 250 ms put the card into the next press's frame.
            for (n in 1..40) {
                feed.offer(game(n))
                after(250)
            }
            assertEquals("no card change in any press's frame while the person keeps pressing", listOf<SecondScreenContent>(SecondScreenContent.Calm), seen)
            after(SETTLE_MS)
            assertEquals("the card settles to the game under the cursor", game(40), feed.content.value)
            assertEquals(listOf(SecondScreenContent.Calm, game(40)), seen)
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
    fun aQuickReadMakesTheCardAppearOnceWithItsFactsAndDetails() =
        runTest {
            val feed = feed { (it as SecondScreenContent.Game).copy(isPlayKnown = true, lastPlayedDaysAgo = 1, playTimeMinutes = 30, details = FULL_DETAILS) }
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            watching.cancel()
            assertEquals("Calm, then the one complete card: $seen", 2, seen.size)
            assertEquals(FULL_DETAILS, (seen.last() as SecondScreenContent.Game).details)
            assertTrue((seen.last() as SecondScreenContent.Game).isPlayKnown)
        }

    @Test
    fun aSlowReadShowsTheCardAtTheCapAndTheFactsWhenTheyCome() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            val feed = feed { answer.await() }
            val offered = game(1).copy(scrapeKey = "crc:1")
            feed.offer(offered)
            after(SETTLE_MS + FILL_WAIT_MS - 1)
            assertEquals("still Calm just before the cap", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals("the card without facts or details at the cap", offered, feed.content.value)
            answer.complete((offered).copy(isPlayKnown = true, details = FULL_DETAILS))
            runCurrent()
            assertEquals(FULL_DETAILS, (feed.content.value as SecondScreenContent.Game).details)
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
    fun aResultsDetailsAreNeverCarriedToAnotherResultKey() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed = feed { if (++reads == 1) (it as SecondScreenContent.Game).copy(isPlayKnown = true, details = FULL_DETAILS) else answer.await() }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            // Signed out now: the same game, no key.
            feed.offer(game(1).copy(scrapeKey = null, isFavorite = true))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertNull("no details of another key", (feed.content.value as SecondScreenContent.Game).details)
        }

    @Test
    fun aGameOfferedAgainKeepsItsDetailsUntilTheNewAnswer() =
        runTest {
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed =
                feed {
                    if (++reads == 1) (it as SecondScreenContent.Game).copy(isPlayKnown = true, details = FULL_DETAILS) else answer.await()
                }
            feed.offer(game(1).copy(scrapeKey = "crc:1"))
            after(SETTLE_MS)
            assertEquals(FULL_DETAILS, (feed.content.value as SecondScreenContent.Game).details)
            // The same game offered again (a favourite toggled), its second read slow: shown without waiting
            // for it, with the details it had, never without them in between.
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            feed.offer(game(1).copy(scrapeKey = "crc:1", isFavorite = true))
            after(SETTLE_MS + FILL_WAIT_MS)
            val kept = feed.content.value as SecondScreenContent.Game
            assertTrue("shown again before the answer", kept.isFavorite)
            assertEquals("its details kept", FULL_DETAILS, kept.details)
            answer.complete(kept)
            runCurrent()
            watching.cancel()
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
    fun aGameOfferedAgainKeepsItsPaceAndAchievementsUntilTheNewAnswer() =
        runTest {
            val pace = com.plumemu.battery.BatteryPace(120.0, 30.0)
            val ra = com.plumemu.graph.AchievementCount(12, 45)
            val feed = feed { (it as SecondScreenContent.Game).copy(isPlayKnown = true, lastPlayedDaysAgo = 0, playTimeMinutes = 5, batteryPace = pace, achievements = ra) }
            feed.offer(game(1))
            after(SETTLE_MS)
            val seen = mutableListOf<SecondScreenContent>()
            val watching = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.content.collect { seen += it } }
            // A star toggled: the same game is offered again, and shown with what it had until the read answers.
            feed.offer(game(1).copy(isFavorite = true))
            after(SETTLE_MS)
            watching.cancel()
            assertTrue("never shown without its pace and achievements: $seen", seen.all { (it as SecondScreenContent.Game).let { g -> g.batteryPace == pace && g.achievements == ra } })
        }

    @Test
    fun aListWithAnotherCountKeepsItsFiguresUntilTheNewAnswer() =
        runTest {
            val favorites = SystemEntry("snes", "Super Nintendo", 3)
            val read = SystemFigures(emptyList(), 600, 2, null, 0, 0, null, 1L)
            val answer = CompletableDeferred<SecondScreenContent?>()
            var reads = 0
            val feed = feed { content -> if (++reads == 1) (content as SystemEntry).copy(figures = read) else answer.await() }
            feed.offer(favorites)
            after(SETTLE_MS)
            feed.offer(favorites.copy(gameCount = 4))
            after(SETTLE_MS + FILL_WAIT_MS)
            assertEquals(favorites.copy(gameCount = 4, figures = read), feed.content.value)
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

    @Test
    fun theFirstOfferAfterTheWindowShowsHasNoSettle() =
        runTest {
            val feed = feed { played(1) }
            feed.setFollowing(true)
            feed.offer(game(1))
            after(FILL_WAIT_MS)
            assertTrue("shown well before SETTLE_MS, with its facts", FILL_WAIT_MS < SETTLE_MS)
            assertEquals(played(1), feed.content.value)
        }

    @Test
    fun theSecondOfferStillWaitsTheSettle() =
        runTest {
            val feed = feed()
            feed.setFollowing(true)
            feed.offer(game(1))
            after(1)
            assertEquals(game(1), feed.content.value)
            feed.offer(game(2))
            after(SETTLE_MS - 1)
            assertEquals(game(1), feed.content.value)
            after(1)
            assertEquals(game(2), feed.content.value)
        }

    @Test
    fun theFirstOfferAfterTheWindowShowsAgainIsImmediateAgain() =
        runTest {
            val feed = feed()
            feed.setFollowing(true)
            feed.offer(game(1))
            after(1)
            feed.setFollowing(false)
            feed.setFollowing(true)
            feed.offer(game(2))
            after(1)
            assertEquals(game(2), feed.content.value)
        }

    @Test
    fun aMoveRightAfterTheFirstOfferCancelsItAndSettles() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val feed = feed { gate.await(); played((it as SecondScreenContent.Game).gameId!!.toInt()) }
            feed.setFollowing(true)
            feed.offer(game(1))
            after(50)
            feed.offer(game(2))
            gate.complete(Unit)
            after(SETTLE_MS - 1)
            assertEquals("the first read was cancelled", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals(played(2), feed.content.value)
        }

    @Test
    fun theSameCardOfferedAgainWhileItWaitsKeepsItsWait() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val feed = feed { gate.await(); played(1) }
            feed.setFollowing(true)
            feed.offer(game(1))
            after(50)
            feed.offer(game(1))
            gate.complete(Unit)
            after(1)
            assertEquals("the library read again: the first card is not settled again", played(1), feed.content.value)
        }

    @Test
    fun theSameCardOfferedAgainIsReadOnceMoreAfterTheSettle() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var reads = 0
            val feed =
                feed {
                    reads++
                    if (reads == 1) {
                        gate.await()
                        played(1)
                    } else {
                        played(1).copy(playTimeMinutes = 45)
                    }
                }
            feed.setFollowing(true)
            feed.offer(game(1))
            after(10)
            feed.offer(game(1))
            gate.complete(Unit)
            after(1)
            assertEquals("the first read shows at once", played(1), feed.content.value)
            after(SETTLE_MS)
            assertEquals("then a fresher record is read again", played(1).copy(playTimeMinutes = 45), feed.content.value)
        }

    @Test
    fun aMoveAfterTheSameCardOfferedAgainCancelsBoth() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val feed = feed { gate.await(); played((it as SecondScreenContent.Game).gameId!!.toInt()) }
            feed.setFollowing(true)
            feed.offer(game(1))
            after(10)
            feed.offer(game(1))
            after(10)
            feed.offer(game(2))
            gate.complete(Unit)
            after(SETTLE_MS - 1)
            assertEquals("game 1 never shown", SecondScreenContent.Calm, feed.content.value)
            after(1)
            assertEquals(played(2), feed.content.value)
        }
}
