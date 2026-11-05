package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.SortChoices
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val WIDE832 = "w832dp-h468dp-land-xhdpi"

/**
 * The game card's edges over the list behind it: no drawn text of the list is cut by either edge (the year and region, or the
 * systems' counts on a narrow screen, are not drawn while a card is open), and the focused slab's shadow and glow stay inside
 * the card, so beside the slab the page looks as it does beside any other row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelEdgesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private val facts = sampleFacts(canFixBoxArt = true, canSort = true)
    private val d get() = compose.density.density

    private val look = mutableStateOf(PlumThemeSettings.Graphite)
    private val open = mutableStateOf<OpenPanel?>(null)
    private var isSet = false

    private fun show(panel: OpenPanel, paper: Boolean) {
        look.value = (if (paper) PlumThemeSettings.Paper else PlumThemeSettings.Graphite).copy(textSize = TextSize.Small)
        open.value = panel
        if (!isSet) {
            isSet = true
            compose.setContent { PlumTheme(look.value) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = open.value) } }
        }
        compose.waitForIdle()
    }

    private fun panels(): List<Pair<String, OpenPanel>> =
        listOf(
            "menu" to OpenPanel(game, PanelScreen.Menu(facts), focus = 3),
            "info" to OpenPanel(game, PanelScreen.Info(facts)),
            "name" to OpenPanel(game, PanelScreen.NameMenu(facts), focus = 0),
            "sort" to OpenPanel(game, PanelScreen.SortMenu(SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = false)), focus = 0),
        )

    /** Every drawn line of text (not hidden from semantics) as (label, left, right, top, bottom) in root pixels. */
    private fun inkLines(): List<Pair<String, FloatArray>> {
        val out = mutableListOf<Pair<String, FloatArray>>()
        fun walk(node: SemanticsNode) {
            val config = node.config
            val isHidden = SemanticsProperties.InvisibleToUser in config
            // The system names are drawn at zero alpha beside a narrow screen's cards; their counts are checked.
            val isName = SystemRowTags.LABEL == config.getOrNull(SemanticsProperties.TestTag)
            val action = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            if (!isHidden && !isName && action != null) {
                val results = mutableListOf<TextLayoutResult>()
                if (action(results)) {
                    val layout = results.first()
                    val origin = node.positionInRoot
                    for (line in 0 until layout.lineCount) {
                        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                        out += "$text (line $line)" to
                            floatArrayOf(
                                origin.x + layout.getLineLeft(line),
                                origin.x + layout.getLineRight(line),
                                origin.y + layout.getLineTop(line),
                                origin.y + layout.getLineBottom(line),
                            )
                    }
                }
            }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    private fun assertNothingCut(size: String) {
        for (paper in listOf(false, true)) {
            for ((name, panel) in panels()) {
                show(panel, paper)
                val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val cut = StringBuilder()
                for ((text, ink) in inkLines()) {
                    if (ink[3] <= card.top || ink[2] >= card.bottom) continue
                    for ((edgeName, edge) in listOf("left" to card.left, "right" to card.right)) {
                        if (ink[0] < edge - 0.5f && ink[1] > edge + 0.5f) cut.append(" \"$text\" crosses the card's $edgeName edge at ${edge / d} dp (text ${ink[0] / d}..${ink[1] / d});")
                    }
                }
                assertTrue("$size ${if (paper) "paper" else "graphite"} $name: the list's text is cut by the card:${cut.take(400)}", cut.isEmpty())
            }
        }
    }

    @Test
    @Config(qualifiers = WIDE768)
    fun noListTextIsCutByTheCardAt768() = assertNothingCut("768")

    @Test
    @Config(qualifiers = NARROW576)
    fun noListTextIsCutByTheCardAt576() = assertNothingCut("576")

    @Test
    @Config(qualifiers = WIDE832)
    fun noListTextIsCutByTheCardAt832() = assertNothingCut("832")

    /** Pixels 1 to 16 dp outside each card edge beside the focused slab equal those beside a row with no slab (the page there is empty). */
    private fun assertSlabStaysInTheCard(size: String) {
        for (paper in listOf(false, true)) {
            for ((name, panel) in panels().filter { it.first in listOf("menu", "sort") }) {
                show(panel, paper)
                val shot = compose.onRoot().captureToImage().toPixelMap()
                val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val key = panel.content.focusableRows[panel.focus].key
                val slab = compose.onNodeWithTag(GamePanelTags.row(key), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                // A band two rows away, inside the card and clear of its corners.
                val shift = slab.height * 2f
                val margin = CardDimens.corner.value * d * 2f
                val dy = if (slab.top - shift >= card.top + margin) -shift else shift
                assertTrue("$name: no room for a reference band", slab.bottom + dy <= card.bottom - margin)
                var worst = 0f
                var at = ""
                for ((side, edge) in listOf("left" to card.left, "right" to card.right)) {
                    for (away in 1..16) {
                        val x = (if (side == "left") edge - away * d else edge + away * d).toInt().coerceIn(0, shot.width - 1)
                        for (y in (slab.top + 2).toInt() until (slab.bottom - 2).toInt()) {
                            val a = shot[x, y]
                            val b = shot[x, (y + dy).toInt()]
                            val diff = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                            if (diff > worst) {
                                worst = diff
                                at = "$side edge, ${away} dp out, y ${y / d} dp"
                            }
                        }
                    }
                }
                assertTrue("$size ${if (paper) "paper" else "graphite"} $name: beside the slab the page differs from beside a plain row by $worst (limit 0.012) at $at: the slab's shadow or glow spills out of the card", worst <= 0.012f)
            }
        }
    }

    @Test
    @Config(qualifiers = WIDE768)
    fun theSlabsShadowStaysInsideTheCardAt768() = assertSlabStaysInTheCard("768")

    @Test
    @Config(qualifiers = NARROW576)
    fun theSlabsShadowStaysInsideTheCardAt576() = assertSlabStaysInTheCard("576")
}
