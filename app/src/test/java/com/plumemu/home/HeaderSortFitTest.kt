package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The top bar of a long-named system with a four-digit count, sorted Recently added, at 576x432 in the longest
 * languages and every text size: the name is whole on one line; the sort, then the count, give way whole
 * before anything is cut. Shots at Small and Largest.
 */
abstract class HeaderSortFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var size by mutableStateOf(TextSize.Small)

    private val content =
        HomeContent.of(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(SystemGames("nes", NAME, (0 until 1_234).map { GameRowModel("nes-$it", "Game %04d".format(it), "USA") })),
            otherSort = SortGamesSetting.RecentlyAdded,
        )

    private fun show() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                HomeAt(content, listOf(Action.TapItem(Column.Systems, content.entries.indexOfFirst { it.id == "nes" }), Action.Enter))
            }
        }
        compose.waitForIdle()
    }

    /** The texts the top bar draws, the clock left out. */
    private fun barTexts(): List<String> {
        val bar = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).fetchSemanticsNode()
        return generateSequence(listOf(bar)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }
            .filterNot { it.contains(":") || it.endsWith("%") }.toList()
    }

    @Test
    fun theNameAndCountAreWholeAndTheSortIsWholeOrLeftOut() {
        show()
        val report = mutableListOf<String>()
        for (at in TextSize.entries) {
            size = at
            compose.waitForIdle()
            val bar = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).fetchSemanticsNode()
            assertNoTextCut(bar, "top bar at ${at.name}")
            val texts = barTexts()
            assertEquals("the name at ${at.name}", NAME, texts.first())
            // Whole or left out: "count · sort", the count alone, or nothing beside the name.
            val rest = texts.drop(1).singleOrNull()
            assertTrue("the rest at ${at.name}: $rest", rest == null || (rest.startsWith("1") && rest.none { it == '\u2026' }))
            report += "${at.name}: ${rest ?: "(name only)"}"
            if (at == TextSize.Small || at == TextSize.Largest) {
                val lang = RuntimeEnvironment.getQualifiers().substringBefore("-")
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/home_header_sorted_576x432_${lang}_${at.name.lowercase()}.png")
            }
        }
        println("RESULT ${RuntimeEnvironment.getQualifiers()} ${report.joinToString("; ")}")
    }

    private companion object {
        const val NAME = "Nintendo Entertainment System"
    }
}

private const val Q = "w576dp-h432dp-land-xhdpi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-$Q")
class HeaderSortFitFrenchTest : HeaderSortFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-$Q")
class HeaderSortFitSpanishTest : HeaderSortFitBase()
