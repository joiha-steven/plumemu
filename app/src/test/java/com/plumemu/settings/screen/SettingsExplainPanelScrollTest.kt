package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An explanation that runs past its panel fades, and its end is reached by gamepad and by finger; one that fits offers nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsExplainPanelScrollTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private var body = R.string.settings_privacy_body

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, settings, geometry) }
        compose.waitForIdle()
    }

    private fun show(
        option: SettingsOption,
        textSize: TextSize = TextSize.Largest,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, optionsAt(option)).also { it.content = SettingsContent(gameCount = 1, hasStatistics = true) } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bodyEnd() = compose.onNode(hasText(RuntimeEnvironment.getApplication().getString(body)), useUnmergedTree = true).getUnclippedBoundsInRoot().bottom

    private fun panelEnd() = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom

    private fun assertTheEndShows() {
        assertTrue("the text ends at ${bodyEnd()}, inside the panel's end ${panelEnd()}", bodyEnd() <= panelEnd())
        assertFalse("no fade past the end", exists(SettingsPanelTags.EXPLAIN + FADE_BOTTOM_SUFFIX))
        assertTrue("a fade over the text gone above", exists(SettingsPanelTags.EXPLAIN + FADE_TOP_SUFFIX))
    }

    private fun assertTheKeysReachTheEnd() {
        repeat(MAX_PRESSES) { if (model.licenseCanScroll(true)) press(Action.Move(Direction.Down)) }
        assertFalse("nothing is left below", model.licenseCanScroll(true))
        assertTheEndShows()
    }

    @Test
    fun theEndOfPrivacyIsReachedByKeys() {
        show(SettingsOption.Privacy)
        assertTrue("the end starts past the panel", bodyEnd() > panelEnd())
        assertTrue("it fades where it continues", exists(SettingsPanelTags.EXPLAIN + FADE_BOTTOM_SUFFIX))
        assertTrue("the hint offers More", exists(SettingsTags.HINT_MORE))
        press(Action.Move(Direction.Right))
        assertEquals("Right enters the panel", PanelPage.ReadOnly, model.focus.page)
        assertTheKeysReachTheEnd()
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Privacy, model.focusedOption)
    }

    @Test
    fun theEndOfPrivacyIsReachedByFinger() {
        show(SettingsOption.Privacy)
        repeat(MAX_SWIPES) {
            compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTheEndShows()
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun aSwitchsTextIsReachedByKeysAt832() {
        body = R.string.settings_usage_statistics_body
        show(SettingsOption.UsageStatistics)
        press(Action.Move(Direction.Right))
        assertEquals(PanelPage.ReadOnly, model.focus.page)
        assertTheKeysReachTheEnd()
    }

    @Test
    fun aChoiceKeepsRightForItsValueAndEntersByY() {
        body = R.string.settings_sort_games_body
        show(SettingsOption.SortGames)
        assertTrue("this case needs text past the panel", bodyEnd() > panelEnd())
        assertTrue("the hint offers More", exists(SettingsTags.HINT_MORE))
        press(Action.Move(Direction.Right))
        assertEquals("Right steps the value", null, model.focus.page)
        press(Action.ToggleFavorite)
        assertEquals("Y enters the panel", PanelPage.ReadOnly, model.focus.page)
        assertTheKeysReachTheEnd()
    }

    @Test
    fun aTextThatFitsOffersNoMore() {
        show(SettingsOption.Vibration, TextSize.Small)
        assertFalse("this case needs text that fits", exists(SettingsPanelTags.EXPLAIN + FADE_BOTTOM_SUFFIX))
        assertFalse(exists(SettingsTags.HINT_MORE))
        press(Action.Move(Direction.Right))
        assertEquals(SettingsColumn.Options, model.focus.column)
        press(Action.ToggleFavorite)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun aRowWithAnotherPanelIsNotOfferedMorePastPrivacy() {
        show(SettingsOption.Privacy)
        assertTrue(model.panelTextScrolls)
        press(Action.Move(Direction.Up))
        assertFalse("the panel that scrolled is gone", model.panelTextScrolls)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun onANarrowScreenTheStopReachesTheEnd() {
        show(SettingsOption.Privacy)
        press(Action.Move(Direction.Right))
        assertEquals(PanelPage.ReadOnly, model.focus.page)
        assertTheKeysReachTheEnd()
    }

    private companion object {
        const val MAX_PRESSES = 24
        const val MAX_SWIPES = 8
    }
}
