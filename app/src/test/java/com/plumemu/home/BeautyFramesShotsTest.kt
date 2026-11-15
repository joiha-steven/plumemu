package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.PageGrain
import com.plumemu.coreui.components.pageBackground
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.LocalPageGrain
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.panelAt
import com.plumemu.setup.SetupFakes
import com.plumemu.setup.SetupLayout
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Frames for judging the home clean-up and the page grain: the Super Nintendo list with its details, in a dark and a light set. Written only when
 * `PLUM_SHOTS_DIR` is set (label from `PLUM_SHOTS_LABEL`); without it the test only checks that the screens build. Not compared with goldens.
 */
abstract class BeautyFramesShotsBase(
    private val size: ReferenceShots.Size,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun settingsOf(set: ColorSet) = PlumSettings(appearance = AppearanceSettings().withColors(set), flags = SetupFlags(setupCompleted = true))

    private fun write(name: String) {
        val dir = System.getenv("PLUM_SHOTS_DIR") ?: return
        File(dir).mkdirs()
        File(dir, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val label get() = System.getenv("PLUM_SHOTS_LABEL") ?: "after"
    private val sets = listOf("plumemu" to ColorSet.Plumemu, "daylight" to ColorSet.Daylight)
    private val grains = PageGrain.entries
    private var set by mutableStateOf(ColorSet.Plumemu)
    private var grain by mutableStateOf(PageGrain.Off)

    /** Hosts [body] once in the theme and grain the states hold, then writes one file per set and grain; [name] gives null to skip a pair. */
    private fun shoot(
        grainsToShoot: List<PageGrain>,
        name: (String, PageGrain) -> String?,
        body: @Composable (ColorSet) -> Unit,
    ) {
        compose.setContent { PlumTheme(settingsOf(set).toThemeSettings()) { CompositionLocalProvider(LocalPageGrain provides grain) { body(set) } } }
        for ((setName, s) in sets) {
            for (g in grainsToShoot) {
                val file = name(setName, g) ?: continue
                compose.runOnIdle {
                    set = s
                    grain = g
                }
                compose.waitForIdle()
                write(file)
            }
        }
    }

    /** Wide: the list with its details beside it. Narrow: the games list itself, the stop before the details. */
    @Composable
    private fun SnesList() {
        if (size == ReferenceShots.Size.Narrow) {
            HomeWithArt(rememberSource { realCoverSource() }, gridActions(boardIndexOf("snes"), 4))
        } else {
            RealCoverDetails("snes", 4)
        }
    }

    private fun grainName(g: PageGrain) = "grain-${g.name.lowercase()}"

    @Test
    fun homeList() =
        shoot(grains, { s, g -> "home_${size.label}_${s}_${grainName(g)}_$label.png" }) { SnesList() }

    /** The grain-free home, named as the "before" frames are. */
    @Test
    fun homeListPlain() = shoot(listOf(PageGrain.Off), { s, _ -> "home_${size.label}_${s}_$label.png" }) { SnesList() }

    @Test
    fun settingsColorsRow() =
        shoot(grains, { s, g -> "settings_${size.label}_${s}_${grainName(g)}_$label.png" }) { SettingsAt(optionsAt(SettingsOption.Colors), settingsOf(it)) }

    @Test
    fun posterList() =
        shoot(listOf(PageGrain.Off, PageGrain.Light), { s, g -> "posters_list_${size.label}_${s}_${grainName(g)}_$label.png" }) {
            WithFacts { HomeAt(posterSnesBoard(), listOf(Action.TapItem(Column.Systems, POSTER_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 6))) }
        }

    @Test
    fun posterGrid() =
        shoot(listOf(PageGrain.Off, PageGrain.Light), { s, g -> "posters_grid_${size.label}_${s}_${grainName(g)}_$label.png" }) {
            ProvideGridSize(GridSize.Medium) { HomeAt(posterSnesBoard(), gridActions(POSTER_SNES_INDEX, 1), gridIds = setOf("snes")) }
        }

    private fun band(
        place: String,
        body: @Composable () -> Unit,
    ) {
        if (size != ReferenceShots.Size.Wide) return
        shoot(listOf(PageGrain.Strong), { s, _ -> "band_${place}_${size.label}_${s}_grain-strong.png" }) { body() }
    }

    private fun longSnes(): HomeContent {
        val base = FakeHomeContent.board()
        return base.withEntries(base.entries.map { e -> if (e.id == "snes") e.copy(rows = e.rows + e.rows.map { it.copy(id = it.id + "-b") }) else e })
    }

    /** The list scrolled so its top and bottom edge fades both show. */
    @Test
    fun bandListFade() = band("listfade") { HomeAt(longSnes(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 12)) }

    /** The top bar and the hint bar over the grain. */
    @Test
    fun bandBars() = band("bars") { HomeAt(FakeHomeContent.board(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4)) }

    /** START on a game row: the game menu and its backdrop. */
    @Test
    fun bandGameMenu() =
        band("gamemenu") {
            val content = FakeHomeContent.board()
            val row = content.entries[FakeHomeContent.BOARD_SNES_INDEX].rows[4]
            val panel = OpenPanel(GameRef(row.id, row.title), PanelScreen.Menu(sampleFacts(title = row.title)), focus = 0)
            HomeAt(content, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4), panel = panel)
        }

    /** Settings with Colors' panel open (the options column paints the page colour over its own area). */
    @Test
    fun bandSettingsPanel() = band("settingspanel") { SettingsAt(panelAt(SettingsOption.Colors, PanelPage.ColorSets, 0), settingsOf(set)) }

    /** A setup page, on the page paint MainScreen gives it. */
    @Test
    fun bandSetup() =
        band("setup") {
            Box(Modifier.fillMaxSize().pageBackground()) { SetupLayout(SetupFakes.look, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }, wifi = { false }, charging = { false }) }
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class BeautyFramesShots768Test : BeautyFramesShotsBase(ReferenceShots.Size.Wide)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class BeautyFramesShots576Test : BeautyFramesShotsBase(ReferenceShots.Size.Narrow)

@Composable
private fun WithFacts(content: @Composable () -> Unit) = CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(), content = content)
