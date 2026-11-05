package com.plumemu.secondscreen

import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Layout C on the AYN Thor at its real pixel sizes: the top screen 1920x1080 (837x471 dp at
 * 367 dpi) and the bottom screen 1240x1080 (538x468 dp at 369 dpi).
 * Shots `layout_c_thor_top_<case>_<graphite|paper>.png`.
 * The top starts at Large text there, as the app does on a screen that wide.
 */

/** The top screen: always the single-screen home with its column, with the second screen's window up or not (the top screen ignores it), with and without box art. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class LayoutCThorTopScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var case by mutableStateOf(Case.Art)

    private enum class Case(
        val label: String,
        val hasArt: Boolean,
    ) {
        Art("art", hasArt = true),
        NoArt("no_art", hasArt = false),
    }

    @Test
    fun theTopScreen() {
        compose.setContent {
            PlumTheme(settings) {
                // A fresh strip per case: the board's focus is applied once, before its first frame.
                key(case) {
                    val content = if (case.hasArt) FakeHomeContent.withArtwork() else FakeHomeContent.board()
                    HomeAt(content, BoardGamesFocused)
                }
            }
        }
        val mismatches = mutableListOf<String>()
        for ((look, base) in listOf("graphite" to PlumThemeSettings.Graphite, "paper" to PlumThemeSettings.Paper)) {
            for (each in Case.entries) {
                settings = base
                case = each
                compose.waitForIdle()
                val name = "layout_c_thor_top_${each.label}_$look"
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
                } catch (e: AssertionError) {
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}
