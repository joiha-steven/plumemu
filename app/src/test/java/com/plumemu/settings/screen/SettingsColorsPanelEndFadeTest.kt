package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
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
import com.plumemu.coredata.settings.ColorSetting
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Colors' panel on a small screen: what the window cuts at the bottom fades out, and the fade never lies over what has the focus. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorsPanelEndFadeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(appearance = OLD_DEFAULT_LOOK, flags = SetupFlags(setupCompleted = true))
    private var shown = settings
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private val fade = SettingsTags.COLOR_SETS_PANEL + FADE_BOTTOM_SUFFIX

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, shown, geometry) }
        compose.waitForIdle()
    }

    private fun hasFade() = compose.onAllNodesWithTag(fade).fetchSemanticsNodes().isNotEmpty()

    private fun bottomOf(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom

    private fun topOf(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun open(shown: PlumSettings = settings) {
        this.shown = shown
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.Colors)) }
                SettingsLayout(shown, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    private fun assertClearOfTheFade(item: Int) {
        if (hasFade()) assertTrue("item $item ends above the fade", bottomOf(SettingsTags.panelItem(item)) <= topOf(fade))
    }

    @Test
    fun theBackgroundRowStaysClearOfTheFade() {
        open()
        press(Action.Enter)
        repeat(colorSetsPanelEntries(settings).size) { if (model.focus.panelIndex != COLORS_BACKGROUND_ITEM) press(Action.Move(Direction.Down)) }
        repeat(columnsDown()) { if (model.focus.panelIndex != COLORS_BACKGROUND_ITEM) press(Action.Move(Direction.Down)) }
        assertEquals(COLORS_BACKGROUND_ITEM, model.focus.panelIndex)
        assertClearOfTheFade(COLORS_BACKGROUND_ITEM)
    }

    private fun columnsDown() = colorSetsPanelEntries(shown).size

    @Test
    fun aPanelOpenedOnALowTileShowsItClearOfTheFade() {
        val own = settings.withAppearance { it.copy(background = ColorSetting.Custom(0x203050)) }
        open(own)
        press(Action.Enter)
        val low = colorSetsPanelEntries(own).lastIndex
        assertEquals("Custom, the last tile, is in force", low, model.focus.panelIndex)
        assertClearOfTheFade(low)
    }

    @Test
    fun backToARowFromItsPageLeavesItClearOfTheFade() {
        open()
        press(Action.Enter)
        repeat(2 * columnsDown()) { if (model.focus.panelIndex != COLORS_TEXT_ITEM) press(Action.Move(Direction.Down)) }
        press(Action.Move(Direction.Up))
        assertEquals(COLORS_BACKGROUND_ITEM, model.focus.panelIndex)
        press(Action.Move(Direction.Right))
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        press(Action.Back)
        assertEquals(COLORS_BACKGROUND_ITEM, model.focus.panelIndex)
        assertClearOfTheFade(COLORS_BACKGROUND_ITEM)
    }

    @Test
    fun theFadeStopsShortOfAFocusAFingerBroughtDownToIt() {
        assertEquals("a full fade with room", 1f, fadeReachOver(room = 100f, overhang = 10f, fadePx = 40f))
        assertEquals("half of it with half the room", 0.5f, fadeReachOver(room = 30f, overhang = 10f, fadePx = 40f))
        assertEquals("none over the focus", 0f, fadeReachOver(room = 5f, overhang = 10f, fadePx = 40f))
    }

    @Test
    fun theFocusStaysClearOfTheFadeAndTheFadeLeavesAtTheEnd() {
        open()
        press(Action.Enter)
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertTrue("the panel is taller than this screen", hasFade())
        val tiles = colorSetsPanelEntries(settings).size
        repeat(tiles) {
            if (model.focus.panelIndex < tiles) {
                press(Action.Move(Direction.Down))
                if (hasFade() && model.focus.panelIndex < tiles) {
                    val tile = SettingsTags.panelItem(model.focus.panelIndex)
                    assertTrue("tile ${model.focus.panelIndex} clear of the fade", bottomOf(tile) <= topOf(fade))
                }
            }
        }
        press(Action.Move(Direction.Down))
        assertEquals(COLORS_TEXT_ITEM, model.focus.panelIndex)
        assertFalse("nothing is cut at the end of the panel", hasFade())
    }
}
