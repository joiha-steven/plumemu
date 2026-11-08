package com.plumemu.settings.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.bringIntoViewWhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 * Where a [PanelScrollColumn] rests: a text block is never left half gone by a key, a finger is never fought, Up always moves, the text above the first
 * focus stop comes back, room added for a rest goes when it is no longer needed, and an interrupted settle does not end the settling.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsPanelRestsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var scroll: ScrollState
    private lateinit var scope: CoroutineScope
    private var focused by mutableIntStateOf(-1)
    private var presses by mutableIntStateOf(0)
    private var twoHold by mutableStateOf(false)

    private fun column(stops: Int) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = true)) {
                scroll = rememberScrollState()
                scope = rememberCoroutineScope()
                Box(Modifier.width(300.dp).height(COLUMN_DP.dp)) {
                    PanelScrollColumn(GAP_DP.dp, scroll = scroll, tag = COLUMN, keyMoves = presses) {
                        repeat(BLOCKS) { Box(Modifier.panelBlock().fillMaxWidth().height(BLOCK_DP.dp)) }
                        repeat(stops) { Box(Modifier.bringIntoViewWhen(focused == it || (twoHold && it == 0)).testTag("stop$it").fillMaxWidth().height(STOP_DP.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(by: Int) {
        compose.runOnIdle {
            presses++
            scope.launch { scroll.animateScrollTo(scroll.value + by) }
        }
        compose.waitForIdle()
    }

    private fun px(dp: Int) = (dp * RuntimeEnvironment.getApplication().resources.displayMetrics.density).toInt()

    /** Blocks start at this many px in the column. */
    private fun blockTop(i: Int) = i * px(BLOCK_DP + GAP_DP)

    /** No block has more than its tail above the window's top. */
    private fun assertNoBlockHalfGone() {
        val at = scroll.value
        for (i in 0 until BLOCKS) {
            val cut = at - blockTop(i)
            assertTrue("block $i is cut by $cut px at $at", cut <= 0 || cut >= px(BLOCK_DP) - px(EDGE_DP))
        }
    }

    private fun drag(by: Float) {
        compose.onNodeWithTag(COLUMN, useUnmergedTree = true).performTouchInput { swipe(center, center.copy(y = center.y + by), durationMillis = 300) }
        compose.waitForIdle()
    }

    @Test
    fun aFingerDraggingBackIsNotFought() {
        column(stops = 2)
        focused = 1
        compose.waitForIdle()
        val rest = scroll.value
        assertTrue("the column scrolled to the stop", rest > px(BLOCK_DP))
        drag(px(40).toFloat())
        val first = scroll.value
        assertTrue("the drag took the column back: $rest to $first", first < rest - px(20))
        drag(px(40).toFloat())
        val second = scroll.value
        assertTrue("a second short drag adds to the first: $first to $second", second < first - px(20))
    }

    @Test
    fun aFingerDraggingOnIsNotFought() {
        column(stops = 2)
        drag(-px(40).toFloat())
        val first = scroll.value
        drag(-px(40).toFloat())
        assertTrue("two short drags add up, nothing jumps: $first to ${scroll.value}", scroll.value in first + px(20)..first + px(60))
    }

    @Test
    fun aKeyNeverLeavesABlockHalfGoneGoingEitherWay() {
        column(stops = 0)
        repeat(4) {
            press(px(BLOCK_DP / 2))
            assertNoBlockHalfGone()
        }
        val low = scroll.value
        assertTrue("Down reached the end part: $low", low >= blockTop(2) - px(EDGE_DP))
        var at = low
        while (at > 0) {
            press(-px(BLOCK_DP / 2))
            assertNoBlockHalfGone()
            assertTrue("Up moved: $at to ${scroll.value}", scroll.value < at)
            at = scroll.value
        }
    }

    @Test
    fun roomAddedForARestGoesWhenNoLongerNeeded() {
        column(stops = 0)
        val natural = scroll.maxValue
        repeat(4) { press(px(BLOCK_DP / 2)) }
        assertTrue("the column grew to reach a rest: ${scroll.maxValue} over $natural", scroll.maxValue > natural)
        var presses = 0
        while (scroll.value > 0 && presses++ < MAX_PRESSES) press(-px(BLOCK_DP / 2))
        assertEquals("no blank scroll is left", natural, scroll.maxValue)
    }

    @Test
    fun aSettleInterruptedByAPressDoesNotEndTheSettling() {
        column(stops = 0)
        press(px(BLOCK_DP / 2))
        assertNoBlockHalfGone()
        // Two presses under 200 ms: the second lands while the settle after the first still runs.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            presses++
            scope.launch { scroll.scrollTo(scroll.value + px(BLOCK_DP / 2)) }
        }
        var frames = 0
        while (!scroll.isScrollInProgress && frames++ < MAX_PRESSES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue("the settle runs: at ${scroll.value}, after $frames frames, max ${scroll.maxValue}, block ${px(BLOCK_DP)}", scroll.isScrollInProgress)
        compose.runOnIdle {
            presses++
            scope.launch { scroll.scrollTo(scroll.value + px(BLOCK_DP / 2)) }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNoBlockHalfGone()
        press(-px(BLOCK_DP / 3))
        assertNoBlockHalfGone()
    }

    @Test
    fun upToTheFirstStopBringsTheTextAboveItBack() {
        column(stops = 2)
        focused = 0
        compose.waitForIdle()
        val opening = scroll.value
        focused = 1
        compose.waitForIdle()
        assertTrue("Down went on: ${scroll.value} from $opening", scroll.value >= opening)
        focused = 0
        compose.waitForIdle()
        assertEquals("back on the first stop as much of the text shows as with the stop on screen", opening, scroll.value)
        assertTrue("the stop is on screen", compose.onNodeWithTag("stop0", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom <= COLUMN_DP.dp)
    }

    private var pageSettings by mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true)))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private class Scene(val focus: SettingsFocus, val content: SettingsContent, val font: PlumFont)

    private var scene by mutableStateOf<Scene?>(null)

    /** A tile and a row of one panel can hold the focus at once: the column settles on the lower one, it does not chase the upper one for ever. */
    @Test
    fun twoStopsHoldingTheFocusEndTheSettling() {
        column(stops = 2)
        focused = 1
        compose.waitForIdle()
        val rest = scroll.value
        twoHold = true
        compose.waitForIdle()
        assertTrue("the lower stop stays on screen: $rest to ${scroll.value}", scroll.value >= rest - px(STOP_DP))
    }

    private fun panel(
        focus: SettingsFocus,
        content: SettingsContent,
        font: PlumFont,
    ) {
        val isFirst = scene == null
        scene = Scene(focus, content, font)
        if (isFirst) {
            compose.setContent {
                val now = scene ?: return@setContent
                PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                    key(now) {
                        geometry = settingsGeometry(PlumTheme.layout)
                        model = remember { SettingsModel(NoActions, now.focus).also { it.content = now.content } }
                        SettingsLayout(pageSettings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(direction: Direction) {
        compose.runOnIdle { model.onAction(Action.Move(direction), pageSettings, geometry) }
        compose.waitForIdle()
    }

    private fun confirmBodyComesBackOnUp(
        option: SettingsOption,
        page: PanelPage,
        body: Int,
    ) = listOf(PlumFont.Jost).forEach { font ->
        val content = SettingsContent()
        val section = SettingsSection.entries.firstOrNull { option in it.rowsIn(content) }
        val open = section?.let { SettingsFocus(column = SettingsColumn.Panel, section = it, rememberedOption = mapOf(it to it.rowsIn(content).indexOf(option)), page = page) }
        panel(open ?: panelAt(option, page, SettingsModel.CONFIRM_CANCEL), content, font)
        key(Direction.Down)
        if (model.focus.panelIndex != SettingsModel.CONFIRM_RESET) key(Direction.Right)
        assertEquals(SettingsModel.CONFIRM_RESET, model.focus.panelIndex)
        key(Direction.Up)
        if (model.focus.panelIndex != SettingsModel.CONFIRM_CANCEL) key(Direction.Left)
        assertEquals(SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        val text = RuntimeEnvironment.getApplication().getString(body)
        val shown = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$font: the explanation $shown is back in $panel", shown.top >= panel.top - 1.dp && shown.bottom <= panel.bottom)
        val button = compose.onNodeWithTag(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$font: the focused button is whole", button.bottom <= panel.bottom && button.top >= shown.bottom)
    }

    @Test
    fun confirmResetAllsTextComesBackByGamepad() = confirmBodyComesBackOnUp(SettingsOption.ResetAll, PanelPage.ResetAllConfirm, R.string.settings_reset_all_body)

    @Test
    fun confirmResetLibrarysTextComesBackByGamepad() = confirmBodyComesBackOnUp(SettingsOption.ResetLibrary, PanelPage.ResetLibraryConfirm, R.string.settings_reset_library_body)

    @Test
    fun confirmClearRecentsTextComesBackByGamepad() = confirmBodyComesBackOnUp(SettingsOption.ClearRecent, PanelPage.Confirm, R.string.settings_clear_recent_body)

    private fun aboutTop() = compose.onNode(hasText(RuntimeEnvironment.getApplication().getString(R.string.app_name)) and hasAnyAncestor(hasTestTag(SettingsPanelTags.ABOUT)), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun aboutsUpAlwaysMoves() {
        val content = SettingsContent(platformData = PlatformDataVersion(6, "0".repeat(64), "2026-11-01"))
        panel(optionsAt(SettingsOption.Version), content, PlumFont.Jost)
        key(Direction.Right)
        assertEquals("Right enters the panel", PanelPage.ReadOnly, model.focus.page)
        val opening = aboutTop()
        var downs = 0
        while (downs < MAX_PRESSES) {
            val before = aboutTop()
            key(Direction.Down)
            if (aboutTop() >= before) break
            downs++
        }
        assertTrue("the panel scrolls here: can ${model.licenseCanScroll(true)}, top ${aboutTop()} from $opening, presses ${model.licenseScroll}", downs > 0)
        var ups = 0
        while (aboutTop() < opening && ups < MAX_PRESSES) {
            val before = aboutTop()
            key(Direction.Up)
            assertTrue("Up moved from $before: ${aboutTop()}", aboutTop() > before)
            ups++
        }
        assertEquals("Up came back to the top", opening, aboutTop())
    }

    private companion object {
        const val COLUMN = "rests_column"
        const val COLUMN_DP = 300
        const val BLOCK_DP = 200
        const val STOP_DP = 48
        const val GAP_DP = 12
        const val EDGE_DP = 36
        const val BLOCKS = 3
        const val UNTIL_SETTLES_MS = 340L
        const val MAX_PRESSES = 40
    }
}
