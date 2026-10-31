package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeArtSource
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsStatisticsSource
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.TEST_VERSION
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * While Settings is open the second screen shows its Statistics figures: from the read
 * Settings makes for its page (one per opening, none of its own), the calm mark until they arrive,
 * the new figures after a scan, the main screen's card again on leaving, and no recomposition of
 * the top screen for it (counted with Compose's composition tracer, as SecondScreenHomeFollowTest).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenStatisticsFollowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true))

    /** Settings' figures: counts its reads; [gate] holds a read back until completed; [answer] is what it reads. */
    private val source =
        object : SettingsStatisticsSource {
            var reads = 0
            var gate: CompletableDeferred<Unit>? = null
            var answer: StatisticsState = StatisticsState.Read(SecondScreenStatisticsSamples.withCovers)

            override suspend fun read(): StatisticsState {
                reads++
                gate?.await()
                return answer
            }

            override val art: HomeArtSource = HomeArtSource.None
        }

    private val library = MutableStateFlow(SettingsContent())
    private val host =
        SettingsHost(versionName = TEST_VERSION, update = {}, resetAllSettings = {}, resetLibrary = {}, content = library, statistics = source)
    private lateinit var router: InputRouter
    private var feed: SecondScreenFeed? = null

    /** Every card the feed showed, in order. */
    private val seen = mutableListOf<SecondScreenContent>()
    private val calls = mutableListOf<String>()

    /** A compose rule hosts one content per test: the variant is a state, and a new one builds a fresh main screen. */
    private var variant by mutableStateOf(true)
    private var isHosted = false

    private fun showMainScreen(withSecondScreen: Boolean = true) {
        if (isHosted) {
            compose.runOnIdle { variant = withSecondScreen }
        } else {
            variant = withSecondScreen
            isHosted = true
            compose.setContent { key(variant) { Hosted(variant) } }
        }
        compose.waitForIdle()
        // The main screen's game index is built off the main thread before its first card.
        if (withSecondScreen) compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
    }

    @Composable
    private fun Hosted(withSecondScreen: Boolean) {
        val scope = rememberCoroutineScope()
        router = remember { InputRouter(scope, onDebug = {}) }
        val made = remember { if (withSecondScreen) SecondScreenFeed(scope).also { it.setFollowing(true) } else null }
        feed = made
        if (made != null) LaunchedEffect(made) { made.content.collect { seen += it } }
        MainScreen(settings, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host, secondScreen = made)
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        settle()
    }

    /** The cursor rests: what was offered reaches the feed (the settle rule has its own test). */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SecondScreenFeed.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun shown(): SecondScreenContent = compose.runOnIdle { feed!!.content.value }

    private fun statisticsOf(figures: PlayStatistics) = SecondScreenContent.Statistics(figures)

    @After
    fun stopTracing() = Composer.setTracer(null)

    @Test
    fun theCardIsCalmUntilTheFiguresArriveThenShowsThemFromSettingsOwnRead() {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        showMainScreen()
        send(Action.Menu)
        assertEquals("Settings reads once", 1, source.reads)
        assertEquals("not before the figures: the calm mark", SecondScreenContent.Calm, shown())
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(statisticsOf(SecondScreenStatisticsSamples.withCovers), shown())
        assertEquals("the second screen read nothing of its own", 1, source.reads)
        // From the main screen's card: the calm mark, then the figures, nothing in between and nothing twice.
        val sinceSettings = seen.drop(seen.indexOfLast { it is SecondScreenContent.System } + 1)
        assertEquals(listOf(SecondScreenContent.Calm, statisticsOf(SecondScreenStatisticsSamples.withCovers)), sinceSettings)
    }

    @Test
    fun aQuickReadGoesStraightToTheCard() {
        showMainScreen()
        val before = seen.size
        send(Action.Menu)
        assertEquals(statisticsOf(SecondScreenStatisticsSamples.withCovers), shown())
        assertEquals("one change, no calm mark in between", listOf(statisticsOf(SecondScreenStatisticsSamples.withCovers)), seen.drop(before))
        assertEquals(1, source.reads)
    }

    @Test
    fun theFiguresFollowAScanThatEndsWhileSettingsIsOpen() {
        showMainScreen()
        send(Action.Menu)
        assertEquals(statisticsOf(SecondScreenStatisticsSamples.withCovers), shown())
        source.answer = StatisticsState.Read(SecondScreenStatisticsSamples.signedOut)
        library.value = SettingsContent(isScanning = true)
        settle()
        assertEquals("a scan starting changes nothing yet", 1, source.reads)
        library.value = SettingsContent(isScanning = false)
        settle()
        assertEquals("Settings read again, once", 2, source.reads)
        assertEquals(statisticsOf(SecondScreenStatisticsSamples.signedOut), shown())
    }

    @Test
    fun leavingSettingsGivesTheMainScreensCardBack() {
        showMainScreen()
        send(Action.Menu)
        assertTrue(shown() is SecondScreenContent.Statistics)
        send(Action.Back)
        // Which entry: whatever the main screen's focus is after Settings (its own tests cover that).
        compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
        send(Action.Menu)
        assertEquals("opened again: read again, shown again", 2, source.reads)
        assertTrue(shown() is SecondScreenContent.Statistics)
    }

    @Test
    fun aFailedReadOrAnEmptyLibraryStaysCalm() {
        source.answer = StatisticsState.Failed
        showMainScreen()
        send(Action.Menu)
        assertEquals("failed", SecondScreenContent.Calm, shown())
        send(Action.Back)
        compose.waitUntil(WAIT_MS) { shown() is SecondScreenContent.System }
        source.answer = StatisticsState.Read(PlayStatistics())
        send(Action.Menu)
        assertEquals("empty library", SecondScreenContent.Calm, shown())
        assertTrue("no statistics card at any point", seen.none { it is SecondScreenContent.Statistics })
    }

    @Test
    fun theCardsContentFromTheState() {
        assertEquals(SecondScreenContent.Calm, secondScreenContentOf(StatisticsState.NotRead))
        assertEquals(SecondScreenContent.Calm, secondScreenContentOf(StatisticsState.Failed))
        assertEquals(SecondScreenContent.Calm, secondScreenContentOf(StatisticsState.Read(PlayStatistics())))
        // Played time on games no longer listed, with an empty library: still Calm, as Settings says "No games".
        assertEquals(SecondScreenContent.Calm, secondScreenContentOf(StatisticsState.Read(PlayStatistics(totalSeconds = 600, hasPlayed = true))))
        val figures = SecondScreenStatisticsSamples.nothingPlayed
        assertEquals(SecondScreenContent.Statistics(figures), secondScreenContentOf(StatisticsState.Read(figures)))
    }

    @Test
    fun openingSettingsRecomposesTheSameWithTheSecondScreenOnAsOff() {
        val off = recomposedByOpeningSettings(withSecondScreen = false)
        val on = recomposedByOpeningSettings(withSecondScreen = true)
        assertTrue("opening Settings recomposed something: $off", off.isNotEmpty())
        assertEquals("the top screen's recompositions with the second screen on", off, on)
    }

    /** Our composables that ran from Menu until the figures are on the page (and on the bottom card), sorted. */
    private fun recomposedByOpeningSettings(withSecondScreen: Boolean): List<String> {
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        showMainScreen(withSecondScreen)
        send(Action.Menu)
        compose.runOnIdle {
            calls.clear()
            Composer.setTracer(
                object : CompositionTracer {
                    override fun isTraceInProgress(): Boolean = true

                    override fun traceEventStart(
                        key: Int,
                        dirty1: Int,
                        dirty2: Int,
                        info: String,
                    ) {
                        if (info.startsWith("com.plumemu.")) calls += info.substringBefore(" (")
                    }

                    override fun traceEventEnd() = Unit
                },
            )
            gate.complete(Unit)
        }
        settle()
        val ran = compose.runOnIdle { calls.toList().sorted().also { Composer.setTracer(null) } }
        if (withSecondScreen) assertTrue("the bottom card shows the figures", shown() is SecondScreenContent.Statistics)
        send(Action.Back)
        return ran
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
