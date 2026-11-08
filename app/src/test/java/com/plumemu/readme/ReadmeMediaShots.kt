package com.plumemu.readme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.home.APPS_ENTRY
import com.plumemu.home.EntryKind
import com.plumemu.home.HomeAppIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.plumemu.home.GameRowModel
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeWithArt
import com.plumemu.home.ProvideHomeArt
import com.plumemu.home.SystemInfo
import com.plumemu.platforms.SystemKind
import com.plumemu.home.gridActions
import com.plumemu.home.rememberSource
import com.plumemu.home.typingFor
import com.plumemu.home.withSystemInfo
import com.plumemu.home.LocalMasteredGames
import com.plumemu.game.AchievementsSamples
import com.plumemu.game.BadgeImages
import com.plumemu.game.GameRef
import com.plumemu.game.LocalBadgeImages
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.graph.AchievementLine
import com.plumemu.graph.AchievementsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.plumemu.secondscreen.GAME_WITH_STAR
import com.plumemu.secondscreen.InGameBatterySamples
import com.plumemu.secondscreen.InGameCard
import com.plumemu.secondscreen.SYSTEM_WITH_LAST
import com.plumemu.secondscreen.SecondScreenCard
import com.plumemu.secondscreen.SecondScreenStatisticsSamples
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.BoxArtShots
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.panelAt
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The raw screens for the public README, written to `build/readme-media/raw/<name>.png` (repo root).
 * Skipped unless `-Dplumemu.readme=true` is given, so normal test runs
 * and `verifyRoborazziDebug` never see it and nothing lands in the screenshot references.
 *
 * Every 16:9 shot is 768x432 dp and the 4:3 one 640x480 dp, all at xxhdpi (3x: 2304 and 1920 px
 * wide); the second screen's two shots are the Thor's lower screen (538x468 dp). One clock and one
 * battery (the board's 21:40, 82%), Medium text, and the poster covers of [PosterCoverImages].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ReadmeMediaShots {
    private val outDir = File(File(System.getProperty("user.dir")).parentFile, "build/readme-media/raw")
    private var recordBefore: String? = null

    @Before
    fun enable() {
        assumeTrue("set -Dplumemu.readme=true to render the README's raw screens", System.getProperty("plumemu.readme") == "true")
        outDir.mkdirs()
        // Roborazzi writes only when told to record; this is the one place that says so.
        recordBefore = System.getProperty("roborazzi.test.record")
        System.setProperty("roborazzi.test.record", "true")
    }

    @After
    fun restore() {
        if (System.getProperty("plumemu.readme") != "true") return
        if (recordBefore == null) System.clearProperty("roborazzi.test.record") else System.setProperty("roborazzi.test.record", recordBefore!!)
    }

    // The settings of a ready-made set, Medium text, as the app stores them.
    private fun stored(
        set: ColorSet,
        gridSize: com.plumemu.coredata.settings.GridSizeSetting = com.plumemu.coredata.settings.GridSizeSetting.Medium,
    ): PlumSettings =
        PlumSettings(
            appearance = AppearanceSettings().withColors(set).copy(textSize = TextSizeSetting.Medium, ownColors = null, gridSize = gridSize),
            flags = SetupFlags(setupCompleted = true),
        )

    private fun shoot(
        name: String,
        settings: PlumSettings,
        widthDp: Int = 768,
        heightDp: Int = 432,
        content: @Composable (PlumSettings) -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${heightDp}dp-land-xxhdpi")
        captureRoboImage(filePath = File(outDir, "$name.png").path) { PlumTheme(settings.toThemeSettings()) { content(settings) } }
    }

    private val library: HomeContent by lazy { withRealGames(com.plumemu.home.CoverArtLibrary) }

    private val metroidFocused: List<Action> get() = gridActions(systemIndex("snes"), readmeIndexOf("snes", "Super Metroid"))

    @Composable
    private fun HomeOf(
        actions: List<Action>,
        content: HomeContent = library,
        grid: Set<String> = emptySet(),
        size: GridSize = GridSize.Medium,
        charging: Boolean = false,
    ) = ProvideGridSize(size) { HomeWithArt(rememberSource { posterCoverSource(library) }, actions, content, gridIds = grid, charging = charging) }

    private fun systemIndex(id: String) = library.entries.indexOfFirst { it.id == id }

    private val dreamcastInfo = SystemInfo("DC", "Sega", SystemKind.Console, 1998, 2001)

    private fun withDreamcastInfo(): HomeContent =
        library.withSystemInfo().let { c -> c.withEntries(c.entries.map { if (it.id == "dreamcast") it.copy(info = dreamcastInfo) else it }) }

    @Test fun heroMain() = shoot("hero-main", stored(ColorSet.SpiceCube)) { HomeOf(metroidFocused) }

    @Test fun heroGrid() = shoot("hero-grid", stored(ColorSet.Pocket)) {
            val content = limitedTo(library, "snes", 12)
            ProvideGridSize(GridSize.Medium) {
                ProvideHomeArt(rememberSource { posterCoverSource(library) }, content.hasBoxArt) {
                    HomeAt(content, gridActions(systemIndex("snes"), 5), gridIds = setOf("snes"), showHints = false)
                }
            }
        }

    @Test
    fun heroSystem() = shoot("hero-system", stored(ColorSet.Banana)) { HomeOf(gridActions(systemIndex("dreamcast"), 0), withDreamcastInfo()) }

    @Test
    fun heroSearch() =
        shoot("hero-search", stored(ColorSet.Ivory)) { HomeAt(library, metroidFocused, search = typingFor("super", library)) }

    /** Info's RetroAchievements section beside a list with trophies; made-up achievements, drawn badges. */
    @Test
    fun achievements() {
        val ids = library.withEntries(library.entries.map { e -> if (e.id == "snes") e.copy(rows = e.rows.mapIndexed { i, r -> r.copy(id = (AchievementsSamples.FIRST_ID + i).toString()) }) else e })
        val snes = ids.entries.first { it.id == "snes" }.rows
        val mastered = listOf("Super Mario World", "Super Castlevania IV").mapNotNull { t -> snes.firstOrNull { it.title == t }?.id?.toLong() }.toSet()
        val at = "2026-10-3"
        fun line(id: Int, title: String, text: String, points: Int, earned: Boolean) =
            AchievementLine(id, title, text, points, "badge:$id", "badge:$id", if (earned) "${at}0 12:00:00" else null, null)
        val lines =
            listOf(
                line(1, "Morph Ball", "Find the Morph Ball", 5, true),
                line(2, "Bombs Away", "Find the Bombs and use them on the first wall", 10, true),
                line(3, "Ice Beam", "Find the Ice Beam", 10, true),
                line(4, "High Jump", "Find the High Jump Boots", 10, false),
                line(5, "Speed Booster", "Find the Speed Booster and break the block", 25, false),
                line(6, "Plasma Beam", "Defeat the guardian of the deep caves", 25, false),
            )
        val state = AchievementsState.Ready("Super Metroid", lines.size, 3, 0, lines)
        val badges =
            object : BadgeImages {
                override fun cached(url: String, sizePx: Int) = draw(url, sizePx)

                override suspend fun load(url: String, sizePx: Int) = draw(url, sizePx)

                private fun draw(url: String, size: Int): androidx.compose.ui.graphics.ImageBitmap {
                    val n = url.substringAfter(':').toInt()
                    val tops = intArrayOf(0xFFFF8A65.toInt(), 0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt(), 0xFFFFD54F.toInt())
                    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = tops[(n - 1) % tops.size]
                    c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.2f, size * 0.2f, p)
                    p.color = 0x55FFFFFF
                    c.drawCircle(size / 2f, size / 2f, size * 0.3f, p)
                    p.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(size / 2f, size / 2f, size * 0.12f, p)
                    return bmp.asImageBitmap()
                }
            }
        shoot("achievements", stored(ColorSet.ColdGrey)) {
            val screen = remember { PanelScreen.Info(AchievementsSamples.facts(state).copy(folderName = "ROMs/snes", emulator = com.plumemu.game.EmulatorToStart("Snes9x", isInstalled = true), sizeBytes = 3L * 1024 * 1024, lastPlayedAt = System.currentTimeMillis() - 86_400_000L)) }
            val panel = remember(screen) { OpenPanel(GameRef(snes.first { it.title == "Super Metroid" }.id, "Super Metroid"), screen) }
            CompositionLocalProvider(LocalMasteredGames provides mastered, LocalBadgeImages provides badges) {
                ProvideGridSize(GridSize.Medium) {
                    ProvideHomeArt(rememberSource { posterCoverSource(ids) }, ids.hasBoxArt) { HomeAt(ids, metroidFocused, panel = panel) }
                }
            }
            LaunchedEffect(screen) {
                repeat(2) { withFrameNanos { } }
                repeat(READMEDOWNS) { if (screen.scroll.press(isDown = true)) withFrameNanos { } }
            }
        }
    }

    @Test
    fun sets() {
        for (set in ColorSet.entries) shoot("sets-${set.name.lowercase()}", stored(set)) { HomeOf(metroidFocused) }
    }

    @Test
    fun gridDark() = shoot("grid-dark", stored(ColorSet.Plumemu)) { HomeOf(gridActions(systemIndex("snes"), 4), limitedTo(library, "snes", 6), grid = setOf("snes"), size = GridSize.Large) }

    /** Apps as a grid of icons: made-up apps with plain generated icons (no real logos). */
    @Test
    fun appsGrid() {
        val apps = APP_NAMES.mapIndexed { i, name -> GameRowModel("app-$i", name, region = "") }
        val content = library.withEntries(library.entries.map { if (it.kind == EntryKind.Apps) it.copy(rows = apps, shownCount = apps.size) else it })
        shoot("apps-grid", stored(ColorSet.ColdGrey)) {
            CompositionLocalProvider(LocalHomeAppIcons provides remember { PosterAppIcons() }) {
                HomeOf(gridActions(APPS_ENTRY, 7), content, grid = setOf(HomeContent.APPS_ID))
            }
        }
    }

    @Test
    fun gridLight() = shoot("grid-light", stored(ColorSet.Daylight)) { HomeOf(gridActions(systemIndex("psx"), readmeIndexOf("psx", "Final Fantasy VII")), limitedTo(library, "psx", 15), grid = setOf("psx"), size = GridSize.Small) }

    @Test
    fun systemInfo() = shoot("system-info", stored(ColorSet.Ivory)) { HomeOf(gridActions(systemIndex("snes"), 0), library.withSystemInfo(), charging = true) }

    private fun typedQuery(query: String) =
        com.plumemu.home.search.SearchState().apply {
            open()
            onQueryChange(query)
            showResults(query, com.plumemu.home.search.SearchIndex.of(library).find(query))
        }

    @Test
    fun keyboard() = shoot("keyboard", stored(ColorSet.SpiceCube)) { HomeAt(library, metroidFocused, search = typedQuery("zelda")) }

    @Test
    fun fourThree() {
        val wine =
            PlumSettings(
                appearance =
                    AppearanceSettings(
                        background = ColorSetting.Custom(0x7A1424),
                        textColor = TextColorSetting.Light,
                        accent = ColorSetting.Custom(0xFFB4A8),
                        textSize = TextSizeSetting.Medium,
                    ),
                flags = SetupFlags(setupCompleted = true),
            )
        shoot("four-three", wine, 640, 480) { HomeOf(metroidFocused) }
    }

    @Test
    fun systemCodes() =
        shoot("system-codes", stored(ColorSet.Plumemu)) { HomeOf(metroidFocused, library.withLeftEdge(LeftEdgeSetting.Codes)) }

    @Test
    fun colors() =
        shoot("colors", stored(ColorSet.RedAndGold)) { s -> SettingsAt(panelAt(SettingsOption.Colors, PanelPage.ColorSets, ColorSet.RedAndGold.ordinal), s) }

    @Test
    fun buttonColors() = shoot("button-colors", stored(ColorSet.Banana)) { HomeOf(gridActions(systemIndex("snes"), readmeIndexOf("snes", "Zelda: A Link to the Past"))) }

    @Test
    fun settings() = shoot("settings", stored(ColorSet.PeaGreen)) { s -> SettingsAt(
                optionsAt(SettingsOption.RomFolders),
                s,
                ShotContent.library.copy(account = BoxArtShots.SIGNED_IN, boxArt = BoxArtShots.done, boxArtBytes = BoxArtShots.BOX_ART_BYTES, accountLimits = BoxArtShots.limits),
            ) }

    @Test
    fun update() {
        val release =
            UpdateManifest(
                versionCode = 901,
                versionName = "0.1.3",
                apkUrl = "https://example.invalid/plumemu.apk",
                size = 8_100_000,
                sha256 = "ab".repeat(32),
                minSdk = 23,
                released = "2026-11-07",
                notes = listOf("Lists, menus and panels run under the bars and fade there.", "Covers sit in a glass frame, a little further apart.", "Settings falls from 53 rows to about 40."),
                notesUrl = null,
            )
        val content = SettingsContent(update = UpdateView(lastCheck = LastCheck(1_793_784_600_000L, CheckOutcome.Found), found = release))
        val row = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1))
        shoot("update", stored(ColorSet.Daylight)) { s -> SettingsAt(row.copy(column = SettingsColumn.Panel, page = PanelPage.Update, panelIndex = 0), s, content, version = "0.1.2") }
    }

    @Test
    fun batteryInGame() =
        shoot("battery-in-game", stored(ColorSet.Plumemu), 538, 468) {
            val s = InGameBatterySamples.heavy
            InGameCard(
                time = InGameBatterySamples.format("H:mm", InGameBatterySamples.at(s.minute)),
                battery = s.reading,
                wifi = s.wifi,
                shift = { s.shift },
                part = { s.part },
                formatTime = InGameBatterySamples.time24,
            )
        }

    @Test
    fun systemCard() =
        shoot("system-card", stored(ColorSet.Plumemu), 538, 468) {
            SecondScreenCard(SYSTEM_WITH_LAST, images = SecondScreenStatisticsSamples.covers())
        }

    private companion object {
        const val READMEDOWNS = 2
        val APP_NAMES =
            listOf(
                "Browser", "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Music", "Settings",
                "Notes", "Maps", "Weather", "Podcasts", "Photos", "Downloads", "Recorder", "Video", "Mail", "Messages",
                "Phone", "Translate", "Torch", "Updates",
            )
    }
}

