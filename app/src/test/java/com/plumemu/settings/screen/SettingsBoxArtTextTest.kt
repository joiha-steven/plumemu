package com.plumemu.settings.screen

import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.graph.ScrapeCounts
import com.plumemu.scraper.AccountLimits
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The words of every box-art line: the panel's full line and the account row's short form. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBoxArtTextTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @get:Rule
    val utc = UtcTimeZone()

    private val counts = ScrapeCounts(withBoxFront = 300, listed = 2_054, pending = 1_714)

    /** 22:00 UTC, the shots' resume time. */
    private val tenPm = 1_792_792_800_000L

    @Test
    fun everyLineSaysItsState() {
        val lines =
            listOf(
                BoxArtLine.Running(counts, TimeLeft.Hours(5)) to ("Downloading box art 340\u00A0of\u00A02,054 · about 5\u00A0h left" to "340\u00A0of\u00A02,054"),
                BoxArtLine.Running(counts, TimeLeft.Minutes(40)) to ("Downloading box art 340\u00A0of\u00A02,054 · about 40\u00A0min left" to "340\u00A0of\u00A02,054"),
                BoxArtLine.Running(counts, TimeLeft.Days(3)) to ("Downloading box art 340\u00A0of\u00A02,054 · about 3 days left" to "340\u00A0of\u00A02,054"),
                BoxArtLine.Running(counts, TimeLeft.UnderAMinute) to ("Downloading box art 340\u00A0of\u00A02,054 · under a minute left" to "340\u00A0of\u00A02,054"),
                BoxArtLine.Running(counts, null) to ("Downloading box art 340\u00A0of\u00A02,054" to "340\u00A0of\u00A02,054"),
                BoxArtLine.Running(null, null) to ("Downloading box art" to "Downloading"),
                BoxArtLine.PausedByPerson to ("Paused" to "Paused"),
                BoxArtLine.WaitingForWifi to ("Waiting for Wi-Fi" to "Waiting for Wi-Fi"),
                BoxArtLine.WaitingForBattery to ("Waiting for the battery to charge" to "Battery low"),
                BoxArtLine.DailyLimit(tenPm) to ("Daily limit. Resumes at 10:00 PM." to "Daily limit reached"),
                BoxArtLine.ServerClosed(tenPm) to ("ScreenScraper is closed for now, trying again at 10:00 PM" to "Closed for now"),
                BoxArtLine.WrongPassword to ("Wrong password: sign in again" to "Sign in again"),
                BoxArtLine.Retrying(tenPm) to ("No connection, trying again at 10:00 PM" to "No connection"),
                BoxArtLine.Retrying(null) to ("No connection, trying again soon" to "No connection"),
                BoxArtLine.Starting(counts) to ("Starting…" to "Starting…"),
                BoxArtLine.Done(2_012, 2_054) to ("Done: 2,012 of 2,054 games have box art" to "Done"),
                BoxArtLine.Some(300, 2_054) to ("300 of 2,054 games have box art" to null),
                BoxArtLine.EmptyLibrary to ("No games yet: box art comes once Plumemu has found your games." to null),
                BoxArtLine.Unreadable to ("Plumemu could not read how far box art has come." to null),
                BoxArtLine.NotInThisBuild to ("Box art: not available in this build" to null),
            )
        val said = mutableListOf<Pair<String, String?>>()
        compose.setContent { lines.forEach { (line, _) -> said += boxArtLineText(line) to boxArtShortText(line) } }
        compose.waitForIdle()
        lines.map { it.second }.zip(said).forEach { (expected, actual) -> assertEquals(expected, actual) }
    }

    @Test
    fun theFactsSayWhatTheAccountMayDo() {
        compose.setContent { AccountFacts(BoxArtShots.limits) }
        compose.waitForIdle()
        compose.onNodeWithText("Requests today: 1,234\u00A0of\u00A010,000").assertExists()
        compose.onNodeWithText("Parallel downloads: 1").assertExists()
        compose.onNodeWithText("Download speed: 128\u00A0KB/s").assertExists()
    }

    @Test
    fun aUsedUpDayIsSaidInTheStatusLinesWords() {
        compose.setContent { AccountFacts(BoxArtShots.limitsUsedUp) }
        compose.waitForIdle()
        compose.onNodeWithText("Requests today: 10,000\u00A0of\u00A010,000 · daily limit reached").assertExists()
    }

    @Test
    fun nothingIsShownWhileTheLimitsAreUnknown() {
        compose.setContent { AccountFacts(null) }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsPanelTags.FACTS).assertDoesNotExist()
    }

    @Test
    fun onlyWhatIsKnownIsShownAndNoDashesStandIn() {
        assertEquals(listOf(AccountFact.Requests(today = 7, limit = null, usedUp = false)), accountFactsOf(AccountLimits(requestsToday = 7)))
        assertEquals(
            listOf(AccountFact.DownloadSpeed(64)),
            accountFactsOf(AccountLimits(maxThreads = 0, requestsToday = null, maxDownloadKilobytesPerSecond = 64)),
        )
        assertEquals(emptyList<AccountFact>(), accountFactsOf(AccountLimits()))
        assertEquals(emptyList<AccountFact>(), accountFactsOf(null))
        // The per-minute budget is not shown.
        assertEquals(emptyList<AccountFact>(), accountFactsOf(AccountLimits(maxRequestsPerMinute = 300)))
    }

    @Test
    fun theFactsFollowTheLocalesGrouping() {
        compose.setContent {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { setLocale(java.util.Locale.GERMANY) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) { AccountFacts(BoxArtShots.limits) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Requests today: 1.234\u00A0of\u00A010.000").assertExists()
    }
}
