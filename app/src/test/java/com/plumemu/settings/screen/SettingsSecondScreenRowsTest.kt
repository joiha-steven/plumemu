package com.plumemu.settings.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rows on screen: absent without a second screen; with one, the switch, and In game while it is on; labels, values, explanations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSecondScreenRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int) = context.getString(id)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))

    /** Settings › Home screen with [screen] found and [stored] settings, as the graph builds its content. */
    private fun show(
        screen: SecondScreen?,
        option: SettingsOption,
        stored: PlumSettings = settings,
    ) {
        val content = SettingsContent().withSecondScreen(screen, stored.secondScreen)
        val rows = SettingsSection.HomeScreen.rowsIn(content)
        val focus =
            SettingsFocus(
                column = SettingsColumn.Options,
                section = SettingsSection.HomeScreen,
                rememberedOption = mapOf(SettingsSection.HomeScreen to rows.indexOf(option).coerceAtLeast(0)),
            )
        compose.setContent { PlumTheme { SettingsAt(focus, stored, content) } }
    }

    @Test
    fun withoutASecondScreenHomeScreenHasNoSuchRows() {
        show(null, SettingsOption.ShowBattery)
        // The focused row's label shows twice: the row and its panel's title.
        compose.onAllNodesWithText(text(R.string.settings_show_battery))[0].assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_second_screen)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_second_screen_in_game)).assertDoesNotExist()
    }

    @Test
    fun withAScreenTheSwitchRowExplainsItself() {
        show(SecondScreen(4, "Screen-2", ScreenKind.BuiltIn), SettingsOption.SecondScreen)
        compose.onAllNodesWithText(text(R.string.settings_second_screen)).assertCountEquals(2) // the row and its panel's title
        compose.onNodeWithText(text(R.string.settings_second_screen_in_game)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_second_screen_body)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_value_choice, text(R.string.settings_second_screen_clock))).assertIsDisplayed()
    }

    @Test
    fun theInGameRowShowsBlackAndItsExplanation() {
        val stored = settings.copy(secondScreen = SecondScreenSettings(enabled = true, inGame = InGameSetting.Black))
        show(SecondScreen(2, "Overlay #1", ScreenKind.Unsure), SettingsOption.SecondScreenInGame, stored)
        compose.onNodeWithText(context.getString(R.string.settings_value_choice, text(R.string.settings_second_screen_black))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_second_screen_in_game_body)).assertIsDisplayed()
        // The in-game card's once-ever note, said here too so it is never only on the second screen.
        val note = text(R.string.in_game_drift_note).replaceFirstChar { it.lowercase() }
        assertTrue("the explanation says why the card moves", note in text(R.string.settings_second_screen_in_game_body))
    }

    @Test
    fun withTheSwitchOffInGameIsNotListed() {
        val stored = settings.copy(secondScreen = SecondScreenSettings(enabled = false))
        show(SecondScreen(4, "Screen-2", ScreenKind.BuiltIn), SettingsOption.SecondScreen, stored)
        compose.onAllNodesWithText(text(R.string.settings_second_screen)).assertCountEquals(2)
        compose.onNodeWithText(text(R.string.settings_second_screen_in_game)).assertDoesNotExist()
    }
}
