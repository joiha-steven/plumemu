package com.plumemu.textsize

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide details block with every cell filled (GENRE, RATING, PLAYED, LAST PLAYED, EMULATOR, BATTERY) at 768 x 432, Small to Largest text,
 * in every font: the cells that leave are the first of BATTERY, EMULATOR, GENRE, RATING (the cover keeps its height, PLAYED and LAST PLAYED stay), the
 * battery value is whole when it is shown, and no text is cut, ends in an ellipsis or overlaps another.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsAllCellsTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun texts(node: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun text(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("").replace(" ", " ")

    private fun isEllipsized(node: SemanticsNode): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.firstOrNull() ?: return false
        return (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }

    @Test
    fun everyCellFilledAtSmallInSerif() = everyCellFilledFits(PlumFont.Serif, TextSize.Small)

    @Test
    fun everyCellFilledAtLargeInSerif() = everyCellFilledFits(PlumFont.Serif, TextSize.Large)

    @Test
    fun everyCellFilledAtLargestInSerif() = everyCellFilledFits(PlumFont.Serif, TextSize.Largest)

    @Test
    fun everyCellFilledAtSmallInJost() = everyCellFilledFits(PlumFont.Jost, TextSize.Small)

    @Test
    fun everyCellFilledAtLargeInJost() = everyCellFilledFits(PlumFont.Jost, TextSize.Large)

    @Test
    fun everyCellFilledAtLargestInJost() = everyCellFilledFits(PlumFont.Jost, TextSize.Largest)

    @Test
    fun everyCellFilledAtSmallInSans() = everyCellFilledFits(PlumFont.Sans, TextSize.Small)

    @Test
    fun everyCellFilledAtLargeInSans() = everyCellFilledFits(PlumFont.Sans, TextSize.Large)

    @Test
    fun everyCellFilledAtLargestInSans() = everyCellFilledFits(PlumFont.Sans, TextSize.Largest)

    @Test
    fun everyCellFilledAtSmallInMono() = everyCellFilledFits(PlumFont.Mono, TextSize.Small)

    @Test
    fun everyCellFilledAtLargeInMono() = everyCellFilledFits(PlumFont.Mono, TextSize.Large)

    @Test
    fun everyCellFilledAtLargestInMono() = everyCellFilledFits(PlumFont.Mono, TextSize.Largest)

    // 832 x 468 (Retroid Pocket 6) at the default size: the same checks, in the default font and the others.
    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun everyCellFilledAtLargeOn832InJost() = everyCellFilledFits(PlumFont.Jost, TextSize.Large)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun everyCellFilledAtLargeOn832InSerif() = everyCellFilledFits(PlumFont.Serif, TextSize.Large)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun everyCellFilledAtLargeOn832InMono() = everyCellFilledFits(PlumFont.Mono, TextSize.Large)

    // The genre is one line with an ellipsis; a list too long for its share shows its first genre.
    @Test
    fun genreIsOneLineAtLargeInJost() {
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", description = "", rating = 17)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Large, font = PlumFont.Jost)) {
                HomeAt(LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
            }
        }
        compose.waitForIdle()
        val block = findTagged(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), HomeTags.DETAILS_FACTS)!!
        val genre = texts(block).first { text(it).startsWith("Action") }
        val layouts = mutableListOf<TextLayoutResult>()
        genre.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue("genre '${text(genre)}' is one line, got ${layouts.first().lineCount}", layouts.first().lineCount == 1)
    }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun everyCellFilledFits(
        font: PlumFont,
        size: TextSize,
    ) {
        val sample = BATTERY_LINE_SAMPLES[2]
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, sample.level).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", description = "", rating = 17)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(pace = sample.pace)) {
                    HomeAt(LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val block = findTagged(root, HomeTags.DETAILS_FACTS)!!
        val all = texts(block)
        val shown = all.map(::text)
        // Cells that do not fit leave whole in this order; the ones missing are always its first few, and PLAYED and LAST PLAYED stay.
        // The battery's time is whole; it says "About" only where its row has room for the word.
        val dropOrder = listOf("BATTERY" to "14 h 15 min", "EMULATOR" to "Snes9x", "GENRE" to "Action", "RATING" to "8.5")
        val missing = dropOrder.filter { it.first !in shown }
        assertTrue("$font $size: missing ${missing.map { it.first }} is not a prefix of the drop order: $shown", missing == dropOrder.take(missing.size))
        assertTrue("$font $size: LAST PLAYED stays: $shown", "LAST PLAYED" in shown)
        assertTrue("$font $size: PLAYED stays: $shown", "PLAYED" in shown)
        for ((label, value) in dropOrder.filter { it !in missing }) {
            if (value != null) assertTrue("$font $size: $label value whole: $shown", shown.any { it.contains(value) })
        }
        for ((label, value) in missing) assertTrue("$font $size: $label has no stray value: $shown", value == null || shown.none { it.contains(value) })
        // The genre, then the emulator's name, give way (an ellipsis) before a cell leaves, so the others keep their place; any other text is whole.
        val notGenre = all.filter { !text(it).startsWith("Action") && !text(it).startsWith("RetroArch") }
        assertTrue("no text but the genre and the emulator ends in an ellipsis: $shown", notGenre.none(::isEllipsized) && notGenre.none { text(it).contains("…") })
        for ((i, a) in all.withIndex()) {
            for (b in all.drop(i + 1)) {
                val x = a.boundsInRoot
                val y = b.boundsInRoot
                val overlaps = x.left < y.right - 0.5f && y.left < x.right - 0.5f && x.top < y.bottom - 0.5f && y.top < x.bottom - 0.5f
                assertTrue("'${text(a)}' $x overlaps '${text(b)}' $y", !overlaps)
            }
        }
        // The battery cell, when it is there, is never drawn cut.
        val cell = findTagged(root, HomeTags.DETAILS_BATTERY)?.takeIf { it.layoutInfo.isPlaced }
        val column = findTagged(root, HomeTags.DETAILS)!!.boundsInRoot
        for (each in all) {
            val box = each.boundsInRoot
            assertTrue("'${text(each)}' is cut or outside the column: $box of ${each.size}", box.height + 1f >= each.size.height && box.bottom <= column.bottom + 1f)
        }
        if (cell != null) assertTrue("$font: battery value on screen: ${texts(cell).map(::text)}", texts(cell).any { text(it).endsWith("14 h 15 min") })
    }
}
