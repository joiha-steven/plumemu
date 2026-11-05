package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorItem
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.SortChoices
import com.plumemu.game.sampleFacts
import com.plumemu.game.sampleListing
import com.plumemu.game.sampleVersions
import com.plumemu.coredata.settings.SortGamesSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** The Pocket 6 (1920x1080 at 2.25: 853 x 480 dp), the Retroid Pocket 5 and the Nova, as Robolectric qualifiers. */
internal const val POCKET6 = "w853dp-h480dp-land-360dpi"
internal const val WIDE768 = "w768dp-h432dp-land-xhdpi"
internal const val NARROW576 = "w576dp-h432dp-land-xhdpi"

/** Writes the screen to `build/panel-shots/<name>.png` when that folder exists (for a person to look at; nothing checks it). */
internal fun ComposeContentTestRule.saveShot(name: String) {
    val dir = File("build/panel-shots")
    if (!dir.isDirectory) return
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

/**
 * The game panel's card on the Pocket 6 and the reference sizes (V2 panel review): the slab centred in the card with the
 * card title's inner padding, the card on the page's margins, no band over the page behind it, the pickers' rows on one line, the
 * footer button in one tone and Info's facts not cut while the card has room.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelV2Test {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("gba-1", "Advance Wars")
    private val facts =
        sampleFacts(title = "Advance Wars").copy(systemName = "Game Boy Advance", regions = "USA", fileName = "Advance Wars (USA).gba", folderName = "ROMs/gba", sizeBytes = 2, emulator = null)
    private val d get() = compose.density.density

    private fun settings(text: TextSize, paper: Boolean = false) = (if (paper) PlumThemeSettings.Paper else PlumThemeSettings.Graphite).copy(textSize = text)

    private val look = mutableStateOf(settings(TextSize.Small))
    private val open = mutableStateOf<OpenPanel?>(null)
    private var isSet = false
    private var shadow = androidx.compose.ui.graphics.Color.Black

    /** Shows [panel] (or none) over the home list; a test may call it again, the screen is set up once. */
    private fun show(panel: OpenPanel?, settings: PlumThemeSettings) {
        look.value = settings
        open.value = panel
        if (!isSet) {
            isSet = true
            compose.setContent { PlumTheme(look.value) { shadow = PlumTheme.colors.card.shadow; HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = open.value) } }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun item(id: String, name: String) = EmulatorItem(id, name, "com.retroarch", isLegalRisk = false, core = "${id}_libretro_android.so")

    /** Play with on the Game Boy Advance board: nothing chosen yet (Automatic asks, and is the current one) and five RetroArch cores. */
    private fun pickerListing(extra: Int = 0) =
        sampleListing(
            installed =
                listOf(item("mgba", "RetroArch: mGBA"), item("gpsp", "RetroArch: gpSP"), item("vbam", "RetroArch: VBA-M"), item("vbanext", "RetroArch: VBA Next"), item("mednafen_gba", "RetroArch: mednafen_gba")) +
                    List(extra) { item("core$it", "RetroArch: core number $it") },
            notInstalled = emptyList(),
            automatic = null,
        ).copy(isSystemChosen = false)

    /** Every kind of panel that lists rows on a slab. */
    private fun slabPanels(): List<Pair<String, OpenPanel>> =
        listOf(
            "menu" to OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true)), focus = 3),
            // The last row too: at the default size on the Pocket 6 the menu scrolls, so the first focus leaves it below the card.
            "menuLast" to OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true)), focus = 7),
            "playWith" to OpenPanel(game, PanelScreen.PlayWith(pickerListing()), focus = 0),
            "sort" to OpenPanel(game, PanelScreen.SortMenu(SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = false)), focus = 0),
            "name" to OpenPanel(game, PanelScreen.NameMenu(facts), focus = 0),
            "info" to OpenPanel(game, PanelScreen.Info(facts)),
            "versions" to OpenPanel(game, PanelScreen.Versions(sampleVersions(currentIndex = 1)), focus = 1),
        )

    private fun firstText(row: SemanticsNode): SemanticsNode = row.children.first { SemanticsProperties.Text in it.config }

    /** The slab's left and right insets in the card match, and the label, value and chevron keep the card title's inner padding on both sides. */
    private fun assertInsets(what: String, panel: OpenPanel, settings: PlumThemeSettings) {
        show(panel, settings)
        val card = bounds(GamePanelTags.CARD)
        val title = node(GamePanelTags.TITLE).boundsInRoot
        for (row in panel.content.focusableRows) {
            if (!has(GamePanelTags.row(row.key))) continue
            // A row of a scrolling list that is not in the window has no place to measure.
            if (firstText(node(GamePanelTags.row(row.key))).boundsInRoot.width <= 0f) continue
            val bounds = bounds(GamePanelTags.row(row.key))
            val left = bounds.left.value - card.left.value
            val right = card.right.value - bounds.right.value
            assertEquals("$what/${row.key}: slab inset left $left vs right $right", left, right, 0.5f)
            val label = firstText(node(GamePanelTags.row(row.key))).boundsInRoot
            assertEquals("$what/${row.key}: label starts where the title does", title.left / d, label.left / d, 0.5f)
            val barToText = label.left / d - bounds.left.value - (SlabDimens.barInset.value + SlabDimens.barWidth.value)
            assertEquals("$what/${row.key}: gap from the slab's bar to the text (the card's text column less the bar: 8 dp, a game row's is 10.67)", 8f, barToText, 0.5f)
            if (has(GamePanelTags.chevron(row.key))) {
                val chevron = node(GamePanelTags.chevron(row.key)).boundsInRoot
                assertEquals("$what/${row.key}: chevron ends the title's padding before the slab's edge", label.left / d - bounds.left.value, bounds.right.value - chevron.right / d, 0.5f)
            }
        }
    }

    /** Every panel on both themes; the menu, Play with and Info are also saved with the card's pixel box for the board pairs (when `build/panel-shots` exists). */
    private fun assertInsetsEverywhere(size: String, text: TextSize) {
        for (paper in listOf(false, true)) {
            for ((name, panel) in slabPanels()) {
                assertInsets("$name ${text.name} ${if (paper) "paper" else "graphite"}", panel, settings(text, paper))
                if (name in listOf("menu", "playWith", "info") && File("build/panel-shots").isDirectory) {
                    val card = node(GamePanelTags.CARD).boundsInRoot
                    val tag = "spec_panels_${size}_${name}_${if (paper) "paper" else "graphite"}"
                    compose.saveShot(tag)
                    File("build/panel-shots/$tag.txt").writeText("${card.left.toInt()} ${card.top.toInt()} ${card.right.toInt()} ${card.bottom.toInt()} $d")
                }
            }
        }
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun slabInsetsAreEqualOnThePocket6() = assertInsetsEverywhere("1920", TextSize.Large)

    @Test
    @Config(qualifiers = WIDE768)
    fun slabInsetsAreEqualAt768() = assertInsetsEverywhere("768", TextSize.Small)

    @Test
    @Config(qualifiers = NARROW576)
    fun slabInsetsAreEqualAt576() = assertInsetsEverywhere("576", TextSize.Small)

    /** The card sits on the page's outer margin and clear of both bars. */
    private fun assertPlacement(text: TextSize) {
        for ((name, panel) in slabPanels()) {
            show(panel, settings(text))
            val card = bounds(GamePanelTags.CARD)
            val rootWidth = compose.onRoot().getUnclippedBoundsInRoot().let { it.right.value - it.left.value }
            assertEquals("$name: right margin is the page's", PlumDimens.stripMarginSide.value, rootWidth - card.right.value, 0.5f)
            val bar = bounds(HomeTags.TOP_BAR)
            assertTrue("$name: card top ${card.top} is not below the top bar ${bar.bottom} and its gap", card.top.value >= bar.bottom.value + PlumDimens.barGap.value - 0.5f)
            assertTrue("$name: card ends above the hint bar's gap", card.bottom.value <= bounds(HomeTags.HINT_BAR).top.value - PlumDimens.footerGap.value + 0.5f)
        }
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun cardIsOnThePageMarginsOnThePocket6() = assertPlacement(TextSize.Large)

    @Test
    @Config(qualifiers = WIDE768)
    fun cardIsOnThePageMarginsAt768() = assertPlacement(TextSize.Small)

    @Test
    @Config(qualifiers = NARROW576)
    fun cardIsOnThePageMarginsAt576() = assertPlacement(TextSize.Small)

    private fun lum(c: androidx.compose.ui.graphics.Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

    /**
     * Opening the panel changes nothing of the page except the card's own shadow, which depends on the distance from the card alone: left of
     * the card, down its height, each column changes smoothly (the shadow's own slope; the list's year and region are not drawn under a card, so no glyph
     * is cut and no band or tint crosses the page), and 44 dp out the page is as it was before the card opened (no fade or scrim over it). The focused
     * row changes its own look when a panel opens: its band is left out.
     */
    private fun assertEvenBehindTheCard(paper: Boolean) {
        show(null, settings(TextSize.Large, paper))
        val before = compose.onRoot().captureToImage().toPixelMap()
        show(OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true))), settings(TextSize.Large, paper))
        val after = compose.onRoot().captureToImage().toPixelMap()
        compose.saveShot(if (paper) "scrim_paper" else "scrim_graphite")
        val card = node(GamePanelTags.CARD).boundsInRoot
        val page = before[2, (card.top + card.height / 2).toInt()]
        val changed = (0 until before.height).filter { y -> (200 until 700).any { x -> abs(lum(before[x, y]) - lum(after[x, y])) > 0.03f } }
        val bandTop = changed.min() - 4 * d.toInt()
        val bandBottom = changed.max() + 4 * d.toInt()
        val worst = StringBuilder()
        for (away in (1..44)) {
            val x = (card.left - away * d).toInt()
            val column = (card.top + 30 * d).toInt().until((card.bottom - 30 * d).toInt()).filter { it !in bandTop..bandBottom }.map { lum(after[x, it]) }
            val step = column.zipWithNext { a, b -> abs(a - b) }.max()
            if (step > 0.01f) worst.append(" ${away}dp: the column jumps by $step between neighbours (a glyph, band or tint);")
        }
        val far = (card.left - 44 * d).toInt()
        val pageNow = lum(after[far, (card.top + card.height / 2).toInt()])
        if (abs(pageNow - lum(page)) > 0.02f) worst.append(" 44dp out the page is $pageNow, it was ${lum(page)} (a fade or scrim);")
        assertTrue("the page beside the card is not as it was apart from the card's own shadow:$worst", worst.isEmpty())
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun pageBehindTheCardIsDimmedEvenlyOnGraphite() = assertEvenBehindTheCard(paper = false)

    @Test
    @Config(qualifiers = POCKET6)
    fun pageBehindTheCardIsDimmedEvenlyOnPaper() = assertEvenBehindTheCard(paper = true)

    private fun saturation(c: androidx.compose.ui.graphics.Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)

    /**
     * The page is not covered behind the card, so what lies under its rounded corners must not show: at the right corners, where the home
     * cover ends on the same margin, no pixel within 16 dp of the card's edge is more saturated than the page beside it.
     */
    private fun assertCornersClear(paper: Boolean) {
        show(OpenPanel(game, PanelScreen.Menu(sampleFacts(canFixBoxArt = true, canSort = true))), settings(TextSize.Large, paper))
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val card = node(GamePanelTags.CARD).boundsInRoot
        val page = saturation(shot[(card.right + 12 * d).toInt(), (card.top + card.height / 2).toInt()])
        val bad = StringBuilder()
        for (cornerY in listOf(card.top, card.bottom)) {
            for (y in (cornerY - 16 * d).toInt()..(cornerY + 16 * d).toInt()) {
                for (x in (card.right - 16 * d).toInt()..minOf((card.right + 16 * d).toInt(), shot.width - 1)) {
                    // The card's own edge line (and its anti-aliasing) is part of the card.
                    val r = CardDimens.corner.value * d
                    val cy = if (cornerY == card.top) card.top + r else card.bottom - r
                    val cx = card.right - r
                    val inCorner = x > cx && (y < card.top + r || y > card.bottom - r)
                    val inShape = if (inCorner) hypot(x - cx, y - cy) <= r + 1.5f else x <= card.right + 1.5f && y >= card.top - 1.5f && y <= card.bottom + 1.5f
                    if (inShape) continue
                    val s = saturation(shot[x, y])
                    if (s > page + 0.05f) bad.append(" ($x,$y) $s;")
                }
            }
        }
        assertTrue("a sliver of the page shows at the card's right corners (page saturation $page):${bad.take(300)}", bad.isEmpty())
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun theCardsRightCornersShowNothingOfThePageOnGraphite() = assertCornersClear(paper = false)

    @Test
    @Config(qualifiers = POCKET6)
    fun theCardsRightCornersShowNothingOfThePageOnPaper() = assertCornersClear(paper = true)

    private fun linesOf(text: SemanticsNode): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().lineCount
    }

    /** Every Play with entry is one line, its "Current" badge on that line, and the row is not taller than a plain one. */
    private fun assertPickerOneLine(what: String, text: TextSize, paper: Boolean = false) {
        val panel = OpenPanel(game, PanelScreen.PlayWith(pickerListing()), focus = 0)
        show(panel, settings(text, paper))
        val heights = panel.content.focusableRows.map { node(GamePanelTags.row(it.key)).boundsInRoot.height / d }
        val rowHeight = heights.min()
        for (row in panel.content.focusableRows) {
            val tag = GamePanelTags.row(row.key)
            val node = node(tag)
            val label = firstText(node)
            assertEquals("$what/${row.key}: '${label.config[SemanticsProperties.Text].joinToString()}' lines", 1, linesOf(label))
            assertTrue("$what/${row.key}: row is ${node.boundsInRoot.height / d} dp high, a plain row is $rowHeight", node.boundsInRoot.height / d <= rowHeight + 0.5f)
            if (row.isCurrent) {
                val badge = node.children.last { SemanticsProperties.Text in it.config }
                assertEquals("$what/${row.key}: the badge is beside the name", label.boundsInRoot.center.y / d, badge.boundsInRoot.center.y / d, 1f)
            }
        }
        compose.saveShot("picker_${text.name}${if (paper) "_paper" else ""}")
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun pickerEntriesAreOneLineOnThePocket6() = assertPickerOneLine("p6 large", TextSize.Large)

    @Test
    @Config(qualifiers = POCKET6)
    fun pickerEntriesAreOneLineOnThePocket6Paper() = assertPickerOneLine("p6 large paper", TextSize.Large, paper = true)

    @Test
    @Config(qualifiers = WIDE768)
    fun pickerEntriesAreOneLineAt768() = assertPickerOneLine("768", TextSize.Small)

    @Test
    @Config(qualifiers = NARROW576)
    fun pickerEntriesAreOneLineAt576() = assertPickerOneLine("576", TextSize.Small)

    /** A list that does not fit shows it scrolls: the fade is there exactly while rows continue past the bottom. */
    @Test
    @Config(qualifiers = WIDE768)
    fun aPickerListThatOverflowsShowsTheScrollFade() {
        show(OpenPanel(game, PanelScreen.PlayWith(pickerListing(extra = 8)), focus = 0), settings(TextSize.Small))
        assertTrue("the list is longer than the card", node(GamePanelTags.ROWS).boundsInRoot.height < 15 * 40 * d)
        assertTrue("rows continue past the bottom, so the fade is drawn", has(GamePanelTags.FADE_BOTTOM))
        show(OpenPanel(game, PanelScreen.PlayWith(pickerListing()), focus = 0), settings(TextSize.Small))
    }

    @Test
    @Config(qualifiers = WIDE768)
    fun aPickerListThatFitsHasNoScrollFade() {
        show(OpenPanel(game, PanelScreen.PlayWith(pickerListing()), focus = 0), settings(TextSize.Small))
        assertTrue("no fade over a list that fits", !has(GamePanelTags.FADE_BOTTOM))
    }

    /** Down the card's right side below Back, no step in the card's tone: the slab's shadow is not cut by the footer's edge. */
    private fun assertOneFooterTone(what: String, paper: Boolean) {
        val panel = OpenPanel(game, PanelScreen.Info(facts))
        show(panel, settings(TextSize.Large, paper))
        val card = node(GamePanelTags.CARD).boundsInRoot
        val back = node(GamePanelTags.row(panel.content.focusableRows.first().key)).boundsInRoot
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val x = (back.left + back.width * 0.7f).toInt()
        var worst = 0f
        var at = 0
        for (y in (back.bottom + 6 * d).toInt() until (card.bottom - 3 * d).toInt()) {
            val step = abs(lum(shot[x, y + 1]) - lum(shot[x, y])) * 255f
            if (step > worst) {
                worst = step
                at = y
            }
        }
        compose.saveShot("info_${if (paper) "paper" else "graphite"}")
        // A shadow's gradient steps up to 3 levels a pixel; a cut edge is 16.
        assertTrue("$what: a step of $worst levels at y=$at under Back (the card ends at ${card.bottom})", worst <= 3.5f)
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun backHasOneFooterToneOnGraphite() = assertOneFooterTone("graphite", paper = false)

    @Test
    @Config(qualifiers = POCKET6)
    fun backHasOneFooterToneOnPaper() = assertOneFooterTone("paper", paper = true)

    /** The facts are whole while the card has room for them: no fade over them, every row inside the viewport. */
    private fun assertFactsNotCut(what: String, text: TextSize, paper: Boolean, fileName: String) {
        val panel = OpenPanel(game, PanelScreen.Info(facts.copy(fileName = fileName)))
        show(panel, settings(text, paper))
        val viewport = node(GamePanelTags.FACTS_SCROLL).boundsInRoot
        val back = node(GamePanelTags.row(panel.content.focusableRows.first().key)).boundsInRoot
        val last = node(GamePanelTags.fact(infoRowCount(panel) - 1)).boundsInRoot
        assertTrue("$what: the last fact (bottom ${last.bottom / d}) is cut by the viewport (${viewport.bottom / d})", last.bottom <= viewport.bottom + 1f)
        assertTrue("$what: no fade over the facts (viewport ${viewport.height / d} dp, content ${node(GamePanelTags.FACTS).boundsInRoot.height / d} dp, Back at ${back.top / d})", !has(GamePanelTags.FADE_BOTTOM))
        assertTrue("$what: the facts end above Back", last.bottom <= back.top)
        // The facts take the room there is, so Back rests in the card's foot (its slab and the card's padding below it), not under the last fact.
        val card = node(GamePanelTags.CARD).boundsInRoot
        assertTrue("$what: Back ends ${(card.bottom - back.bottom) / d} dp above the card's bottom", card.bottom - back.bottom <= 20 * d)
    }

    /** A long file name makes the facts scroll: the fade must end in the card's own tone, not in a band with a hard edge. */
    private fun assertFadeMeltsIntoTheCard(paper: Boolean) {
        show(OpenPanel(game, PanelScreen.Info(facts.copy(fileName = "Advance Wars (USA) (Rev 1) [!].gba"))), settings(TextSize.Large, paper))
        assertTrue("the facts scroll, so the fade is drawn", has(GamePanelTags.FADE_BOTTOM))
        val fade = node(GamePanelTags.FADE_BOTTOM).boundsInRoot
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val x = (fade.left + 4 * d).toInt()
        val inside = shot[x, fade.bottom.toInt() - 2]
        val below = shot[x, fade.bottom.toInt() + 2]
        val step = maxOf(abs(inside.red - below.red), abs(inside.green - below.green), abs(inside.blue - below.blue))
        assertTrue("the fade's foot is $inside, the card under it $below", step <= 3f / 255f)
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun theFactsFadeMeltsIntoTheCardInGraphite() = assertFadeMeltsIntoTheCard(paper = false)

    @Test
    @Config(qualifiers = POCKET6)
    fun theFactsFadeMeltsIntoTheCardInPaper() = assertFadeMeltsIntoTheCard(paper = true)

    private fun infoRowCount(panel: OpenPanel): Int = (0 until 20).count { has(GamePanelTags.fact(it)) }

    @Test
    @Config(qualifiers = POCKET6)
    fun infoFactsAreWholeOnThePocket6() = assertFactsNotCut("p6 large", TextSize.Large, paper = true, fileName = "Advance Wars (USA) [!].gba")

    @Test
    @Config(qualifiers = WIDE768)
    fun infoFactsAreWholeAt768() = assertFactsNotCut("768", TextSize.Small, paper = false, fileName = "Advance Wars (USA).gba")
}
