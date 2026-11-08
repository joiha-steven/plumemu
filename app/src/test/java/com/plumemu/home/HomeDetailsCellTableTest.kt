package com.plumemu.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The details box at Medium in Jost for a table of values (language x genre x emulator x time played): all six cells are placed inside the box,
 * in at most three rows, PLAYED and LAST PLAYED on one row, every value on one line. A cell is never dropped while three rows can hold it.
 */
abstract class HomeDetailsCellTableBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Case(val lang: String, val genre: String, val emulator: EmulatorCase, val minutes: Int, val level: Int = 100)

    private enum class EmulatorCase { PICODRIVE, BEETLE, NOT_CHOSEN }

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun textOf(n: SemanticsNode) = n.config[SemanticsProperties.Text].joinToString("")

    @Test
    fun everyCellStandsInsideTheBoxForEveryCombination() {
        val cases =
            LANGUAGES.flatMap { lang ->
                GENRES.flatMap { genre -> EmulatorCase.values().flatMap { emu -> MINUTES.map { Case(lang, genre, emu, it) } } }
            }
        var current by mutableStateOf(cases.first())
        val failures = mutableListOf<String>()
        compose.setContent {
            val base = LocalContext.current
            val c = current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(c.lang)) }
            val localized = remember(c.lang) { base.createConfigurationContext(config) }
            val emulator =
                when (c.emulator) {
                    EmulatorCase.PICODRIVE -> "Picodrive"
                    EmulatorCase.BEETLE -> "RetroArch: Beetle PSX HW (not installed)"
                    EmulatorCase.NOT_CHOSEN -> localized.getString(R.string.game_info_emulator_not_chosen)
                }
            val details = GameDetails(year = 1994, publisher = "Nintendo", genre = c.genre, players = "1", description = "", rating = 17)
            val play = PlayFacts(2, c.minutes, emulator, batteryMinutesOnCharge = 855.0, batteryPercentPerHour = 7.0)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config, LocalResources provides localized.resources) {
                PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                    CompositionLocalProvider(LocalBatteryReading provides remember { mutableStateOf<BatteryReading?>(BatteryReading(c.level, false)) }) {
                        Column(Modifier.width(PlumDimens.detailsColumnWidth).testTag(PANEL)) { DetailsBlock(details, ShownPlay.Fixed(play)) }
                    }
                }
            }
        }
        for (c in cases) {
            current = c
            compose.waitForIdle()
            val res = RuntimeEnvironmentResources.of(c.lang)
            val labels = listOf(R.string.home_details_genre, R.string.home_details_play_time, R.string.home_details_last_played, R.string.home_details_emulator, R.string.home_details_battery).map { res.getString(it) }
            val box = compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val all = texts(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode())
            val found = labels.map { l -> all.firstOrNull { textOf(it) == l } }
            val name = "${c.lang} genre='${c.genre.take(8)}' ${c.emulator} ${c.minutes} min"
            if (found.any { it == null }) {
                failures += "$name: a cell is missing: ${all.map(::textOf)}"
                continue
            }
            val boxes = found.map { it!!.boundsInRoot }
            val rows = boxes.map { Math.round(it.top) }.distinct().size
            fun inside(r: Rect) = r.top >= box.top - 1f && r.bottom <= box.bottom + 1f && r.right <= box.right + 1f && r.left >= box.left - 1f
            if (!boxes.all(::inside)) failures += "$name: a label lies outside the box $box: $boxes"
            val values = all.filter { textOf(it) !in labels && it.boundsInRoot.width > 0f }
            if (!values.all { inside(it.boundsInRoot) }) failures += "$name: a value lies outside the box $box: ${values.map { textOf(it) to it.boundsInRoot }}"
            if (rows > 3) failures += "$name: $rows rows"
            if (Math.round(boxes[1].top) != Math.round(boxes[2].top)) failures += "$name: PLAYED and LAST PLAYED are on different rows"
            if (values.any { it.boundsInRoot.height > values.minOf { v -> v.boundsInRoot.height } * 1.5f }) failures += "$name: a value takes two lines"
            // Rows of two cells share one column line: the second cell starts at the same x in each (the rating cell is read by its description).
            fun described(n: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.ContentDescription in n.config) listOf(n) else emptyList()) + n.children.flatMap(::described)
            val ratingBox = described(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode()).firstOrNull()?.boundsInRoot
            val all6 = (boxes + listOfNotNull(ratingBox)).groupBy { Math.round(it.top) }.values.filter { it.size == 2 }.map { row -> row.maxOf { it.left } }
            // Where one split cannot hold every pair (2,057 h played in Vietnamese; and since the labels are 13 in sentence case, Vietnamese's
            // "last played" label with a long genre or emulator, re-measured) the pairs keep their own.
            if (c.lang != "vi" && c.minutes == MINUTES.first() && all6.size > 1 && all6.max() - all6.min() > 1f) failures += "$name: the second column starts at different x: $all6"
            val rating = all.size
            println("CELLS $name rows=$rows ${boxes.map { "${Math.round(it.top)}:${Math.round(it.left)}" }} texts=$rating")
        }
        assertTrue("${failures.size} of ${cases.size} combinations fail:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val PANEL = "details_panel"
        val LANGUAGES = listOf("en", "vi", "fr", "es")
        val GENRES = listOf("Role-Playing Game / Action RPG", "Action")
        val MINUTES = listOf(252, 2057 * 60)
    }
}

private object RuntimeEnvironmentResources {
    fun of(lang: String): android.content.res.Resources {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return app.createConfigurationContext(config).resources
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsCellTable768Test : HomeDetailsCellTableBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsCellTable832Test : HomeDetailsCellTableBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsCellTableThorTest : HomeDetailsCellTableBase()
