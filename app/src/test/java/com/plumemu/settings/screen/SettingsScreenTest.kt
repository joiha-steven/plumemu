package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainScreen
import com.plumemu.R
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.settings.toThemeSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.GamePanelTags
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings inside the main screen: START opens it, Back walks back to the main screen with its
 * focus kept, every change applies at once, and each kind of control works by button and by touch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true)))
    private var resetsLibrary = 0
    private lateinit var router: InputRouter

    private val host =
        SettingsHost(
            versionName = TEST_VERSION,
            update = { settings.value = it(settings.value) },
            resetAllSettings = { settings.value = settings.value.resetKeepingFlags() },
            resetLibrary = { resetsLibrary++ },
        )

    private fun showMainScreen() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
        }
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun down(times: Int = 1) = repeat(times) { send(Action.Move(Direction.Down)) }

    // What the top bar says, without the configuration's identity hash: the main screen is composed anew after Settings.
    private fun topBar(): String = compose.onNodeWithTag(HomeTags.TOP_BAR).fetchSemanticsNode().config.toString().substringAfter('{')

    private fun hintKey(
        tag: String,
        letter: String,
    ) = compose.onNode(hasText(letter) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    @Test
    fun startOpensSettingsAndBackReturnsToTheMainScreenWithItsFocus() {
        showMainScreen()
        down(2)
        send(Action.Move(Direction.Right))
        val before = topBar()
        send(Action.Move(Direction.Left), Action.Menu)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
        // Not composed at all under Settings: each colour change would recompose it (measured on Android 6).
        compose.onNodeWithTag(HomeTags.TOP_BAR).assertDoesNotExist()

        send(Action.Move(Direction.Down), Action.Enter) // Appearance › options
        compose.onNodeWithText(context.getString(R.string.settings_crumb, context.getString(R.string.settings_title), context.getString(R.string.settings_section_appearance)))
            .assertIsDisplayed()
        send(Action.Back, Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        send(Action.Move(Direction.Right))
        assertEquals("the same system as before Settings", before, topBar())
    }

    private fun topBarTexts(part: String) =
        compose.onAllNodes(hasText(part, substring = true) and hasAnyAncestor(hasTestTag(HomeTags.TOP_BAR)), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun showButtonHintsOffHidesTheMainScreensHintsButNotSettings() {
        showMainScreen()
        compose.onNodeWithTag(HomeTags.HINT_BAR).assertExists()
        send(Action.Menu)
        down(3) // Controls
        send(Action.Enter, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter) // Show button hints › off (after Push right to play)
        assertFalse(settings.value.controls.showButtonHints)
        compose.onNodeWithTag(SettingsTags.HINT_BACK).assertIsDisplayed()
        send(Action.Back, Action.Back)
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.HINT_BAR).assertDoesNotExist()
        // START still opens Settings, whose hints show the way back.
        send(Action.Menu)
        compose.onNodeWithTag(SettingsTags.HINT_BACK).assertIsDisplayed()
    }

    @Test
    fun aLongPressOnTheSystemColumnOpensSettingsWithHintsOnAndOff() {
        showMainScreen()
        for (hints in listOf(true, false)) {
            settings.value = settings.value.copy(controls = settings.value.controls.copy(showButtonHints = hints))
            compose.waitForIdle()
            compose.onNodeWithTag(HomeTags.system(4), useUnmergedTree = true).performTouchInput { longClick() }
            compose.waitForIdle()
            compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
            compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick() // "B Close"
            compose.waitForIdle()
            compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
        }
    }

    @Test
    fun aLongPressOnAGameStillOpensItsMenu() {
        settings.value = settings.value.copy(controls = settings.value.controls.copy(showButtonHints = false))
        showMainScreen()
        down(3)
        send(Action.Move(Direction.Right))
        compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun clockAndShowBatteryOffLeaveTheTopBar() {
        settings.value = settings.value.copy(homeScreen = settings.value.homeScreen.copy(showClock = true))
        showMainScreen()
        // The clock is the only text of the bar with a colon ("9:41 AM"); the place has none.
        assertEquals(1, topBarTexts(":"))
        settings.value = settings.value.copy(homeScreen = settings.value.homeScreen.copy(showClock = false, showBattery = false))
        compose.waitForIdle()
        assertEquals(0, topBarTexts(":"))
        assertEquals(0, topBarTexts("%"))
        compose.onNodeWithTag(HomeTags.TOP_BAR).assertIsDisplayed()
    }

    @Test
    fun theSettingsHintOpensSettingsByTouch() {
        showMainScreen()
        compose.onNodeWithText(context.getString(R.string.home_hint_settings)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick() // "B Close"
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun aChangeAppliesAtOnceToTheScreen() {
        showMainScreen()
        send(Action.Menu)
        down()
        send(Action.Enter, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Right)) // under Colors, Background, Text color and Accent: Font › Serif
        assertEquals(FontSetting.Serif, settings.value.appearance.font)
        compose.onNodeWithText(context.getString(R.string.settings_value_choice, context.getString(R.string.settings_font_serif))).assertIsDisplayed()
    }

    @Test
    fun theConfirmButtonSwitchFlipsTheHintLetters() {
        showMainScreen()
        send(Action.Menu)
        down(SettingsSection.Advanced.ordinal) // Advanced, after Statistics
        send(Action.Enter)
        hintKey(SettingsTags.HINT_CONFIRM, "A").assertExists()
        hintKey(SettingsTags.HINT_BACK, "B").assertExists()
        send(Action.Enter)
        assertEquals(ConfirmButtonSetting.Bottom, settings.value.controls.confirmButton)
        hintKey(SettingsTags.HINT_CONFIRM, "B").assertExists()
        hintKey(SettingsTags.HINT_BACK, "A").assertExists()
    }

    @Test
    fun touchFocusesARowThenActsAndValuesAndSwitchesActOnTheFirstTap() {
        showMainScreen()
        send(Action.Menu)
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Advanced)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Advanced)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Animations)).performClick()
        compose.waitForIdle()
        assertFalse("a switch flips on a tap", settings.value.advanced.animations)

        compose.onNodeWithTag(SettingsTags.HINT_SECTION).performClick() // "L1 R1 Section" → About
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Version)).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick()
        compose.waitForIdle()
        // Statistics sits between Home screen and Advanced: the hint walks through it.
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.HomeScreen)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.HomeScreen)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.HINT_SECTION).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.statistic(0)).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_SECTION).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Animations)).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HINT_BACK).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.section(SettingsSection.Appearance)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.value(SettingsOption.Font)).performClick()
        compose.waitForIdle()
        assertEquals("a tap on ‹ › goes to the next value", "Serif", settings.value.appearance.font.name)

        compose.onNodeWithTag(SettingsTags.option(SettingsOption.Background)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.panelItem(0)).performClick() // Graphite (the default is Black), in the panel beside
        compose.waitForIdle()
        assertEquals(ColorSetting.Preset("Graphite"), settings.value.appearance.background)
    }

    @Test
    fun resetLibraryRunsOnlyAfterItsConfirmStep() {
        showMainScreen()
        send(Action.Menu)
        down(SettingsSection.Advanced.ordinal) // Advanced, after Statistics
        send(Action.Enter)
        // Reset library, the last row of Advanced (Back up… sits before the resets).
        down(SettingsSection.Advanced.options.indexOf(SettingsOption.ResetLibrary))
        send(Action.Enter, Action.Enter) // Cancel
        assertEquals(0, resetsLibrary)
        send(Action.Enter, Action.Move(Direction.Right), Action.Enter)
        assertEquals(1, resetsLibrary)
        compose.onNodeWithTag(SettingsPanelTags.DONE).assertIsDisplayed()
    }

    @Test
    fun aStoredUnreadableCombinationIsDrawnAdjustedAndNeverRewrittenByOpeningAPanel() {
        settings.value = settings.value.copy(appearance = settings.value.appearance.copy(background = ColorSetting.Preset("Paper")))
        showMainScreen()
        send(Action.Menu)
        down()
        send(Action.Enter, Action.Move(Direction.Down)) // Background, under Colors
        assertEquals("opening the panel changes nothing stored: Light text on Paper stays", TextColorSetting.Light, settings.value.appearance.textColor)
        assertEquals("but it is drawn dark so the screen can be read", com.plumemu.coreui.theme.TextMode.Dark, settings.value.toThemeSettings().textMode)
    }

    @Test
    fun settingsLeavingTheCompositionMidPreviewPutsBackTheOpeningColour() {
        val present = mutableStateOf(true)
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            if (present.value) {
                MainScreen(settings.value, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
            }
        }
        send(Action.Move(Direction.Left), Action.Menu) // Settings
        send(Action.Move(Direction.Down), Action.Enter, Action.Move(Direction.Down), Action.Enter) // Appearance › Background (under Colors) › presets
        // Down to the last row and right to its end, the "+" (however many swatches a row holds), then into the palette and up onto a swatch.
        send(*Array(6) { Action.Move(Direction.Down) }, *Array(6) { Action.Move(Direction.Right) })
        val opening = settings.value.appearance.background // presets apply as the cursor reaches them
        send(Action.Enter, Action.Move(Direction.Up))
        assertTrue("a swatch is previewed", settings.value.appearance.background is ColorSetting.Custom)
        compose.runOnIdle { present.value = false } // as when the Activity is recreated
        compose.waitForIdle()
        assertEquals(opening, settings.value.appearance.background)
    }
}
