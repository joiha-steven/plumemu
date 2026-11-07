package com.plumemu.settings.screen

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import com.plumemu.account.AccountStatus
import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Accounts › Box art in the third column (832x468): the Current badge shares the title's line and the detail below
 * keeps the column's whole width; with the second option focused a part of the first shows under the fade; with the third focused the option cut by the top edge fades into it over the edge's last 12 dp, no hard slice at the edge and nothing of it above; never left as a body without its title.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBoxArtSourceFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @get:Rule
    val utc = UtcTimeZone()

    private fun open(index: Int) {
        RuntimeEnvironment.setQualifiers("w832dp-h468dp-land-xhdpi")
        val content = AccountShotContent.of(AccountStatus.SignedIn("plumtester"))
        compose.setContent {
            PlumTheme(TableText.Graphite) {
                val settings = settingsForShot().let { it.copy(library = it.library.copy(boxArtSource = null)) }
                SettingsAt(AccountShotContent.focusOn(content, SettingsOption.BoxArtSource, PanelPage.BoxArtSource).copy(panelIndex = index), settings, content)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun badgeSharesTheTitleLineAndTheDetailKeepsTheWidth() {
        open(0)
        val badge = compose.onNodeWithText("Current", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("Box art and game info") and hasAnyAncestor(hasTestTag(SettingsTags.panelItem(0))), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val detail = compose.onNodeWithText("Box art plus year", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("badge $badge must share the title's line $title", badge.top < title.bottom && title.top < badge.bottom)
        assertTrue("badge $badge must end above the detail $detail", badge.bottom <= detail.top)
        assertTrue("detail $detail must reach the badge's right edge ${badge.right}", detail.right >= badge.right - 1.dp)
    }

    @Test
    fun theOptionAboveShowsUnderTheFadeWhenTheSecondIsFocused() {
        open(1)
        val first = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("part of the first option $first shows under the list's top ${list.top}", first.bottom >= list.top + EdgePeek - 1.dp)
    }

    @Test
    fun anOptionCutByTheTopEdgeFadesIn() {
        open(2)
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cut = compose.onNodeWithTag(SettingsTags.panelItem(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("option 0 $cut must cross the list's top edge ${list.top}", cut.top < list.top && cut.bottom > list.top)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val bg = image.getPixel(2, image.height / 2)
        val x0 = (cut.left.value * density).toInt()
        val x1 = (cut.right.value * density).toInt()
        val y0 = (list.top.value * density).toInt()
        val y1 = (cut.bottom.value * density).toInt()
        assertTrue("the cut part is $y0 to $y1", y1 > y0)
        // The list's top edge is plain page: the option's ink comes in over the band below it, no hard slice.
        var atEdge = 0
        for (y in y0 until minOf(y0 + density.toInt(), y1)) for (x in x0 until x1) if (far(image.getPixel(x, y), bg)) atEdge++
        assertEquals("pixels of the cut option in the first dp at the list's top edge", 0, atEdge)
        // Deeper in, the option is faded, not hidden: some of its ink shows.
        var inside = 0
        for (y in y0 + density.toInt() until y1) for (x in x0 until x1) if (far(image.getPixel(x, y), bg)) inside++
        assertTrue("the cut option shows none of its ink below the first dp: it is hidden, not faded", inside > 0)
        // And nothing of it above the list's window.
        val above = y0 - (SlabDimens.listEndRoom.value * density).toInt() / 2
        var stray = 0
        for (y in above until y0) for (x in x0 until x1) if (far(image.getPixel(x, y), bg)) stray++
        assertEquals("pixels of the cut option above the list's window", 0, stray)
    }

    /** Ink, not the slab's soft glow or a gradient: a channel off by more than 40. */
    private fun far(
        a: Int,
        b: Int,
    ) = listOf(0, 8, 16).any { kotlin.math.abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) > 40 }
}
