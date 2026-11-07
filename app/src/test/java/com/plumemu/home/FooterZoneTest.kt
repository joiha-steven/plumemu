package com.plumemu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.HintKeyDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.setup.HomeRolePromptLayout
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupFakes
import com.plumemu.setup.SetupTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One footer zone on every screen with hint keys: from what the screen's content ends at to the screen's bottom it is
 * [PlumDimens.footerGap] + the bar + [PlumDimens.footerBelow], 40 dp at Medium (the content ends where the strip does).
 * The panels over the main screen draw no strip of their own: their key row sits the same distance above the bottom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class FooterZoneTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun rootBottom(): Dp = compose.onRoot().getUnclippedBoundsInRoot().bottom

    /** Home's columns run to the hint bar (rows go under its fade): their content ends where the slot does, the footer gap above the bar. */
    private fun zoneUnder(
        tag: String,
        runsToBar: Boolean,
    ): Float = (rootBottom() - compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { if (runsToBar) it.slot().bottom else it.bottom }).value

    private fun check(
        what: String,
        contentTag: String,
        runsToBar: Boolean = true,
        scene: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        var expected = 0f
        var size by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                expected = PlumTheme.layout.footerZone.value
                scene()
            }
        }
        compose.waitForIdle()
        assertEquals("$what at Medium: footer zone", 40f, zoneUnder(contentTag, runsToBar), 0.5f)
        size = TextSize.Largest
        compose.waitForIdle()
        assertEquals("$what at Largest: footer zone", expected, zoneUnder(contentTag, runsToBar), 0.5f)
    }

    @Test
    fun homeList() = check("home list", HomeTags.STRIP) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    @Test
    fun homeSystems() = check("home systems", HomeTags.STRIP) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) }

    @Test
    fun homeGrid() = check("home grid", HomeTags.STRIP) { HomeAt(FakeHomeContent.withArtwork(), gridActions(NDS_INDEX, 0), gridIds = setOf("nds")) }

    private val searchContent = FakeHomeContent.withFavorites()
    private val searchState =
        SearchState().apply {
            open()
            onQueryChange("super")
            showResults("super", SearchIndex.of(searchContent).find("super"))
            toResults()
        }

    @Test
    fun search() =
        check("search", SearchTags.RESULTS) {
            HomeAt(searchContent, BoardGamesFocused, search = searchState)
        }

    @Test
    fun settings() = check("settings", SettingsTags.STRIP, runsToBar = false) { SettingsAt(SettingsFocus()) }

    @Test
    fun setup() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) { SetupAt(SetupFakes.ready) } }
        compose.waitForIdle()
        val bar = compose.onNodeWithTag(SetupTags.HINTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("setup bar height", 22f, (bar.bottom - bar.top).value, 0.5f)
        assertEquals("setup: the bar to the bottom", PlumDimens.footerBelow.value, (rootBottom() - bar.bottom).value, 0.5f)
    }

    @Test
    fun theKeyRowOfAPanelSitsAtTheSameHeight() {
        var density = Density(1f)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                density = LocalDensity.current
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) { HomeRolePromptLayout(false, focus = 0, letters = ButtonLetters.Default, onAction = {}) }
            }
        }
        compose.waitForIdle()
        val centres = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { with(density) { ((it.boundsInRoot.top + it.boundsInRoot.bottom) / 2f).toDp().value } }
        // The bar's middle: the margin under it and half the bar above that (a cap rests a little above it: its collar is deeper below).
        val expected = (rootBottom() - PlumDimens.footerBelow - PlumDimens.hintBarHeight / 2).value
        assertEquals("the panel's key row centre", expected, centres.first(), 1f)
    }
}

/** The room a list keeps above the hint keys' drawn top (their collars) at [size]: the footer gap less how far the collars reach above the bar. */
internal fun footerGapToKeys(size: TextSize): Dp =
    PlumDimens.footerGap - HintKeyDimens.reachAboveBar(PlumDimens.hintBarHeight * size.hintHeightScale, PlumDimens.keyCapHeight * size.hintHeightScale)
