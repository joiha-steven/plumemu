package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.EmulatorToStart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home with the details column at the sizes people have (832x468 and the Thor's 837x471), Medium Jost, Graphite and Paper: a game with
 * every fact, a game with two rows only, each with and without a synopsis (it shows only where the cover leaves room).
 */
abstract class HomeDetailsNormalSizeBase(private val label: String) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Plays(private val play: GamePlay) : HomePlayFactsSource {
        override suspend fun read(gameId: Long) = play
    }

    private fun shoot(
        scene: String,
        preset: ReferenceShots.Preset,
        twoRows: Boolean,
        synopsis: Boolean,
    ) {
        val id = LibraryLikeContent.idAt(LibraryLikeContent.SUPER_METROID).toString()
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = if (twoRows) "" else "Action", players = "1", description = "", rating = if (twoRows) null else SUPER_METROID_RATING)
        val base = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details)
        val source = coverArtSource(content = base, synopses = if (synopsis) mapOf(artKey(id) to SUPER_METROID_SYNOPSIS) else emptyMap(), ratings = if (twoRows) emptyMap() else mapOf(artKey(id) to SUPER_METROID_RATING))
        if (!twoRows) {
            @Suppress("DEPRECATION")
            RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, BATTERY_LINE_SAMPLES[1].level).putExtra(BatteryManager.EXTRA_SCALE, 100))
        }
        val play = GamePlay(2, 252, EmulatorToStart("RetroArch: Snes9x", isInstalled = true).takeIf { !twoRows }, batteryPace = BATTERY_LINE_SAMPLES[1].pace.takeIf { !twoRows })
        compose.setContent {
            PlumTheme(preset.settings) {
                CompositionLocalProvider(LocalHomePlayFacts provides Plays(play)) { HomeWithArt(source, LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), base) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/home_details_${scene}_${label}_${preset.label}.png")
    }

    @Test fun allFactsGraphite() = shoot("all_facts", ReferenceShots.Preset.Graphite, twoRows = false, synopsis = true)

    @Test fun allFactsPaper() = shoot("all_facts", ReferenceShots.Preset.Paper, twoRows = false, synopsis = true)

    @Test fun twoRowsGraphite() = shoot("two_rows", ReferenceShots.Preset.Graphite, twoRows = true, synopsis = true)

    @Test fun twoRowsPaper() = shoot("two_rows", ReferenceShots.Preset.Paper, twoRows = true, synopsis = true)

    @Test fun noSynopsisGraphite() = shoot("no_synopsis", ReferenceShots.Preset.Graphite, twoRows = false, synopsis = false)

    @Test fun noSynopsisPaper() = shoot("no_synopsis", ReferenceShots.Preset.Paper, twoRows = false, synopsis = false)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsNormalSize832Test : HomeDetailsNormalSizeBase("832x468")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsNormalSizeThorTest : HomeDetailsNormalSizeBase("837x471")
