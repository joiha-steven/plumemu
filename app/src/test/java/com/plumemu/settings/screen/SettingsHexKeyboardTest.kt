package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.ImeProbe
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import androidx.compose.ui.test.performImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The custom colour's hex field and the Android keyboard: the panel opens on the
 * hex row without a keyboard; A starts typing and only then does it come up, named and not full
 * screen; the keyboard's Done applies the code once. Android 9 (sdk 28).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsHexKeyboardTest : SettingsModelHarness() {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val ime = ImeProbe(compose)
    private val entered = mutableListOf<String>()

    private fun showAccentHexRow() {
        // Android's keyboard (Settings › Controls › Keyboard); Plumemu's: SettingsHexOwnKeyboardTest.
        useAndroidKeyboard()
        // Typed accents are dark codes: they read on Paper, not on Graphite (accents under 3:1 are refused).
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark))
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        repeat(6) { press(Action.Move(Direction.Right)) }
        press(Action.Move(Direction.Down))
        press(Action.Enter) // "+": the accent palette, on a swatch
        repeat(10) { press(Action.Move(Direction.Down)) } // the hex code is the last row
        assertEquals(PanelPage.AccentCustom, model.focus.page)
        val touch =
            SettingsTouch(
                onSection = {},
                onOption = {},
                onValue = {},
                onPanelItem = {},
                onHexEntered = {
                    entered += it
                    model.onHexEntered(it)
                },
                onHexText = model::onHexTextChange,
                onHint = {},
            )
        compose.setContent {
            ime.view = LocalView.current
            PlumTheme { SettingsLayout(actions.shown, model, touch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY }) }
        }
        ime.settle()
    }

    @Test
    fun theHexRowOpensWithoutTheKeyboardAndAStartsTypingNamed() {
        showAccentHexRow()
        assertFalse("no field, no keyboard", model.isEditingHex)
        assertFalse(ime.keyboardShows)
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        assertTrue(model.isEditingHex)
        assertTrue("A opens the keyboard", ime.keyboardShows)
        ime.assertInlineKeyboardNamed(ime.context.getString(R.string.settings_hex_hint))
    }

    @Test
    fun aBringsTheKeyboardBackAfterTheSystemClosedItAndDoesNotApplyTheCode() {
        showAccentHexRow()
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        compose.runOnIdle { model.onHexTextChange("1A2B3C") }
        ime.closeKeyboardFromTheSystem()
        assertFalse(ime.keyboardShows)
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        assertTrue("A on the hex field brings the keyboard back", ime.keyboardShows)
        assertTrue("and does not use the code", model.isEditingHex)
        assertEquals(emptyList<String>(), entered)
    }

    @Test
    fun theHintBarSaysATypeOnTheHexRowAndWhileTyping() {
        showAccentHexRow()
        val type = ime.context.getString(R.string.account_hint_type)
        compose.onNode(hasTestTag(SettingsTags.HINT_CONFIRM) and hasAnyDescendant(hasText(type)), useUnmergedTree = true).assertExists()
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        compose.onNode(hasTestTag(SettingsTags.HINT_CONFIRM) and hasAnyDescendant(hasText(type)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun downToUseHidesTheKeyboardUpOpensNothingAndAReopensIt() {
        showAccentHexRow()
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        assertTrue(ime.keyboardShows)
        compose.runOnIdle { press(Action.Move(Direction.Down)) }
        ime.settle()
        assertFalse("the keyboard goes with the cursor", ime.keyboardShows)
        compose.runOnIdle { press(Action.Move(Direction.Up)) }
        ime.settle()
        assertFalse("coming back opens nothing", ime.keyboardShows)
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        assertTrue(ime.keyboardShows)
    }

    @Test
    fun aTapOnUseAppliesTheTypedCodeOnce() {
        showAccentHexRow()
        compose.runOnIdle { press(Action.Enter) }
        compose.runOnIdle { model.onHexTextChange("1A2B3C") }
        ime.settle()
        compose.onNodeWithTag(SettingsTags.HEX_USE).performClick()
        ime.settle()
        assertEquals(listOf("1A2B3C"), entered)
        assertFalse(model.isEditingHex)
    }

    @Test
    fun useThisColourLinesUpWithTheHexRowUnderIt() {
        showAccentHexRow()
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        val row = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot()
        val use = compose.onNodeWithTag(SettingsTags.HEX_USE).getUnclippedBoundsInRoot()
        assertEquals("same left edge", row.left.value, use.left.value, 0.5f)
        assertEquals("same right edge", row.right.value, use.right.value, 0.5f)
        assertEquals("same height", (row.bottom - row.top).value, (use.bottom - use.top).value, 0.5f)
        assertEquals("one title-line gap under the hex row", (row.bottom + PlumDimens.titleLineGap).value, use.top.value, 0.5f)
    }

    @Test
    fun theKeyboardsDoneAppliesTheCodeOnce() {
        showAccentHexRow()
        compose.runOnIdle { press(Action.Enter) }
        ime.settle()
        compose.runOnIdle { model.onHexTextChange("1A2B3C") }
        ime.settle()
        ime.taggedField(SettingsTags.HEX_FIELD).performImeAction()
        ime.settle()
        assertEquals(listOf("1A2B3C"), entered)
        assertFalse(model.isEditingHex)
    }
}