/** Drawn app icons for the Apps grid shot: a saturated rounded square per app with a white shape; no real logos. */
internal class PosterAppIcons : HomeAppIcons {
    override val glowDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val generation = MutableStateFlow(0)
    private val memory = HashMap<Pair<String, Int>, androidx.compose.ui.graphics.ImageBitmap>()

    override fun cached(rowId: String, sizePx: Int) = memory[rowId to sizePx]

    override suspend fun load(rowId: String, sizePx: Int) = memory.getOrPut(rowId to sizePx) { draw(rowId.substringAfter('-').toInt(), sizePx) }

    private fun draw(n: Int, size: Int): androidx.compose.ui.graphics.ImageBitmap {
        val tops = intArrayOf(0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFFFA726.toInt(), 0xFF26C6DA.toInt(), 0xFFEC407A.toInt(), 0xFF7E57C2.toInt())
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val s = size.toFloat()
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val base = tops[(n * 3 + n / 8) % tops.size]
        p.shader = android.graphics.LinearGradient(0f, 0f, s, s, base, android.graphics.Color.rgb((android.graphics.Color.red(base) * 0.7).toInt(), (android.graphics.Color.green(base) * 0.7).toInt(), (android.graphics.Color.blue(base) * 0.7).toInt()), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, s, s, s * 0.22f, s * 0.22f, p)
        p.shader = null
        p.color = 0xEEFFFFFF.toInt()
        when (n % 4) {
            0 -> c.drawCircle(s / 2, s / 2, s * 0.24f, p)
            1 -> c.drawRoundRect(s * 0.28f, s * 0.28f, s * 0.72f, s * 0.72f, s * 0.08f, s * 0.08f, p)
            2 -> c.drawPath(android.graphics.Path().apply { moveTo(s * 0.5f, s * 0.26f); lineTo(s * 0.74f, s * 0.7f); lineTo(s * 0.26f, s * 0.7f); close() }, p)
            else -> { c.drawRoundRect(s * 0.26f, s * 0.26f, s * 0.46f, s * 0.74f, s * 0.05f, s * 0.05f, p); c.drawRoundRect(s * 0.54f, s * 0.26f, s * 0.74f, s * 0.74f, s * 0.05f, s * 0.05f, p) }
        }
        return bmp.asImageBitmap()
    }
}
