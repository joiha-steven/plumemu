package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakeAppIcons
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.HomeTags
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The App card's icon and the Apps card's icon grid, measured at every text size on both second-screen sizes.
 */
abstract class SecondScreenCatchUpBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val icons = FakeAppIcons(missing = setOf(NO_ICON_KEY))

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent {
            PlumTheme(settings) { CompositionLocalProvider(LocalHomeAppIcons provides icons) { SecondScreenCard(content, images = FakeCoverImages()) } }
        }
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun px(dp: Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun show(
        showing: SecondScreenContent,
        textSize: TextSize = TextSize.Medium,
    ) {
        settings = PlumThemeSettings.Default.copy(textSize = textSize)
        content = showing
        compose.waitForIdle()
    }

    private fun all(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** The App card keeps the game card's margins. */
    private fun assertInsideGameMargins(
        label: String,
        tags: List<String>,
    ) {
        val card = bounds(SecondScreenTags.CARD)
        for (tag in tags) {
            for (node in all(tag)) {
                val b = node.boundsInRoot
                assertTrue("$label: $tag left ${b.left}", b.left >= card.left + px(SecondScreenGameCardDimens.marginSide) - 0.5f)
                assertTrue("$label: $tag top ${b.top}", b.top >= card.top + px(SecondScreenGameCardDimens.marginTop) - 0.5f)
                assertTrue("$label: $tag right ${b.right}", b.right <= card.right - px(SecondScreenGameCardDimens.marginSide) + 0.5f)
                assertTrue("$label: $tag bottom ${b.bottom}", b.bottom <= card.bottom - px(SecondScreenGameCardDimens.marginBottom) + 0.5f)
            }
        }
    }

    private fun assertInside(
        label: String,
        tags: List<String>,
    ) {
        val card = bounds(SecondScreenTags.CARD)
        val margin = px(dimens.margin)
        for (tag in tags) {
            for (node in all(tag)) {
                val b = node.boundsInRoot
                assertTrue("$label: $tag left ${b.left}", b.left >= card.left + margin - 0.5f)
                assertTrue("$label: $tag top ${b.top}", b.top >= card.top + margin - 0.5f)
                assertTrue("$label: $tag right ${b.right}", b.right <= card.right - margin + 0.5f)
                assertTrue("$label: $tag bottom ${b.bottom}", b.bottom <= card.bottom - margin + 0.5f)
            }
        }
    }

    // ---- the App card

    @Test
    fun theAppCardShowsTheNameWithAnAppTagTheIconLargeAndCentredAndThePackage() {
        for (textSize in TextSize.entries) {
            show(SecondScreenContent.App("RetroArch", "com.retroarch/.Main"), textSize)
            val label = "$size $textSize"
            assertEquals("$label: one icon", 1, all(HomeTags.APP_GAME_ICON).size)
            val card = bounds(SecondScreenTags.CARD)
            val icon = bounds(HomeTags.APP_GAME_ICON)
            assertTrue("$label: the icon is larger than the details icon, ${icon.width}", icon.width > px(AppsGridDimens.appGameDetailsIcon) + 0.5f)
            assertEquals("$label: the icon is centred across the card", (card.left + card.right) / 2, (icon.left + icon.right) / 2, 1f)
            val title = bounds(GameCardTags.TITLE)
            val tag = bounds(GameCardTags.SYSTEM)
            val file = bounds(GameCardTags.FILE)
            assertTrue("$label: the tag is at the title's right", tag.left >= title.right - 0.5f)
            assertTrue("$label: the icon is under the title", icon.top >= title.bottom - 0.5f)
            assertTrue("$label: the package is under the icon", file.top >= icon.bottom - 0.5f)
            assertEquals("$label: the tag", "APP", compose.onNodeWithTag(GameCardTags.SYSTEM, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(""))
            assertInsideGameMargins(label, listOf(GameCardTags.TITLE, GameCardTags.SYSTEM, SecondScreenTags.COVER, GameCardTags.FILE))
        }
    }

    @Test
    fun aLongNameAndPackageKeepInsideTheCard() {
        for (textSize in TextSize.entries) {
            show(APP_LONG_NAME, textSize)
            val label = "$size $textSize long"
            assertInsideGameMargins(label, listOf(GameCardTags.TITLE, GameCardTags.SYSTEM, SecondScreenTags.COVER, GameCardTags.FILE))
            assertTrue("$label: the title stays on one line", bounds(GameCardTags.TITLE).height < px(dimens.margin) * 2.5f)
        }
    }

    @Test
    fun anAppWithoutAnIconShowsTheInitialOnADisc() {
        for (textSize in TextSize.entries) {
            show(SecondScreenContent.App("No Icon App", NO_ICON_KEY), textSize)
            val label = "$size $textSize"
            assertEquals(0, all(HomeTags.APP_GAME_ICON).size)
            val disc = bounds(HomeTags.APP_ICON_STAND_IN)
            val card = bounds(SecondScreenTags.CARD)
            assertTrue("$label: the disc is large, ${disc.width}", disc.width > px(AppsGridDimens.appGameDetailsIcon) + 0.5f)
            assertEquals("$label: the disc is round", disc.width, disc.height, 0.5f)
            assertEquals("$label: the disc is centred across the card", (card.left + card.right) / 2, (disc.left + disc.right) / 2, 1f)
            assertInsideGameMargins(label, listOf(HomeTags.APP_ICON_STAND_IN))
        }
    }

    @Test
    fun anAppWithNoIconKeyHasTheDiscAndNoPackageLine() {
        show(SecondScreenContent.App("RetroArch"))
        assertEquals(1, all(HomeTags.APP_ICON_STAND_IN).size)
        assertEquals(0, all(GameCardTags.FILE).size)
    }

    // ---- the Apps card

    private fun apps(count: Int) = SecondScreenContent.Apps(count, (0 until count).map { AppIconRef(if (it == 3) NO_ICON_KEY else "app.$it", "App $it") })

    @Test
    fun theAppsCardFillsTheHeightLeftWithIconsAndNoMore() {
        for (textSize in TextSize.entries) {
            show(apps(MAX_APP_ICONS), textSize)
            val label = "$size $textSize"
            val grid = bounds(SecondScreenTags.APPS_GRID)
            val title = bounds(SecondScreenTags.TITLE)
            val shown = all(SecondScreenTags.APPS_ICON)
            assertInside(label, listOf(SecondScreenTags.TOP, SecondScreenTags.TITLE, SecondScreenTags.APPS_ICON))
            assertTrue("$label: the grid is under the title", grid.top >= title.bottom)
            val icon = px(AppsGridDimens.appsGridIcon)
            val gap = px(PlumDimens.gridGapPortrait)
            val columns = ((grid.width + gap) / (icon + gap)).toInt()
            val rows = ((grid.height + gap) / (icon + gap)).toInt()
            assertEquals("$label: as many as fit", minOf(MAX_APP_ICONS, columns * rows), shown.size)
            shown.forEach { assertEquals("$label: icon size", icon, it.boundsInRoot.width, 0.5f) }
            if (shown.isNotEmpty()) assertEquals("$label: left on the margin", bounds(SecondScreenTags.CARD).left + px(dimens.margin), shown.minOf { it.boundsInRoot.left }, 0.5f)
        }
    }

    @Test
    fun fewAppsDrawOnlyThemAndAnIconlessOneShowsTheStandIn() {
        show(apps(5))
        assertEquals(5, all(SecondScreenTags.APPS_ICON).size)
        assertEquals(1, all(HomeTags.APP_ICON_STAND_IN).size)
    }

    @Test
    fun noIconsKeepsThePickingBlockWithTheCount() {
        show(SecondScreenContent.Apps(128))
        assertEquals(1, all(SecondScreenTags.BLOCK).size)
        assertEquals(0, all(SecondScreenTags.APPS_GRID).size)
    }
}

private const val NO_ICON_KEY = "app.noicon"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCatchUpRoomyTest : SecondScreenCatchUpBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCatchUpCompactTest : SecondScreenCatchUpBase(SecondSize.Compact)
