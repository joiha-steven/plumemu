package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SystemAccents
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.home.shippedCatalog
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.ViewedSystem
import com.plumemu.settings.systemColors
import com.plumemu.settings.toThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accent color > By system while a finger drags the background: the accent is derived from the colour
 * on screen, so it must follow the previewed background, not the stored one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPreviewAccentTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val preview = ColorPreview()
    private val allColors = shippedCatalog.systemColors()
    private val stored =
        PlumSettings().let {
            it.copy(appearance = it.appearance.copy(background = ColorSetting.Custom(0x102030), accent = ColorSetting.BySystem(ColorSetting.Preset("Blue"))))
        }

    /** A system whose colour is adjusted differently for a dark and a light background (most keep one colour on both). */
    private val system: String = allColors.keys.sorted().first { accentSettledOn(0x102030, it) != accentSettledOn(0xF0F0F0, it) }

    /** The accent the probe drew last: read in a draw lambda, as the focus ring is. */
    private var drawnAccent: Color = Color.Unspecified

    @Composable
    private fun AccentProbe() {
        val colors = PlumTheme.colors
        Box(Modifier.size(8.dp).drawBehind { drawnAccent = colors.accent })
    }

    /** Idle, then a drawn frame (a native-graphics test draws only when asked), so the probe's draw lambda runs. */
    private fun settle() {
        compose.waitForIdle()
        compose.onRoot().captureToImage()
    }

    /** The accent the system settles on once [background] is the stored background. */
    private fun accentSettledOn(background: Int, id: String = system): Color {
        val settled = stored.copy(appearance = stored.appearance.copy(background = ColorSetting.Custom(background)))
        return SystemAccents(settled.toThemeSettings().backgroundColor, allColors).colorOf(id)!!.toComposeColor()
    }

    @Test
    fun theBySystemAccentFollowsThePreviewedBackground() {
        val viewedSystem = ViewedSystem(MutableStateFlow<String?>(system)) { allColors }
        compose.setContent { SettingsTheme(stored, viewedSystem, preview) { AccentProbe() } }
        settle()
        assertEquals(accentSettledOn(0x102030), drawnAccent)

        // Dark to light with a finger still down: the store still holds the dark background.
        compose.runOnIdle { preview.show(stored.appearance.copy(background = ColorSetting.Custom(0xF0F0F0)), fingerDown = true) }
        settle()
        assertNotEquals("the two backgrounds settle on different accents", accentSettledOn(0x102030), accentSettledOn(0xF0F0F0))
        assertEquals(accentSettledOn(0xF0F0F0), drawnAccent)
    }
}
