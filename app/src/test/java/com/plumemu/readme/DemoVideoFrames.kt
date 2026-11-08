package com.plumemu.readme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.home.APPS_ENTRY
import com.plumemu.home.CoverArtLibrary
import com.plumemu.home.GameRowModel
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.HomeAt
import com.plumemu.home.ProvideHomeArt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rememberSource
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.settings.withColors
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripIntent
import com.plumemu.coreui.components.keyboard.KeyboardDirection
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardModel
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.home.EntryKind
import com.plumemu.home.HomeContent
import com.plumemu.home.entry
import com.plumemu.home.homeAction
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.panelAt
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The demo video's raw frames (the README of the demo-video tool folder explains the build):the real main screen at the Nova's
 * 576x432 dp at 280 dpi (1008x756 px: 1.75x, drawn natively, never resampled), one PNG per 16 ms frame of
 * the test clock with a key press log. Skipped unless `-Dplumemu.demo=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class DemoVideoFrames {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val out = File(File(System.getProperty("user.dir")).parentFile, "build/demo-video/frames")

    @Before
    fun enable() {
        assumeTrue("set -Dplumemu.demo=true", System.getProperty("plumemu.demo") == "true")
        RuntimeEnvironment.setQualifiers("w576dp-h432dp-land-280dpi")
    }

    private fun settings(set: ColorSet) =
        PlumSettings(appearance = AppearanceSettings().withColors(set).copy(textSize = TextSizeSetting.Medium, ownColors = null), flags = SetupFlags(setupCompleted = true))

    /** One take: its frames in `frames/<name>/`, its presses in `frames/<name>/events.txt` (frame, key, sound). */
    private inner class Take(val name: String) {
        val dir = File(out, name).also { it.deleteRecursively(); it.mkdirs() }
        var n = 0
        val log = StringBuilder()
        var colours = ""

        fun frame(count: Int = 1) = repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "f%05d.png".format(n++)).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }

        fun seconds(s: Double) = frame(Math.round(s * PACE * 60).toInt())

        /** [apply] runs inside the frame the press is logged at; [sound] is what the app plays for it (move, confirm, back or none). */
        fun press(key: String, sound: String, gap: Double, apply: () -> Unit) {
            log.append("$n $key $sound\n")
            compose.runOnIdle(apply)
            seconds(gap)
        }

        fun note(text: String) = log.append("$n NOTE $text\n")

        fun finish() {
            File(dir, "events.txt").writeText(log.toString())
            File(dir, "colours.txt").writeText(colours)
            println("DEMO $name frames=$n")
        }
    }

    /** Scales every wait; 1.0 is the pace written in the takes (tuned so the whole run is about a minute). */
    private val PACE = 1.0

    private fun begin() {
        compose.mainClock.autoAdvance = false
    }

    private val library: HomeContent by lazy { withRealGames(CoverArtLibrary) }

    private fun entryIndex(id: String) = library.entries.indexOfFirst { it.id == id }

    /** Shortest key presses on Plumemu's keyboard to a key whose press makes [wanted]. */
    private fun keyPath(from: KeyboardModel, wanted: (KeyboardEvent?) -> Boolean): List<KeyboardDirection> {
        val seen = HashSet<Any>()
        val queue = ArrayDeque<Pair<KeyboardModel, List<KeyboardDirection>>>()
        queue += from to emptyList()
        while (queue.isNotEmpty()) {
            val (model, path) = queue.removeFirst()
            if (wanted(model.press().second)) return path
            if (!seen.add(model.focus)) continue
            for (d in KeyboardDirection.entries) queue += model.move(d, false).first to (path + d)
        }
        error("no key")
    }

    @Test
    fun home() {
        val take = Take("home")
        begin()
        val grids = mutableStateOf(emptySet<String>())
        val panelState = mutableStateOf<OpenPanel?>(null)
        val search = SearchState()
        var searchOn by mutableStateOf(false)
        lateinit var state: StripState
        var pageRows = 6
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(settings(ColorSet.SpiceCube).toThemeSettings()) {
                    val bgNow = PlumTheme.colors.bg.value
                    val accentNow = PlumTheme.colors.accent.value
                    val rowsNow = PlumTheme.layout.listRowsThatFit
                    SideEffect { take.colours = "$bgNow $accentNow"; pageRows = rowsNow }
                    ProvideGridSize(GridSize.Medium) {
                        ProvideHomeArt(rememberSource { posterCoverSource(library) }, library.hasBoxArt) {
                            HomeAt(library, isMotionOn = PlumTheme.motion.isOn, onState = { state = it }, gridIds = grids.value, panel = panelState.value, search = if (searchOn) search else null)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val keyDirs = mapOf("DOWN" to Direction.Down, "UP" to Direction.Up, "LEFT" to Direction.Left, "RIGHT" to Direction.Right)
        fun raw(key: String): Action = when (key) {
            in keyDirs -> Action.Move(keyDirs.getValue(key))
            "A" -> Action.Enter; "B" -> Action.Back; "X" -> Action.Search; "Y" -> Action.ToggleFavorite
            "L1" -> Action.PreviousSystem; "R1" -> Action.NextSystem; "START" -> Action.Menu; "SELECT" -> Action.ToggleView
            else -> error(key)
        }
        var menuFocus = 0
        fun strip(key: String, sound: String, gap: Double) = take.press(key, sound, gap) {
            val intents = state.dispatch(homeAction(raw(key)))
            for (i in intents) when (i) {
                is StripIntent.OpenGameMenu -> {
                    val g = library.entry(i.systemId)!!.rows[i.index]
                    menuFocus = 0
                    panelState.value = OpenPanel(GameRef(g.id, g.title), PanelScreen.Menu(sampleFacts(title = g.title).copy(systemName = "Super Nintendo", emulator = com.plumemu.game.EmulatorToStart("Snes9x", isInstalled = true), canFixBoxArt = true)), focus = 0)
                }
                StripIntent.OpenSearch -> { search.open(); searchOn = true }
                is StripIntent.ToggleView -> grids.value = if (i.systemId in grids.value) grids.value - i.systemId else grids.value + i.systemId
                is StripIntent.JumpLetter -> {
                    val rows = library.entry(i.systemId)!!.rows.size
                    state.focusRow((if (i.forward) i.fromIndex + pageRows else i.fromIndex - pageRows).coerceIn(0, rows - 1))
                }
                else -> Unit
            }
        }
        fun menu(key: String, sound: String, gap: Double) = take.press(key, sound, gap) {
            val p = panelState.value!!
            when (key) {
                "DOWN" -> menuFocus++
                "UP" -> menuFocus--
            }
            panelState.value = if (key == "B") null else p.copy(focus = menuFocus)
        }
        fun typeKey(wanted: (KeyboardEvent?) -> Boolean, sound: String, gap: Double) {
            for (d in keyPath(search.keys.model, wanted)) {
                val key = d.name.uppercase()
                take.press(key, "move", 0.16) { search.onAction(Action.Move(Direction.valueOf(d.name))) }
            }
            take.press("A", sound, gap) { search.onAction(Action.Enter) }
            compose.runOnIdle { search.showResults(search.query, SearchIndex.of(library).find(search.query)) }
        }

        // Fast pace: 0.28-0.35 s between moves in a burst, a beat of 0.45-0.5 s before a confirm.
        fun burst(key: String, times: Int, gap: Double = 0.28) = repeat(times) { strip(key, "move", gap) }
        // Scene 1: the systems, down the list and back up to Super Nintendo (computed, so it follows the list).
        take.note("Move between your systems")
        take.seconds(0.8)
        val snes = entryIndex("snes")
        val far = snes + 5
        burst("DOWN", far)
        burst("UP", far - snes, 0.26)
        // Scene 2: a system's games.
        take.note("Open a system and scroll its games")
        strip("A", "confirm", 0.6)
        burst("DOWN", 12)
        // pages go forward, back, forward: a second jump the same way in a row is ignored
        strip("R1", "move", 1.0); strip("L1", "move", 1.0); strip("R1", "move", 1.0)
        burst("DOWN", 5); strip("UP", "move", 0.45)
        take.note("Grid")
        strip("SELECT", "confirm", 0.9)
        strip("RIGHT", "move", 0.32); strip("DOWN", "move", 0.32); strip("LEFT", "move", 0.32)
        strip("DOWN", "move", 0.32); strip("RIGHT", "move", 0.32); strip("RIGHT", "move", 0.32); strip("UP", "move", 0.5)
        // Scene 3: the game menu.
        take.note("Press START on a game for its menu")
        strip("START", "confirm", 0.7)
        menu("DOWN", "move", 0.4); menu("DOWN", "move", 0.4); menu("DOWN", "move", 0.4); menu("UP", "move", 0.55)
        menu("B", "back", 0.5)
        // Scene 4: search.
        take.note("Press X to search")
        strip("X", "confirm", 0.55)
        typeKey({ it == KeyboardEvent.Type('z') }, "confirm", 0.25)
        typeKey({ it == KeyboardEvent.Type('e') }, "confirm", 0.25)
        typeKey({ it == KeyboardEvent.Type('l') }, "confirm", 0.25)
        typeKey({ it == KeyboardEvent.Type('d') }, "confirm", 0.25)
        typeKey({ it == KeyboardEvent.Type('a') }, "confirm", 0.8)
        take.press("START", "move", 0.8) { search.onAction(Action.Menu) }
        take.press("DOWN", "move", 0.35) { search.onAction(Action.Move(Direction.Down)) }
        take.press("DOWN", "move", 0.35) { search.onAction(Action.Move(Direction.Down)) }
        take.press("B", "back", 0.5) { search.close(); searchOn = false }
        // Scene 5: Recent and Favorites.
        take.note("Recent and Favorites")
        strip("B", "back", 0.5)
        strip("UP", "move", 0.55); strip("UP", "move", 0.55); strip("UP", "move", 0.8)
        strip("A", "confirm", 0.6)
        burst("DOWN", 3, 0.35); strip("B", "back", 0.5)
        strip("DOWN", "move", 0.5)
        strip("A", "confirm", 0.6)
        burst("DOWN", 3, 0.35)
        take.seconds(0.4)
        take.finish()
    }

    private val appNames = listOf(
        "Browser", "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Music", "Settings",
        "Notes", "Maps", "Weather", "Podcasts", "Photos", "Downloads", "Recorder", "Video", "Mail", "Messages",
    )

    @Test
    fun apps() {
        val take = Take("apps")
        begin()
        val rows = appNames.mapIndexed { i, name -> GameRowModel("app-$i", name, region = "") }
        val content = library.withEntries(library.entries.map { if (it.kind == EntryKind.Apps) it.copy(rows = rows, shownCount = rows.size) else it })
        lateinit var state: StripState
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(settings(ColorSet.ColdGrey).toThemeSettings()) {
                    val bgNow = PlumTheme.colors.bg.value
                    val accentNow = PlumTheme.colors.accent.value
                    SideEffect { take.colours = "$bgNow $accentNow" }
                    CompositionLocalProvider(LocalHomeAppIcons provides remember { PosterAppIcons() }) {
                        ProvideGridSize(GridSize.Large) {
                            ProvideHomeArt(rememberSource { posterCoverSource(library) }, library.hasBoxArt) {
                                HomeAt(content, isMotionOn = PlumTheme.motion.isOn, onState = { state = it }, gridIds = setOf(HomeContent.APPS_ID))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Systems, APPS_ENTRY)) }
        take.note("Apps")
        take.seconds(0.8)
        fun key(k: String, action: Action, sound: String, gap: Double) = take.press(k, sound, gap) { state.dispatch(action) }
        key("A", Action.Enter, "confirm", 0.8)
        for (d in listOf(Direction.Right, Direction.Right, Direction.Down, Direction.Left, Direction.Down, Direction.Right, Direction.Right, Direction.Down, Direction.Left, Direction.Down, Direction.Right, Direction.Right, Direction.Up)) {
            key(d.name.uppercase(), Action.Move(d), "move", 0.32)
        }
        take.seconds(0.8)
        take.finish()
    }

    @Test
    fun settings() {
        val take = Take("settings")
        begin()
        var focus by mutableStateOf(optionsAt(SettingsOption.Colors))
        var set by mutableStateOf(ColorSet.SpiceCube)
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                val s = settings(set)
                PlumTheme(s.toThemeSettings()) {
                    val bgNow = PlumTheme.colors.bg.value
                    val accentNow = PlumTheme.colors.accent.value
                    SideEffect { take.colours = "$bgNow $accentNow" }
                    SettingsAt(focus, s)
                }
            }
        }
        compose.waitForIdle()
        take.note("Settings: browse")
        take.seconds(0.8)
        val browse = listOf(SettingsOption.Accent, SettingsOption.TextSize)
        for (o in browse) take.press("DOWN", "move", 0.45) { focus = optionsAt(o) }
        for (o in browse.dropLast(1).reversed() + SettingsOption.Colors) take.press("UP", "move", 0.3) { focus = optionsAt(o) }
        take.seconds(0.2)
        take.note("Settings: pick a colour set")
        take.press("A", "confirm", 0.7) { focus = panelAt(SettingsOption.Colors, PanelPage.ColorSets, set.ordinal) }
        for (i in 1..4) {
            val next = ColorSet.entries[set.ordinal + 1]
            take.press("RIGHT", "move", 0.75) { set = next; focus = panelAt(SettingsOption.Colors, PanelPage.ColorSets, next.ordinal) }
        }
        take.press("LEFT", "move", 0.75) { set = ColorSet.Banana; focus = panelAt(SettingsOption.Colors, PanelPage.ColorSets, set.ordinal) }
        take.press("B", "back", 0.7) { focus = optionsAt(SettingsOption.Colors) }
        take.seconds(0.2)
        take.finish()
    }

    @Test
    fun homeAgain() {
        val take = Take("home-again")
        begin()
        lateinit var state: StripState
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(settings(ColorSet.Banana).toThemeSettings()) {
                    val bgNow = PlumTheme.colors.bg.value
                    val accentNow = PlumTheme.colors.accent.value
                    SideEffect { take.colours = "$bgNow $accentNow" }
                    ProvideGridSize(GridSize.Medium) {
                        ProvideHomeArt(rememberSource { posterCoverSource(library) }, library.hasBoxArt) {
                            HomeAt(library, isMotionOn = PlumTheme.motion.isOn, onState = { state = it })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Systems, entryIndex("snes"))) }
        take.note("Back on the home screen")
        take.seconds(0.7)
        take.press("DOWN", "move", 0.35) { state.dispatch(Action.Move(Direction.Down)) }
        take.press("DOWN", "move", 0.35) { state.dispatch(Action.Move(Direction.Down)) }
        take.press("UP", "move", 0.35) { state.dispatch(Action.Move(Direction.Up)) }
        take.press("UP", "move", 1.0) { state.dispatch(Action.Move(Direction.Up)) }
        take.finish()
    }
}
