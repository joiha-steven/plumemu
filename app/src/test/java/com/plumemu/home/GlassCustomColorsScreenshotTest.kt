package com.plumemu.home

import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The focused slab on backgrounds no preset covers, dark, light and either side of the middle: the list and the game menu on a custom colour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GlassCustomColorsScreenshotTest {
    private val backgrounds = listOf(0x1238B0, 0x5A5F6B, 0x8C93A0, 0xE8B84B)

    private fun shot(
        name: String,
        panel: OpenPanel? = null,
    ) {
        RuntimeEnvironment.setQualifiers("w768dp-h432dp-land-xhdpi")
        for (hex in backgrounds) {
            val bg = Rgb(hex)
            val settings = PlumThemeSettings.Graphite.copy(background = BackgroundChoice.Custom(bg), text = TextMode.bestOn(bg).color)
            captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/${name}_768x432_%06x.png".format(hex)) {
                PlumTheme(settings) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
            }
        }
    }

    @Test
    fun list() = shot("glass_custom_list")

    @Test
    fun menu() = shot("glass_custom_menu", OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Menu(sampleFacts(title = "Super Metroid")), focus = 1))
}
