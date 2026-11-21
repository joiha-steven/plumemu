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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
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
 * The details box at Small in Jost for a table of values (language x genre x emulator x time played): all six facts are placed inside the box,
 * one to a line in order at one pitch, the labels at the left and the values at one x (every language), every value on one line. A fact is never
 * dropped while the box can hold six lines.
 */
abstract class HomeDetailsCellTableBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Case(val lang: String, val genre: String, val emulator: EmulatorCase, val minutes: Int, val level: Int = 100)

    private enum class EmulatorCase { PICODRIVE, BEETLE, NOT_CHOSEN }

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
            // The six labels of the language, in the order they are read; the contract is read by position and named in English.
            val labels = listOf(R.string.home_details_genre, R.string.home_details_rating_label, R.string.home_details_play_time, R.string.home_details_last_played, R.string.home_details_emulator, R.string.home_details_battery).map { res.getString(it) }
            val box = compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val name = "${c.lang} genre='${c.genre.take(8)}' ${c.emulator} ${c.minutes} min"
            val lines = readFactLines(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode(), labels).map { FactLine(FACT_ORDER[labels.indexOf(it.name)], it.label, it.value, it.valueLines) }
            if (lines.size != 6) failures += "$name: ${lines.size} facts on show, not six: ${lines.map { it.name }}"
            failures += factLineProblems(box, lines).map { "$name: $it" }
            println("CELLS $name ${lines.map { "${it.name}:${Math.round(it.label.top)}:${Math.round(it.label.left)}" }}")
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
