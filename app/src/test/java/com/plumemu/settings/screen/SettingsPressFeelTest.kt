package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.settings.toThemeSettings
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.components.LocalConfirmCap
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings items the press look reaches (design/rules.md > Shapes and motion > Pressed): a colour swatch, the
 * By system tile, a systems row and a licence row. Holding A dips the focused one, an unfocused one stays
 * as it was, and letting go returns it to rest. Read from the drawn pixels of each item's own bounds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPressFeelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held, LocalConfirmCap provides "A") { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun pixels(tag: String): List<Int> {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y].hashCode()) }
    }

    private fun holdA() {
        compose.runOnIdle {
            held.value = setOf("A")
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
    }

    private fun release() {
        compose.runOnIdle {
            held.value = emptySet()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
    }

    private fun checkDips(
        focusedTag: String,
        otherTag: String,
    ) {
        val focusedRest = pixels(focusedTag)
        val otherRest = pixels(otherTag)
        holdA()
        assertNotEquals("holding A dips the focused item", focusedRest, pixels(focusedTag))
        assertEquals("an unfocused item does not react", otherRest, pixels(otherTag))
        release()
        assertEquals("letting go returns the focused item to rest", focusedRest, pixels(focusedTag))
    }

    @Test
    fun aColourSwatchDips() {
        show { SettingsAt(panelAt(SettingsOption.Background, PanelPage.BackgroundPresets, 1)) }
        checkDips(SettingsTags.panelItem(1), SettingsTags.panelItem(4))
    }

    @Test
    fun theBySystemTileDips() {
        show { SettingsAt(panelAt(SettingsOption.Accent, PanelPage.AccentPresets, ColorPages.bySystemIndex)) }
        checkDips(SettingsTags.panelItem(ColorPages.bySystemIndex), SettingsTags.panelItem(0))
    }

    @Test
    fun aSystemsRowDips() {
        show { SystemsShot(SettingsOption.Systems, enter = true) }
        checkDips(SettingsTags.panelItem(0), SettingsTags.panelItem(1))
    }

    @Test
    fun aLicenceRowDips() {
        show { SettingsAt(panelAt(SettingsOption.Licenses, PanelPage.Licenses, 0)) }
        checkDips(SettingsTags.panelItem(0), SettingsTags.panelItem(1))
    }

    @Test
    fun thePlusTileDips() {
        show { SettingsAt(panelAt(SettingsOption.Accent, PanelPage.AccentPresets, ColorPages.bySystemIndex + 1)) }
        checkDips(SettingsTags.panelItem(ColorPages.bySystemIndex + 1), SettingsTags.panelItem(0))
    }

    /** The accent's own-colour page with the cursor on the Color code row, then [steps]; Android's keyboard, so no tray. */
    @Composable
    private fun ColorCode(steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit) {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val row = PaletteCuration.curated(CustomPalette.accentSwatchesOn(settings.toThemeSettings().backgroundColor)).size + ColorSlider.entries.size
        val model = remember { SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentCustom, row)).also { it.steps(settings, geometry) } }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }

    @Test
    fun theColorCodeRowDips() {
        show { ColorCode { _, _ -> } }
        checkDips(SettingsTags.HEX_ROW, SettingsTags.panelItem(PaletteCuration.curated(CustomPalette.accentSwatchesOn(PlumThemeSettings.Graphite.backgroundColor)).size - 1))
    }

    @Test
    fun theUseThisColorRowDips() {
        show {
            ColorCode { settings, geometry ->
                onAction(Action.Enter, settings, geometry)
                onHexTextChange("1A2B3C")
                onAction(Action.Move(Direction.Down), settings, geometry)
            }
        }
        // Not the Color code row: the Use row's glow reaches into it.
        checkDips(SettingsTags.HEX_USE, SettingsTags.panelItem(PaletteCuration.curated(CustomPalette.accentSwatchesOn(PlumThemeSettings.Graphite.backgroundColor)).size - 1))
    }

    /** The Color code row with Plumemu's own keyboard: A on the row brings the tray up; [steps] then run on the model. */
    private var ownModel: SettingsModel? = null
    private var ownGeometry: SettingsGeometry? = null
    private var ownSettings: PlumSettings? = null

    @Composable
    private fun ColorCodeOwnKeyboard() {
        val settings = settingsForShot()
        val geometry = settingsGeometry(PlumTheme.layout)
        ownGeometry = geometry
        ownSettings = settings
        val row = PaletteCuration.curated(CustomPalette.accentSwatchesOn(settings.toThemeSettings().backgroundColor)).size + ColorSlider.entries.size
        val model =
            remember {
                SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentCustom, row)).also {
                    it.onAction(Action.Enter, settings, geometry)
                    ownModel = it
                }
            }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }

    @Test
    fun theColorCodeRowDoesNotDipWhileTheOwnKeyboardIsUpAndDipsAgainWhenItCloses() {
        show { ColorCodeOwnKeyboard() }
        val model = checkNotNull(ownModel)
        assertNotEquals("the tray is up", null, model.ownKeyboardField)
        val rest = pixels(SettingsTags.HEX_ROW)
        holdA()
        assertEquals("A belongs to the key: the row does not dip", rest, pixels(SettingsTags.HEX_ROW))
        release()
        compose.runOnIdle {
            model.onAction(Action.Back, checkNotNull(ownSettings), checkNotNull(ownGeometry))
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        val closedRest = pixels(SettingsTags.HEX_ROW)
        holdA()
        assertNotEquals("with the tray closed the row dips again", closedRest, pixels(SettingsTags.HEX_ROW))
    }
}
