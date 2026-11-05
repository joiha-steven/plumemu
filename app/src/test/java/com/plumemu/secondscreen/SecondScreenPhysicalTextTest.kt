package com.plumemu.secondscreen

import android.util.DisplayMetrics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenPhysical
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.ThorPanels
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.GameDetails
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How the second screen's window scales its text, and the real Thor bottom screen's game card at that scale. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenPhysicalTextTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite.copy(font = PlumFont.Jost))
    private var content by mutableStateOf<SecondScreenContent>(BOARD_GAME)
    private var scale by mutableStateOf(SecondScreenPhysical.thor)

    private fun show(
        card: SecondScreenContent,
        size: TextSize = TextSize.Large,
    ) {
        settings = PlumThemeSettings.Graphite.copy(font = PlumFont.Jost, textSize = size)
        content = card
        compose.waitForIdle()
    }

    private fun host() =
        compose.setContent {
            PlumTheme(settings) {
                WithPhysicalTextScale(scale) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } }
            }
        }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    /** The drawn height of a text's em in millimetres on the Thor's bottom panel. */
    private fun mmOf(node: SemanticsNode): Double {
        val input = layoutOf(node).layoutInput
        val px = with(input.density) { input.style.fontSize.toPx() }
        return px / ThorPanels.bottom.ppi * MM_PER_INCH
    }

    private fun mainMm(
        role: TypeRole,
        size: TextSize,
    ) = ThorPanels.top.millimetres(role.scaledSizeSp(PlumFont.Jost, size, secondScreen = true))

    private fun byTag(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().last()

    private fun byText(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().last()

    @Test
    fun theOwnersWorstCasesAreAsBigAsTheMainScreensStylesAtEveryTextSize() {
        host()
        val problems = mutableListOf<String>()
        val sizes = mutableMapOf<TextSize, Double>()
        for (size in TextSize.entries) {
            show(BOARD_GAME_NO_RA.copy(lastPlayedDaysAgo = null, weeks = PlayWeeks(List(8) { 0L }, 0, 0)), size)
            val label = mmOf(byText("PLAY TIME"))
            val thisWeek = mmOf(byText("this wk"))
            val system = mmOf(byTag(GameCardTags.SYSTEM))
            val file = mmOf(byTag(GameCardTags.FILE))
            val tag = mainMm(TypeRole.Tag, size)
            val info = mainMm(TypeRole.InfoLineMeta, size)
            println("MM $size label %.3f this wk %.3f PS2 %.3f file %.3f; main tag %.3f info %.3f".format(label, thisWeek, system, file, tag, info))
            if (label + EPS < tag) problems += "$size label $label < $tag"
            if (thisWeek + EPS < tag) problems += "$size this wk $thisWeek < $tag"
            if (system + EPS < tag) problems += "$size PS2 $system < $tag"
            if (file + EPS < info) problems += "$size file name $file < $info"
            sizes[size] = label
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        // The Text size setting scales it: Largest is 1.45 times Medium.
        assertEquals(1.45, sizes.getValue(TextSize.Largest) / sizes.getValue(TextSize.Medium), 0.01)
        assertTrue("Small is smaller than Medium", sizes.getValue(TextSize.Small) < sizes.getValue(TextSize.Medium))
    }

    @Test
    fun withoutTheScaleTheLabelWasUnderTheMainTag() {
        host()
        scale = 1f
        show(BOARD_GAME_NO_RA, TextSize.Medium)
        assertTrue("an unscaled bottom screen reads smaller than the top one", mmOf(byText("PLAY TIME")) < mainMm(TypeRole.Tag, TextSize.Medium))
    }

    @Test
    fun theWindowMultipliesTheTypeAndLeavesTheDensity() {
        var base = 0f to 0f
        var seen = 0f to 0f
        var sizes = 0f to 0f
        compose.setContent {
            PlumTheme(settings) {
                base = LocalDensity.current.density to LocalDensity.current.fontScale
                val before = PlumTheme.type[TypeRole.Tag].fontSize.value
                WithPhysicalTextScale(1.25f) {
                    seen = LocalDensity.current.density to LocalDensity.current.fontScale
                    sizes = before to PlumTheme.type[TypeRole.Tag].fontSize.value
                }
            }
        }
        compose.waitForIdle()
        assertEquals("the density is left alone", base, seen)
        assertEquals(sizes.first * 1.25f * TextSize.Large.secondScreenFactor / TextSize.Large.factor, sizes.second, 1e-3f)
    }

    @Test
    fun theThorsPanelsAreKnownBySizeAndOthersByTheirReportedDpi() {
        fun metrics(
            w: Int,
            h: Int,
            density: Float,
            dpi: Float,
        ) = DisplayMetrics().also {
            it.widthPixels = w
            it.heightPixels = h
            it.density = density
            it.xdpi = dpi
            it.ydpi = dpi
        }
        // Whatever dpi Android reports, the Thor's panels are the data sheet's.
        assertEquals(3.92, panelFactsOf(metrics(1240, 1080, 2.30625f, 369f))!!.diagonalInches, 1e-9)
        assertEquals(6.0, panelFactsOf(metrics(1080, 1920, 2.29375f, 367f))!!.diagonalInches, 1e-9)
        assertEquals(4.59, panelFactsOf(metrics(1280, 720, 2f, 320f))!!.diagonalInches, 0.01)
        assertNull("no dpi, no size", panelFactsOf(metrics(1280, 720, 2f, 0f)))
        assertNotNull(panelFactsOf(metrics(1240, 1080, 2.30625f, 0f)))
    }

    /** Every part is whole inside the card at the Thor's scale and Large text, with a long title, a long description and none. */
    @Test
    fun theCardStaysWholeAtTheThorsScale() {
        host()
        val cases =
            mapOf(
                "board" to BOARD_GAME,
                "no ra" to BOARD_GAME_NO_RA,
                "long title" to BOARD_GAME.copy(title = LONG_TITLE),
                "long description" to BOARD_GAME.copy(details = GameDetails(1994, "", "", "", BOARD_DESCRIPTION.repeat(5))),
                "no description" to BOARD_GAME.copy(details = null),
            )
        val problems = mutableListOf<String>()
        for (size in listOf(TextSize.Medium, TextSize.Large, TextSize.Largest)) {
            for ((name, card) in cases) {
                show(card, size)
                val at = "$size $name"
                val bounds = byTag(GameCardTags.CARD).boundsInRoot
                val title = byTag(GameCardTags.TITLE)
                val system = byTag(GameCardTags.SYSTEM)
                if (layoutOf(title).lineCount != 1) problems += "$at: the title takes ${layoutOf(title).lineCount} lines"
                if (title.boundsInRoot.right > system.boundsInRoot.left + 1f) problems += "$at: the title runs into the system code"
                for (tag in listOf(GameCardTags.TITLE, GameCardTags.SYSTEM, GameCardTags.TILES, GameCardTags.BARS, GameCardTags.FILE)) {
                    val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                    for (n in nodes) {
                        val b = n.boundsInRoot
                        if (b.left < bounds.left - 1f || b.right > bounds.right + 1f || b.bottom > bounds.bottom + 1f) problems += "$at: $tag $b leaves the card $bounds"
                    }
                }
                val descriptions = compose.onAllNodesWithTag(GameCardTags.DESCRIPTION, useUnmergedTree = true).fetchSemanticsNodes()
                if (name == "no description" && descriptions.isNotEmpty()) problems += "$at: a description from nothing"
                val footTop = compose.onAllNodesWithTag(GameCardTags.FILE, useUnmergedTree = true).fetchSemanticsNodes().maxOfOrNull { it.boundsInRoot.top }
                val tilesBottom = byTag(GameCardTags.TILES).boundsInRoot.bottom
                if (footTop != null && footTop < tilesBottom) problems += "$at: the file name overlaps the tiles"
                // The label either shows whole or loses its second half, never a cut word.
                val label = byText("PLAY TIME")
                val text = label.config[SemanticsProperties.Text].joinToString("")
                if (text != "PLAY TIME" && !text.startsWith("PLAY TIME ·")) problems += "$at: label '$text'"
                if (text.endsWith("…")) problems += "$at: label cut '$text'"
            }
        }
        assertTrue("${problems.size} problems:\n" + problems.take(15).joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val MM_PER_INCH = 25.4
        const val EPS = 1e-6
    }
}
