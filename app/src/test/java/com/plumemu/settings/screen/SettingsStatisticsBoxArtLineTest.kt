package com.plumemu.settings.screen

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.account.AccountStatus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.scraper.PictureFailure
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The box art tile of Statistics says what the run is doing or why it failed, also under "Box art only" with no account. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsBoxArtLineTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val counts = ScrapeCounts(withBoxFront = 300, listed = 412, pending = 112)

    private fun show(boxArt: ScrapeStatus, account: AccountStatus = AccountStatus.SignedOut) {
        val content = StatisticsShots.contentOf(StatisticsShots.played).copy(account = account, boxArt = boxArt)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(0), content = content) } } }
        compose.waitForIdle()
    }

    private fun line() = compose.onNodeWithTag(StatisticsTags.BOX_ART_STATUS, useUnmergedTree = true)

    @Test
    fun runningSaysHowFarItIs() {
        show(ScrapeStatus.Running(counts, null))
        line().assertTextEquals("Downloading box art · 300\u00A0of\u00A0412")
    }

    @Test
    fun doneSaysHowManyGamesHaveBoxArt() {
        show(ScrapeStatus.Idle(ScrapeCounts(withBoxFront = 400, listed = 412, pending = 0)))
        line().assertTextEquals("Box art for 400 of 412 games")
    }

    @Test
    fun noNetworkSaysSo() {
        show(ScrapeStatus.Failed(counts, PictureFailure.NoNetwork))
        line().assertTextEquals("No network: box art could not be downloaded")
    }

    @Test
    fun aServerThatDidNotAnswerSaysSo() {
        show(ScrapeStatus.Failed(counts, PictureFailure.ServerDidNotAnswer))
        line().assertTextEquals("The box art server did not answer")
    }

    @Test
    fun noMatchSaysNoneWasFound() {
        show(ScrapeStatus.Idle(ScrapeCounts(withBoxFront = 0, listed = 412, pending = 0)))
        line().assertTextEquals("No box art found for these games")
    }

    @Test
    fun waitingForWifiIsTheNoNetworkReasonOfARunThatCannotStart() {
        show(ScrapeStatus.WaitingForWifi(counts), AccountStatus.SignedIn("plumtester"))
        line().assertTextEquals("Waiting for Wi-Fi")
    }

    @Test
    fun withoutBoxArtSetUpThereIsNoLine() {
        show(ScrapeStatus.NotInThisBuild, AccountStatus.NotInThisBuild)
        compose.onNodeWithTag(StatisticsTags.BOX_ART_STATUS, useUnmergedTree = true).assertDoesNotExist()
    }
}
