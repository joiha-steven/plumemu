package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.testTag
import com.plumemu.home.SnesAboutInfo
import com.plumemu.home.SnesInfo
import com.plumemu.settings.screen.TopSystem
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The second-screen cards set Jost on a shorter line ([CARD_JOST_LINE_EM]); Vietnamese capitals with stacked
 * marks reach about 0.1 em above that line box. Nothing in a card clips text, so the marks are drawn into the
 * space above the line. What must hold is not "every glyph fits its own line box" (a clip no card has) but
 * that no glyph is cut and no mark touches anything else. Two checks prove it:
 *
 * 1. Line to line ([marksAboveTheNextLineDoNotMergeWithTheDescendersOfThePreviousOne]): every card text role,
 *    unclipped in a card, lights as many pixels as on Jost's own line (an overlap would merge pixels), and
 *    where descenders end right above stacked capitals, at least one background row separates them.
 * 2. Above the first line ([SecondScreenCardTopInkBase]): in every real card the ink of each text, from its
 *    first line's top mark, keeps 1 dp of background from the ink above it (a badge, the previous text) and
 *    from the card edge. A text whose marks touched the line above would merge with it and climb past its box.
 *
 * Clipping each text to its line box was the wrong proxy: it fails for marks that are drawn in free space.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SecondScreenCardLineInkTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Medium)
    private var role by mutableStateOf(TypeRole.Meta)
    private var text by mutableStateOf("")
    private var inCard by mutableStateOf(false)

    private val roles =
        listOf(
            TypeRole.SecondTopRoomy, TypeRole.Meta, TypeRole.SecondTitleRoomy, TypeRole.SecondTitleCompact,
            TypeRole.SecondDetailsTitleRoomy, TypeRole.SecondDetailsTitleCompact,
            TypeRole.SecondFactsRoomy, TypeRole.SecondFactsCompact,
        )

    private val oneLine = "\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2 \u00C9\u00C0\u00D1 gjpqy \u1EA4"
    private val wrapped = "\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2 \u00C9\u00C0\u00D1 gjpqy \u1EA4\u1EC0\u1ED4\u1ED6\u1EE2"

    /** Descenders on the first line, stacked capitals right under them on the second. */
    private val descendersOverStacks = "gjpqygjpqyjgpqy\n\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2\u1EA4\u1EC0"

    @Test
    fun marksAboveTheNextLineDoNotMergeWithTheDescendersOfThePreviousOne() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                CompositionLocalProvider(LocalCardLine provides inCard) {
                    Box(Modifier.background(Color.Black).padding(24.dp)) {
                        Column(Modifier.width(if (text == wrapped) 260.dp else 600.dp)) {
                            BasicText(text, Modifier.testTag("ink_text"), style = PlumTheme.type.card(role).copy(color = Color.White))
                        }
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        var minGap = Int.MAX_VALUE
        for (eachSize in listOf(TextSize.Medium, TextSize.Largest)) {
            for (eachRole in roles) {
                for (eachText in listOf(oneLine, wrapped, descendersOverStacks)) {
                    size = eachSize
                    role = eachRole
                    text = eachText
                    inCard = false
                    compose.waitForIdle()
                    val natural = capture().lit()
                    inCard = true
                    compose.waitForIdle()
                    val ink = capture()
                    val inCardLit = ink.lit()
                    assertTrue("$eachSize $eachRole: something is drawn", natural > 200)
                    val label = "$eachSize $eachRole '${eachText.take(6)}'"
                    // Anti-aliasing moves a few edge pixels when the text shifts by a fraction of a pixel; overlap merges pixels.
                    if (kotlin.math.abs(inCardLit - natural) > natural / 100) failures += "$label: $inCardLit lit pixels in the card, $natural on the font's own line"
                    if (eachText == descendersOverStacks) {
                        val gap = descenderToMarkGap(ink)
                        minGap = minOf(minGap, gap)
                        if (gap < 1) failures += "$label: the descenders touch the marks of the next line ($gap background rows between)"
                    }
                }
            }
        }
        println("CARD_LINE_INK min gap between descenders and the next line's marks: $minGap px")
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    /** Background rows between the lowest descender of line 1 and the highest mark of line 2. */
    private fun descenderToMarkGap(ink: Ink): Int {
        val node = compose.onNodeWithTag("ink_text").fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals("two lines", 2, layout.lineCount)
        val x0 = node.boundsInRoot.left.toInt()
        val x1 = node.boundsInRoot.right.toInt()
        var y = (node.boundsInRoot.top + layout.getLineBaseline(0)).toInt() - 1
        assertTrue("the first line's ink sits on its baseline", ink.rowLit(y, x0, x1))
        while (y < ink.height && ink.rowLit(y, x0, x1)) y++
        var gap = 0
        while (y < ink.height && !ink.rowLit(y, x0, x1)) {
            y++
            gap++
        }
        assertTrue("the second line has ink", y < ink.height)
        return gap
    }

    private fun capture(): Ink = Ink.of(compose)
}

/** A capture's pixels; ink is whatever differs visibly from the background (the top-left pixel). */
internal class Ink private constructor(
    val width: Int,
    val height: Int,
    private val pixels: IntArray,
) {
    private val bg = pixels[0]

    fun lit(): Int = pixels.count { differs(it) }

    private fun differs(pixel: Int): Boolean = differs(pixel, bg)

    fun isLit(
        x: Int,
        y: Int,
    ): Boolean = differs(pixels[y * width + x])

    fun rowLit(
        y: Int,
        x0: Int,
        x1: Int,
    ): Boolean = y in 0 until height && (maxOf(x0, 0) until minOf(x1, width)).any { isLit(it, y) }

    /**
     * [rowLit] against the row's own background, the pixel [REFERENCE_GAP] left of [x0]: on a glass tile or under the corner glow the
     * page colour changes from row to row, and a text's ink is what differs from what is right behind it.
     */
    fun rowLitOnItsBackground(
        y: Int,
        x0: Int,
        x1: Int,
    ): Boolean {
        if (y !in 0 until height) return false
        val ref = pixels[y * width + maxOf(x0 - REFERENCE_GAP, 0)]
        return (maxOf(x0, 0) until minOf(x1, width)).any { differs(pixels[y * width + it], ref) }
    }

    private fun differs(
        pixel: Int,
        ref: Int,
    ): Boolean {
        val d = { shift: Int -> kotlin.math.abs(((pixel shr shift) and 0xFF) - ((ref shr shift) and 0xFF)) }
        return maxOf(d(0), d(8), d(16)) > THRESHOLD
    }

    companion object {
        private const val THRESHOLD = 0x30
        private const val REFERENCE_GAP = 2

        fun of(compose: ComposeContentTestRule): Ink {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Ink(bitmap.width, bitmap.height, pixels)
        }
    }
}

/** How far above the baseline stacked marks on a capital reach at least, in em (capitals end near 0.7 em). */
private const val STACKED_RISE_EM = 0.85f

private const val STACKED = "\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2"
private const val STACKED_MORE = "\u1EC6\u1EBE\u1EC2 \u1EAC\u1EB6"
private const val STACKED_LONG = "$STACKED $STACKED_MORE gjpqy $STACKED $STACKED_MORE gjpqy $STACKED $STACKED_MORE gjpqy"
private val STACKED_MARKS = "\u1EA4\u1EC0\u1ED4\u1ED6\u1EE2\u1EC6\u1EBE\u1EC2\u1EAC\u1EB6".toSet()

/**
 * Above the first line of every text in the real cards: texts, badge and facts holding stacked capitals.
 * The ink of each text node, climbed from its first line until a 1 dp band of background, must keep that band
 * from the card edge and from the ink above it; the climb stays within 0.2 em above the node's box (touching
 * the line above would merge the two and climb further). Reports the smallest gap per card.
 */
abstract class SecondScreenCardTopInkBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val covers = SecondScreenStatisticsSamples.covers()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = covers) } }
    }

    private val details = FULL_DETAILS.copy(publisher = "$STACKED $STACKED_MORE", genre = "$STACKED, $STACKED_MORE", players = "1")
    private val stackedGame =
        GAME_WITH_STAR.copy(title = STACKED_LONG, systemName = "$STACKED $STACKED_MORE", region = STACKED_MORE, fileName = "$STACKED_MORE (Japan, USA).sfc")

    private val cases: List<Pair<String, SecondScreenContent>> =
        listOf(
            "picking game" to stackedGame,
            "game card" to BOARD_GAME.copy(title = STACKED_LONG, systemName = "$STACKED $STACKED_MORE", ra = BOARD_RA.copy(recent = BOARD_RA.recent.map { it.copy(title = STACKED_LONG) })),
            "game card no RA" to BOARD_GAME_NO_RA.copy(title = STACKED_LONG, systemName = "$STACKED $STACKED_MORE"),
            "system" to SYSTEM_STACKED_MARKS.copy(name = STACKED_LONG, info = SnesInfo.copy(maker = "$STACKED $STACKED_MORE")),
            "system with about" to SYSTEM_STACKED_MARKS.copy(name = "$STACKED $STACKED_MORE", info = SnesAboutInfo.copy(maker = "$STACKED $STACKED_MORE")),
            "recent" to RECENT_FULL.copy(figures = RECENT_FULL.figures?.let { it.copy(games = it.games.map { game -> game.copy(title = STACKED_LONG) }) }),
            "favorites" to FAVORITES_MANY.copy(games = FAVORITES_MANY.games.map { it.copy(title = STACKED_LONG) }),
            "details no art" to stackedGame.withDetails(details),
            "statistics" to
                SecondScreenContent.Statistics(
                    SecondScreenStatisticsSamples.withCovers.let { stats ->
                        stats.copy(
                            topGames = stats.topGames.map { it.copy(title = STACKED_LONG) },
                            topSystems = listOf(TopSystem("snes", "$STACKED $STACKED_MORE", 19 * 3_600L + 45 * 60)),
                        )
                    },
                ),
        )

    @Test
    fun marksAboveTheFirstLineKeepOneDpFromWhatIsAboveThem() {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val band = density.toInt()
        val failures = mutableListOf<String>()
        var overall = Float.MAX_VALUE
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            for ((name, shown) in cases) {
                settings = PlumThemeSettings.Default.copy(textSize = textSize)
                content = shown
                compose.waitForIdle()
                val ink = Ink.of(compose)
                val nodes =
                    compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                        .filter { it.layoutInfo.isPlaced && it.boundsInRoot.width > 0f && it.config[SemanticsProperties.Text].any { part -> part.text.isNotBlank() } }
                var measured = 0
                var cardMin = Float.MAX_VALUE
                for (node in nodes) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    val layout = layouts.firstOrNull() ?: continue
                    val box = node.boundsInRoot
                    val x0 = box.left.toInt()
                    val x1 = kotlin.math.ceil(box.right).toInt()
                    val firstTop = (box.top + layout.getLineTop(0)).toInt()
                    val firstBottom = (box.top + layout.getLineBottom(0)).toInt()
                    val centre = (firstTop + firstBottom) / 2
                    val seed = (0..(firstBottom - firstTop)).flatMap { listOf(centre + it, centre - it) }.firstOrNull { ink.rowLitOnItsBackground(it, x0, x1) } ?: continue
                    measured++
                    var top = seed
                    var blank = 0
                    var y = seed
                    while (y > 0 && blank < band) {
                        y--
                        if (ink.rowLitOnItsBackground(y, x0, x1)) {
                            top = y
                            blank = 0
                        } else {
                            blank++
                        }
                    }
                    var gap = 0
                    y = top - 1
                    while (y >= 0 && !ink.rowLitOnItsBackground(y, x0, x1)) {
                        gap++
                        y--
                    }
                    val em = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
                    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    val firstLine = text.substring(layout.getLineStart(0), layout.getLineEnd(0).coerceAtMost(text.length))
                    val label = "$widthDp x $heightDp $textSize '$name' '${text.take(12)}'"
                    val gapDp = gap / density
                    if (y < 0) cardMin = minOf(cardMin, gapDp)
                    overall = minOf(overall, gapDp)
                    if (gap < band) failures += "$label: ${"%.1f".format(gapDp)} dp of background above its ink, 1 dp wanted"
                    if (top < box.top - 0.2f * em) failures += "$label: ink reaches ${"%.1f".format((box.top - top) / density)} dp above its box: it touches what is above"
                    // The marks rise above the capitals: if they were not reached, the climb stopped inside the glyph.
                    // A taller line holds them inside the box, so they are looked for above the capitals instead.
                    val marksAbove = box.top + layout.getLineBaseline(0) - STACKED_RISE_EM * em
                    if (firstLine.any { it in STACKED_MARKS } && top > marksAbove) failures += "$label: the stacked marks were not reached (ink top $top, wanted above $marksAbove)"
                }
                assertTrue("$widthDp x $heightDp $textSize '$name': at least two texts measured, got $measured", measured >= 2)
            }
        }
        println("CARD_TOP_INK $widthDp x $heightDp: min gap above any text's ink ${"%.1f".format(overall)} dp")
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardTopInk768Test : SecondScreenCardTopInkBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardTopInk576Test : SecondScreenCardTopInkBase(576, 432)

/** The 4:3 second screen, whose cards use the Compact sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardTopInk480Test : SecondScreenCardTopInkBase(480, 360)
