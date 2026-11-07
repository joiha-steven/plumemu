package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * How Settings' lists and panels meet their edges (design/rules.md › Lists that scroll), read from drawn pixels on Paper, where a shadow cut
 * flat shows most: the focused row's shadow fades out on every side at every list position, a focused card stays whole with a margin, a text
 * block never rests half gone, and every panel that scrolls fades at the edge it continues past.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SettingsEdgesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var focus by mutableIntStateOf(0)

    private fun density() = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun px(dp: Dp) = dp.value * density()

    private fun stage(list: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            PlumTheme(TableText.Paper) {
                CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) {
                        Box(Modifier.padding(start = 80.dp, top = 80.dp).width(320.dp)) { list() }
                    }
                }
            }
        }
    }

    /** The two lists Settings has, with rows that draw nothing but the focus: any step in the pixels around the focus is the shadow's. */
    private fun panelList(
        rows: Int,
        window: Dp,
    ) = stage {
        Box(Modifier.height(window)) {
            PanelLazyList(focus, Modifier) {
                itemsIndexed(List(rows) { it }) { index, _ -> PanelItem(title = BLANK, isFocused = index == focus, modifier = Modifier.height(ROW).testTag(rowTag(index))) }
            }
        }
    }

    private fun column(
        rows: Int,
        window: Dp,
    ) = stage {
        Box(Modifier.height(window)) {
            KeepFocusedColumn({ focus }, rows, PlumDimens.listRowGap, Modifier, fadeTag = "edges", endRoom = SlabDimens.listEndRoom) { place ->
                repeat(rows) { index -> PanelItem(title = BLANK, isFocused = index == focus, modifier = place(index).height(ROW).testTag(rowTag(index))) }
            }
        }
    }

    private fun rowTag(index: Int) = "row_$index"

    /** Down the page beside and through the focused row's slab, outside the slab's own rim: no pixel steps from the next as a clip line would. */
    private fun shadowProblems(label: String): List<String> {
        compose.waitForIdle()
        // A test draws only when captured, and the pill starts its glide in its first draw on the new row: draw, let it land, then read.
        compose.onRoot().captureToImage()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = compose.onNodeWithTag(rowTag(focus), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val over = ((SlabDimens.height - ROW) / 2).coerceAtLeast(0.dp)
        val slabTop = px(row.top - over).toInt()
        val slabBottom = px(row.bottom + over).toInt()
        val problems = mutableListOf<String>()
        for (x in listOf(px(row.left).toInt() - SIDE_PX, px(row.left + (row.right - row.left) / 2).toInt())) {
            for (y in 0 until pixels.height - 1) {
                if (x > px(row.left).toInt() && y in slabTop - SLAB_RIM_PX..slabBottom + SLAB_RIM_PX) continue
                val step = stepBetween(pixels[x, y], pixels[x, y + 1])
                if (step > MAX_STEP) problems += "$label: step ${(step * 255).toInt()}/255 at x=$x y=$y (slab $slabTop..$slabBottom)"
            }
        }
        return problems.take(3)
    }

    private fun stepBetween(
        a: Color,
        b: Color,
    ) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    @Test
    fun aListOfOneRowFadesItsShadowOut() {
        focus = 0
        panelList(rows = 1, window = ROW)
        val problems = shadowProblems("one row")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aPanelListFadesItsShadowOutAtEveryPosition() {
        focus = 0
        panelList(rows = 8, window = 200.dp)
        val problems = (0 until 8).flatMap { focus = it; shadowProblems("panel row $it") }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aColumnFadesItsShadowOutAtEveryPositionGoingDownAndUp() {
        focus = 0
        column(rows = 8, window = 200.dp)
        val problems = ((0 until 8) + (7 downTo 0)).flatMap { focus = it; shadowProblems("column row $it") }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aFocusedCardStaysWholeWithAMargin() {
        RuntimeEnvironment.setQualifiers("w768dp-h432dp-land-xhdpi")
        val content = AccountShotContent.of(AccountStatus.SignedIn("plumtester"))
        val at = AccountShotContent.focusOn(content, SettingsOption.BoxArtSource, PanelPage.BoxArtSource)
        var problems = emptyList<String>()
        focus = 0
        compose.setContent { PlumTheme(TableText.Paper.copy(textSize = TextSize.Largest)) { SettingsAt(at.copy(panelIndex = focus), content = content) } }
        problems =
            (0..2).mapNotNull { index ->
                focus = index
                compose.waitForIdle()
                val panel = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val row = compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
                val keeps = row.bottom <= panel.bottom - SlabDimens.glowReach && row.top >= panel.top
                if (keeps) null else "card $index ${row.top}..${row.bottom} in panel ${panel.top}..${panel.bottom}"
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aPanelTitleIsWholeOrGone() {
        RuntimeEnvironment.setQualifiers("w768dp-h432dp-land-xhdpi")
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = RaAccountStatus.SignedIn("plumtester", 1234))
        val row = SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)
        val at = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Accounts, rememberedOption = mapOf(SettingsSection.Accounts to row), page = PanelPage.RaAccount)
        compose.setContent { PlumTheme(TableText.Paper.copy(textSize = TextSize.Largest)) { SettingsAt(at, settingsForShot(), content) } }
        compose.waitForIdle()
        val panel = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("RetroAchievements") and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val whole = title.top >= panel.top - 1.dp
        val gone = title.bottom <= panel.top + PlumDimens.listFadeBand
        assertTrue("the title ${title.top}..${title.bottom} is half gone at the panel's top ${panel.top}", whole || gone)
    }

    @Test
    fun aPlainPanelThatContinuesFadesThere() {
        RuntimeEnvironment.setQualifiers("w576dp-h432dp-land-xhdpi")
        val content = AccountShotContent.of(AccountStatus.SignedIn("plumtester"))
        val licenses = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to SettingsSection.About.rowsIn(content).indexOf(SettingsOption.Licenses)), page = PanelPage.Licenses, panelIndex = 0)
        compose.setContent { PlumTheme(TableText.Graphite.copy(textSize = TextSize.Large)) { SettingsAt(licenses, content = content) } }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.LICENSE_LIST + FADE_BOTTOM_SUFFIX, useUnmergedTree = true).assertExists()
    }

    private companion object {
        val ROW = 60.dp
        const val BLANK = "\u00A0"
        const val SIDE_PX = 8
        const val SLAB_RIM_PX = 6
        const val SETTLE_MS = 600L

        /** The same limit as `SettingsListEndsBase`: past it a shadow reads as a line. */
        const val MAX_STEP = 5.5f / 255f
    }
}
