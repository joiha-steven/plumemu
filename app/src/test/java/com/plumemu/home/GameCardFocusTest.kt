package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.game.Beetle
import com.plumemu.game.CORE_NO_KEY
import com.plumemu.game.CORE_YES_KEY
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreQuestionMessage
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The core question's focused button has its groove lit in the accent and the other one rests, by measured pixels, in Graphite and Paper
 * (a light theme has no glow, so the groove is the only light). Yes is the main button: its cap is the accent too, so the lit groove reads as
 * the cap's own rim; what is checked is the groove against the collar's lip and against the same button resting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameCardFocusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private val added = AddedSession(sessionId = 9, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
    private val question = CoreQuestion(1, "snes-6", "Super Metroid", Beetle.id, "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), added)
    private var focus by mutableStateOf(0)

    private fun show(settings: PlumThemeSettings) {
        compose.setContent {
            PlumTheme(settings) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question)), focus = focus)) }
        }
        compose.waitForIdle()
    }

    private fun focusOn(index: Int) {
        compose.runOnIdle { focus = index }
        compose.waitForIdle()
    }

    private fun luminance(c: Rgb): Double {
        fun lin(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(a: Rgb, b: Rgb) = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)

    private class Look(val groove: Rgb, val lip: Rgb)

    /** The groove (beside the cap, on the button's middle line) and the lip's body (a little further out) of the button tagged [key]. */
    private fun look(key: String): Look {
        val image = compose.onNodeWithTag(GamePanelTags.row(key), useUnmergedTree = true).captureToImage().toPixelMap()
        val density = compose.density.density
        val y = image.height / 2
        fun at(dp: Float): Rgb {
            val c = image[(dp * density).toInt().coerceIn(0, image.width - 1), y]
            return Rgb((c.red * 255).toInt() shl 16 or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt())
        }
        val lip = CollarDimens.lip.value
        return Look(groove = at(lip + CollarDimens.groove.value / 2), lip = at(lip / 2))
    }

    private fun check(settings: PlumThemeSettings, name: String, minAgainstLip: Double) {
        show(settings)
        // Yes is focused first.
        val yesLit = look(CORE_YES_KEY)
        val noResting = look(CORE_NO_KEY)
        focusOn(1)
        val noLit = look(CORE_NO_KEY)
        val yesResting = look(CORE_YES_KEY)
        for ((what, lit, resting) in listOf(Triple("Yes", yesLit, yesResting), Triple("No", noLit, noResting))) {
            val against = contrast(lit.groove, lit.lip)
            val change = contrast(lit.groove, resting.groove)
            assertTrue("$name $what: the lit groove stands off its lip by $minAgainstLip:1 (is $against)", against >= minAgainstLip)
            assertTrue("$name $what: the lit groove differs from the resting one (is $change)", change >= 2.0)
            assertTrue("$name $what: a resting groove is not the lit one", contrast(resting.groove, resting.lip) < against)
        }
    }

    @Test
    fun theFocusedGrooveIsLitAndTheOtherRestsInGraphite() = check(PlumThemeSettings.Graphite, "Graphite", minAgainstLip = 2.0) // the glow outside carries the rest

    @Test
    fun theFocusedGrooveIsLitAndTheOtherRestsInPaper() = check(PlumThemeSettings.Paper, "Paper", minAgainstLip = 3.0)
}
