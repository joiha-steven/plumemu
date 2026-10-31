package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search typing with Plumemu's keyboard on the AYN Thor's top screen (1920x1080, 837x471 dp at 367
 * dpi), at Large, the Thor's default text size: `home_search_typing_thor_<graphite|paper>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class SearchThorScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Paper)

    @Test
    fun typingOnTheThor() {
        val content = FakeHomeContent.withFavorites()
        val search = typingFor("super", content)
        compose.setContent { PlumTheme(settings.copy(textSize = TextSize.Large)) { HomeAt(content, BoardGamesFocused, search = search) } }
        val mismatches = mutableListOf<String>()
        for ((look, base) in listOf("paper" to PlumThemeSettings.Paper, "graphite" to PlumThemeSettings.Graphite)) {
            settings = base
            compose.waitForIdle()
            val name = "home_search_typing_thor_$look"
            try {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
            } catch (e: AssertionError) {
                mismatches += "$name: ${e.message}"
            }
        }
        if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
    }
}
