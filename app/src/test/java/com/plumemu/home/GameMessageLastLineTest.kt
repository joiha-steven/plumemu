package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.Beetle
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreMissingMessage
import com.plumemu.game.sampleListing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game menu's "Change it in Settings › Library › Emulators." ends a line with more than its last word, at 576x432 and
 * the largest text, in every language, whatever the system's name: the path's last step is joined to the one before it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameMessageLastLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val shown = mutableStateOf(SYSTEMS.first())

    /** The last line, and whether it starts after a space (a break inside a word, as Korean can, does not). */
    private fun lastLineOf(system: String): Pair<String, Boolean> {
        val body = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.game_choose_emulator_body, system)
        shown.value = system
        compose.waitForIdle()
        val node = compose.onNode(hasText(body), useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.first()
        val last = layout.lineCount - 1
        val start = layout.getLineStart(last)
        return body.substring(start, layout.getLineEnd(last)).trim() to (start == 0 || body[start - 1] == ' ')
    }

    private fun assertNoLoneWord(isSpaced: Boolean = true, breaksInWords: Boolean = false) {
        compose.setContent {
            val message = coreMissingMessage(sampleListing().copy(systemName = shown.value), Beetle.id, "RetroArch: Snes9x")
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(GameRef("snes-6", "Super Metroid"), PanelScreen.Message(message)))
            }
        }
        for (system in SYSTEMS) {
            val (line, startsAfterSpace) = lastLineOf(system)
            // Written with spaces: a lone word has none, and the line starts at a word (Korean lines break between syllables anywhere). Without (Chinese, Japanese): a lone end is a few characters.
            val isLone = if (isSpaced) (!breaksInWords && !startsAfterSpace) || line.none { it == ' ' || it == '\u00A0' } else line.length < MIN_UNSPACED_END
            assertTrue("with $system the last line is \"$line\" alone", !isLone)
        }
    }

    @Test fun english() = assertNoLoneWord()

    @Test @Config(qualifiers = "es-w576dp-h432dp-land-xhdpi")
    fun spanish() = assertNoLoneWord()

    @Test @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun french() = assertNoLoneWord()

    @Test @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnamese() = assertNoLoneWord()

    @Test @Config(qualifiers = "ko-w576dp-h432dp-land-xhdpi")
    fun korean() = assertNoLoneWord(breaksInWords = true)

    @Test @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun japanese() = assertNoLoneWord(isSpaced = false)

    @Test @Config(qualifiers = "zh-w576dp-h432dp-land-xhdpi")
    fun chineseSimplified() = assertNoLoneWord(isSpaced = false)

    @Test @Config(qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
    fun chineseTraditional() = assertNoLoneWord(isSpaced = false)

    private companion object {
        val SYSTEMS = listOf("Super Nintendo", "PlayStation", "Game Boy Advance", "Nintendo DS", "Arcade")
        const val MIN_UNSPACED_END = 4
    }
}
