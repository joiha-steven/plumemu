package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
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

/** The 48 dp peek beside a games list: `home_peek_768x432_<look>.png` and `home_peek_thor_<look>.png` (837x471 dp, Large, the Thor's top screen). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomePeekCodesScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Paper)

    @Test
    fun peekOnTheThor() = shootOnTheThor("home_peek_thor", FakeHomeContent.board(), BoardGamesFocused)

    /** System order = Manual with PS1 hidden, as on the reference size ([manualOrderContent]). */
    @Test
    fun manualOrderOnTheThor() {
        val content = manualOrderContent()
        val snes = content.entries.indexOfFirst { it.id == "snes" }
        shootOnTheThor("home_peek_manual_thor", content, listOf(Action.TapItem(Column.Systems, snes), Action.Enter, Action.TapItem(Column.Games, 2)))
    }

    private fun shootOnTheThor(
        name: String,
        content: HomeContent,
        actions: List<Action>,
    ) {
        compose.setContent { PlumTheme(settings.copy(textSize = TextSize.Large)) { HomeAt(content, actions) } }
        val mismatches = mutableListOf<String>()
        for ((look, base) in listOf("paper" to PlumThemeSettings.Paper, "graphite" to PlumThemeSettings.Graphite)) {
            settings = base
            compose.waitForIdle()
            val file = "${name}_$look"
            try {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$file.png")
            } catch (e: AssertionError) {
                mismatches += "$file: ${e.message}"
            }
        }
        if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
    }
}

/** Same at the reference 768x432 in Graphite and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomePeekCodesWideScreenshotTest {
    @Test
    fun peekAtTheReferenceSize() =
        ReferenceShots.capture("home_peek", sizes = ReferenceShots.Size.Original) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    @Test
    fun codes() = ReferenceShots.capture("home_codes") { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    @Test
    fun codesAtLargest() = ReferenceShots.capture("home_codes", textSize = TextSize.Largest) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    /**
     * System order = Manual: the person's order (DS, PSP, SNES, GBA), PS1 hidden from the column. The peek follows that order,
     * has no PS1 and draws the current system's code (SNS) in the accent.
     */
    @Test
    fun manualOrderWithOneSystemHidden() {
        val content = manualOrderContent()
        val snes = content.entries.indexOfFirst { it.id == "snes" }
        ReferenceShots.capture("home_peek_manual", sizes = ReferenceShots.Size.Original) {
            HomeAt(content, listOf(Action.TapItem(Column.Systems, snes), Action.Enter, Action.TapItem(Column.Games, 2)))
        }
    }
}

/** The person's own order (DS, PSP, SNES, GBA) with PS1 hidden from the column. */
private fun manualOrderContent(): HomeContent {
    fun system(id: String, name: String, code: String) = SystemGames(id, name, List(9) { i -> GameRowModel("$id-$i", "$name game ${i + 1}", "USA", systemAbbr = code, systemId = id) }, code = code)
    return HomeContent.of(
        recent = emptyList(), favorites = emptyList(), apps = emptyList(),
        systems = listOf(system("nds", "Nintendo DS", "NDS"), system("psp", "PlayStation Portable", "PSP"), system("snes", "Super Nintendo", "SNS"), system("gba", "Game Boy Advance", "GBA")),
        hiddenSystems = listOf(system("psx", "PlayStation", "PS1")),
    )
}
