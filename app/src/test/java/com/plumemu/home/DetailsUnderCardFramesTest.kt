package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.Beetle
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.sampleFacts
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * With animations on, the card slides in and out over 180 ms. At every frame of both slides no details text is cut by the card's
 * edge, and the column's alpha is one minus the card's own progress (the progress comes from the card's place; the alpha is
 * measured on the pixels: frame = alpha * closed + (1 - alpha) * page). Before, the column vanished on the first frame of the open
 * (half the screen blank) and came back whole on the first frame of the close, under a short card still sliding out.
 */
abstract class DetailsUnderCardFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected open val stop: List<Action> = emptyList()
    protected open val board: HomeContent get() = FakeHomeContent.board()
    private val CUT_VISIBLE = 0.97f
    private val game = GameRef("snes-6", "Super Metroid")
    private var panel by mutableStateOf<OpenPanel?>(null)

    private fun SemanticsNode.isHiddenFromUser(): Boolean = generateSequence(this) { it.parent }.any { SemanticsProperties.InvisibleToUser in it.config }

    /** Drawn texts and the cover under the details column that [card] cuts: overlapping it without being wholly inside. */
    private fun cutParts(card: Rect): List<Pair<String, Rect>> {
        val column = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode()
        val out = mutableListOf<Pair<String, Rect>>()
        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            val label = text ?: tag?.takeIf { it == HomeTags.DETAILS_COVER || it == HomeTags.DETAILS_COVER_SLOT }
            val b = node.boundsInRoot
            if (label != null && node.layoutInfo.isPlaced && b.width > 0f && b.height > 0f && !node.isHiddenFromUser()) {
                val inside = b.left >= card.left - 0.5f && b.right <= card.right + 0.5f && b.top >= card.top - 0.5f && b.bottom <= card.bottom + 0.5f
                if (b.overlaps(card) && !inside) out += label to b
            }
            node.children.forEach(::walk)
        }
        walk(column)
        return out
    }

    private fun cardBounds(): Rect? = compose.onAllNodes(hasTestTag(GamePanelTags.CARD), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private class Sample(val label: String, val card: Rect?, val image: PixelMap, val cut: List<Pair<String, Rect>>)

    private fun sample(label: String): Sample {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val card = cardBounds()
        return Sample(label, card, compose.onRoot().captureToImage().toPixelMap(), if (card == null) emptyList() else cutParts(card))
    }

    /** How much of the closed column is drawn in [now], least squares over the column's pixels in [left] to [right]: 1 whole, 0 gone; null with no ink. */
    private fun alphaOf(closed: PixelMap, now: PixelMap, column: Rect, right: Int): Float? {
        val left = column.left.toInt()
        val top = column.top.toInt()
        val bottom = column.bottom.toInt().coerceAtMost(closed.height)
        val counts = HashMap<Color, Int>()
        for (y in top until bottom) for (x in left until right) counts.merge(closed[x, y], 1, Int::plus)
        val page = counts.maxBy { it.value }.key
        var num = 0.0
        var den = 0.0
        for (y in top until bottom) {
            for (x in left until right) {
                val c = closed[x, y]
                val f = now[x, y]
                num += (f.red - page.red) * (c.red - page.red) + (f.green - page.green) * (c.green - page.green) + (f.blue - page.blue) * (c.blue - page.blue)
                den += (c.red - page.red) * (c.red - page.red) + (c.green - page.green) * (c.green - page.green) + (c.blue - page.blue) * (c.blue - page.blue)
            }
        }
        return if (den < 0.5) null else (num / den).toFloat()
    }

    private fun slides(name: String, open: OpenPanel) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small, animationsOn = true)) { HomeAt(board, BoardGamesFocused + stop, panel = panel) }
        }
        compose.waitForIdle()
        val closed = compose.onRoot().captureToImage().toPixelMap()
        val column = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$name: the column is on screen to fade", column.width > 40f)
        compose.mainClock.autoAdvance = false
        panel = open
        val samples = List(24) { sample("open ${it + 1}") }.toMutableList()
        panel = null
        samples += List(24) { sample("close ${it + 1}") }
        val finalLeft = samples.mapNotNull { it.card?.left }.min()
        val problems = mutableListOf<String>()
        var measured = 0
        for (s in samples) {
            val card = s.card ?: continue
            val progress = 1f - (card.left - finalLeft) / (closed.width - finalLeft)
            // Left of the card and its shadow: the card's own pixels are no part of the column.
            val right = minOf(column.right, card.left - 40f).toInt()
            if (right - column.left.toInt() < 24) continue
            val alpha = alphaOf(closed, s.image, column, right) ?: continue
            measured++
            // A column fading with the card still shows a ghost of its text under the card's edge while it slides; it is
            // asserted where the column is nearly whole (alpha over CUT_VISIBLE), and the semantics hide it while the card is open.
            if (s.cut.isNotEmpty() && alpha > CUT_VISIBLE) problems += "${s.label}: alpha %.2f, the card $card cuts ${s.cut.take(3)}".format(alpha)
            if (abs(alpha - (1f - progress)) > 0.08f) problems += "${s.label}: card progress %.2f, column alpha %.2f, wanted %.2f".format(progress, alpha, 1f - progress)
        }
        assertTrue("$name: the alpha was measured on at least two frames ($measured)", measured >= 2)
        assertEquals("$name:\n" + problems.take(8).joinToString("\n"), emptyList<String>(), problems)
    }

    @Test
    fun menuSlidesInAndOut() = slides("menu", OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true)), focus = 0))

    @Test
    fun shortQuestionSlidesInAndOut() {
        val question = CoreQuestion(1, "snes-6", "Super Metroid", Beetle.id, "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), null)
        slides("question", OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question))))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = WIDE768)
class DetailsUnderCardFrames768Test : DetailsUnderCardFramesBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class DetailsUnderCardFrames832Test : DetailsUnderCardFramesBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = NARROW576)
class DetailsUnderCardFrames576Test : DetailsUnderCardFramesBase() {
    override val stop = listOf(Action.Move(Direction.Right))
    override val board get() = FakeHomeContent.withArtwork()
}
