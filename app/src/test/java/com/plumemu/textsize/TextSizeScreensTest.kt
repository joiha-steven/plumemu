package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TextWeight
import com.plumemu.game.Beetle
import com.plumemu.game.DuckStation
import com.plumemu.game.EmulatorItem
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.Epsxe
import com.plumemu.game.BOX_ART_NONE_KEY
import com.plumemu.game.BOX_ART_SEARCH_KEY
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtProblem
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.BoxArtStoreOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.FixNameTags
import com.plumemu.game.boxArtHitKey
import com.plumemu.game.boxArtStoredContent
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.game.FixNameState
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.launchMessage
import com.plumemu.game.sampleFacts
import com.plumemu.game.sampleListing
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.CoverDetailsBoard
import com.plumemu.home.CoverGridBoard
import com.plumemu.home.CoverGridPortraitBoard
import com.plumemu.home.FINAL_FANTASY_VI
import com.plumemu.home.MEGA_MAN_X
import com.plumemu.home.PERSONA_3_PORTABLE
import com.plumemu.home.coverArtSource
import com.plumemu.home.rememberSource
import com.plumemu.home.BoardSystemsFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeContent
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.ShotPresses
import com.plumemu.settings.screen.SystemsShot
import com.plumemu.settings.screen.SystemsShots
import com.plumemu.settings.screen.RescanShots
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.optionsAt
import com.plumemu.account.AccountShots
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.settings.screen.AccountShotContent
import com.plumemu.settings.screen.BoxArtConfirmItem
import com.plumemu.settings.screen.BoxArtShots
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.accountPageItems
import com.plumemu.settings.screen.SignInFormAt
import com.plumemu.setup.HomeRolePromptAt
import com.plumemu.setup.HomeRoleTags
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupControl
import com.plumemu.setup.SetupFakes
import com.plumemu.setup.SetupTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every text size on the reference screens (design/rules.md › Type). On each screen, at each of the
 * six sizes in Plum Sans and at Large and Largest in Serif and Mono (wider and taller lines): no text
 * is ellipsized, clipped, taller than its box or on top of another text (`TextFit.kt` says what a
 * scrolling list may do), and the focused item is fully on screen. At the largest size the 837x471
 * and 576x432 renderings of the main screens are also kept as screenshots (`textsize_largest_*`).
 */
/** Wide layouts start at 768 dp. */
private const val WIDE_FROM_DP = 768

abstract class TextSizeScreensBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val takesShots: Boolean get() = (widthDp == 837) || (widthDp == 576)

    /** What a screen says about itself to the check. */
    private class Spec(
        val rootTag: String? = null,
        val scrollTags: Set<String> = emptySet(),
        val ellipsisUnder: String? = null,
        /** The smallest text size at which a text under [ellipsisUnder] may end in an ellipsis (Largest unless a screen says why). */
        val ellipsisFrom: TextSize = TextSize.Largest,
        /** Finds the focused item under the whole screen's root; null for a screen with none. */
        val focused: ((SemanticsNode) -> SemanticsNode?)? = null,
        val shoot: Boolean = false,
        /** What must be whole on screen at Largest in Serif or Mono instead, where [focused] cannot fit by design. */
        val focusedWhenTooTall: ((SemanticsNode) -> SemanticsNode?)? = null,
        /** Something on the screen always animates (a cursor), so it never goes idle: step the clock instead. */
        val isAnimated: Boolean = false,
        /** Typed texts that slide when wider than their field ([FitAllowance.slidingText]). */
        val slidingText: Set<String> = emptySet(),
        /** A text cut to its height by design ([FitAllowance.cutToFit]). */
        val cutToFit: String? = null,
        val readThrough: InfoReadThrough? = null,
    )

    private fun check(
        name: String,
        spec: Spec = Spec(),
        screen: @Composable () -> Unit,
    ) {
        // The largest first: a fresh composition at that size is what the shot shows.
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        var weight by mutableStateOf(TextWeight.Default)
        if (spec.isAnimated) compose.mainClock.autoAdvance = false
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font, textWeight = weight)) { screen() } }
        // Bold is the heaviest text: the widest the same words get, so Largest in Sans and Mono at Bold is the worst case.
        val combinations =
            (listOf(PlumFont.Sans to TextSize.Largest) + TextSize.entries.filter { it != TextSize.Largest }.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }).map { Triple(it.first, it.second, TextWeight.Default) } +
                listOf(PlumFont.Sans, PlumFont.Mono).map { Triple(it, TextSize.Largest, TextWeight.Bold) }
        val failures = mutableListOf<String>()
        for ((eachFont, each, eachWeight) in combinations) {
            font = eachFont
            weight = eachWeight
            size = each
            if (spec.isAnimated) compose.mainClock.advanceTimeBy(ANIMATED_STEP_MS) else compose.waitForIdle()
            if (spec.shoot && takesShots && each == TextSize.Largest && eachFont == PlumFont.Sans && eachWeight == TextWeight.Default) {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/textsize_largest_${name}_${widthDp}x${heightDp}_graphite.png")
            }
            val screenRoot = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val root = if (spec.rootTag == null) screenRoot else compose.onNodeWithTag(spec.rootTag, useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} (${each.factor}) ${eachFont.name} ${eachWeight.name} on ${widthDp}x$heightDp"
            (textProblems(root, FitAllowance(spec.scrollTags, spec.ellipsisUnder, isLargest = each.factor >= spec.ellipsisFrom.factor, stripTags = homeLists + HomeTags.DETAILS, cutToFit = spec.cutToFit, slidingText = spec.slidingText)) + spec.readThrough?.unreadRows(compose).orEmpty()).mapTo(failures) { "$label: $it" }
            (if (each == TextSize.Largest && eachFont != PlumFont.Sans) spec.focusedWhenTooTall ?: spec.focused else spec.focused)?.let { find ->
                val node = find(screenRoot)
                if (node == null) failures += "$label: the focused item is not drawn" else notFullyShown(node, screenRoot)?.let { failures += "$label: the focused item $it" }
            }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private fun tagged(tag: String): (SemanticsNode) -> SemanticsNode? = { findTagged(it, tag) }

    /** The row the panel's cursor is on, found by its own key (rows scrolled out of a lazy list have no box to sort by). */
    private fun focusedRow(panel: OpenPanel): (SemanticsNode) -> SemanticsNode? = tagged(GamePanelTags.row(panel.content.focusableRows[panel.focus].key))

    private val homeLists = setOf(HomeTags.SYSTEMS, HomeTags.GAMES)
    private val settingsLists = setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS)
    private val game = GameRef("snes-6", "Super Metroid")
    private val facts =
        sampleFacts(title = "Super Metroid").copy(
            systemName = "Super Nintendo",
            regions = "USA,EUR",
            fileName = "Super Metroid.sfc",
            folderName = "ROMs/snes",
            sizeBytes = 3L * 1024 * 1024,
            emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
        )
    private val finder =
        TitleFinder(
            listOf(
                TitleChoice("Super Metroid", "Japan, USA, Europe", 1994, "MAME software lists"),
                TitleChoice("Super Metroid (Beta)", "Japan", 1994, "MAME software lists"),
                TitleChoice("Metroid Fusion", "USA, Europe", 2002, "MAME software lists"),
            ),
        )

    private fun panelOver(panel: OpenPanel, content: HomeContent = FakeHomeContent.board()): @Composable () -> Unit = { HomeAt(content, BoardGamesFocused, panel = panel) }

    private fun searchOf(query: String): SearchState {
        val content = FakeHomeContent.withFavorites()
        return SearchState().apply {
            open()
            onQueryChange(query)
            showResults(query, SearchIndex.of(content).find(query))
            // The cursor on the results (START, or a result tapped).
            toResults()
        }
    }

    // Home with box art: the grid's covers and bar, the details column, at every size.
    // A portrait tile's title may end in an ellipsis at Largest only (ellipsisUnder = its tag, not
    // the grid): a portrait cell holds four or five lines there and the bar below names the focused
    // game in full. Landscape tiles must fit at every size.
    private val allArtTitles: Set<String> by lazy { FakeHomeContent.withArtwork().entries.flatMap { e -> e.rows.map { it.title } }.toSet() }

    /** The cover [CoverGridBoard] focuses: row 2 on both, three columns wide, two narrow. */
    private val gridFocus: Int get() = if (widthDp >= WIDE_FROM_DP) MEGA_MAN_X else FINAL_FANTASY_VI

    @Test
    fun homeGridWithArt() =
        check("home_art_grid", Spec(scrollTags = homeLists + HomeTags.GRID, ellipsisUnder = HomeTags.PORTRAIT_TILE_TITLE, focused = tagged(HomeTags.cover(gridFocus)), shoot = true)) {
            CoverGridBoard(rememberSource { coverArtSource() })
        }

    @Test
    fun homeGridWithoutArt() =
        check("home_art_grid_none", Spec(scrollTags = homeLists + HomeTags.GRID, ellipsisUnder = HomeTags.PORTRAIT_TILE_TITLE, focused = tagged(HomeTags.cover(gridFocus)))) {
            CoverGridBoard(rememberSource { coverArtSource(noArt = allArtTitles) })
        }

    @Test
    fun homePortraitGridWithoutArt() =
        check("home_art_grid_portrait_none", Spec(scrollTags = homeLists + HomeTags.GRID, ellipsisUnder = HomeTags.PORTRAIT_TILE_TITLE, focused = tagged(HomeTags.cover(PERSONA_3_PORTABLE)))) {
            CoverGridPortraitBoard(rememberSource { coverArtSource(noArt = allArtTitles) })
        }

    // Grid sizes: the checks above are the board's sizes (landscape Large, portrait Medium); these add Medium
    // landscape and Small (12 / 600 titles). Smaller tiles may cut a tile's title from [cutFrom] (ellipsisUnder = the tile-title tag
    // [tileTitle] only, never the whole grid; every other grid text stays strict; the bar names the focused game in full): Largest in landscape, Large in Small tall.
    private fun sized(name: String, cutFrom: TextSize?, focus: Int, tileTitle: String, board: @Composable () -> Unit) =
        check(name, Spec(scrollTags = homeLists + HomeTags.GRID, ellipsisUnder = if (cutFrom == null) HomeTags.PORTRAIT_TILE_TITLE else tileTitle, ellipsisFrom = cutFrom ?: TextSize.Largest, focused = tagged(HomeTags.cover(focus))), board)

    @Test fun homeGridMediumWithoutArt() = sized("home_art_grid_medium_none", TextSize.Largest, gridFocus, HomeTags.WIDE_TILE_TITLE) { CoverGridBoard(rememberSource { coverArtSource(noArt = allArtTitles) }, GridSize.Medium) }
    @Test fun homeGridSmallWithArt() = sized("home_art_grid_small", null, gridFocus, HomeTags.PORTRAIT_TILE_TITLE) { CoverGridBoard(rememberSource { coverArtSource() }, GridSize.Small) }
    @Test fun homeGridSmallWithoutArt() = sized("home_art_grid_small_none", TextSize.Largest, gridFocus, HomeTags.WIDE_TILE_TITLE) { CoverGridBoard(rememberSource { coverArtSource(noArt = allArtTitles) }, GridSize.Small) }
    @Test fun homePortraitGridSmallWithoutArt() = sized("home_art_grid_portrait_small_none", TextSize.Large, PERSONA_3_PORTABLE, HomeTags.PORTRAIT_TILE_TITLE) { CoverGridPortraitBoard(rememberSource { coverArtSource(noArt = allArtTitles) }, GridSize.Small) }
    @Test fun homePortraitGridLargeWithoutArt() = sized("home_art_grid_portrait_large_none", null, PERSONA_3_PORTABLE, HomeTags.PORTRAIT_TILE_TITLE) { CoverGridPortraitBoard(rememberSource { coverArtSource(noArt = allArtTitles) }, GridSize.Large) }

    @Test
    fun homeDetailsWithArt() =
        check("home_art_details", Spec(scrollTags = homeLists, focused = tagged(HomeTags.DETAILS_TITLE), shoot = true, cutToFit = HomeTags.DETAILS_SYNOPSIS)) {
            CoverDetailsBoard(rememberSource { coverArtSource() })
        }

    @Test
    fun homeDetailsWithoutArt() =
        check("home_art_details_none", Spec(scrollTags = homeLists, focused = tagged(HomeTags.DETAILS_TITLE), cutToFit = HomeTags.DETAILS_SYNOPSIS)) {
            CoverDetailsBoard(rememberSource { coverArtSource(noArt = setOf("Super Metroid")) })
        }

    // Home
    @Test
    fun homeSystems() = check("home_systems", Spec(scrollTags = homeLists, focused = tagged(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX)), shoot = true)) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) }

    @Test
    fun homeGames() = check("home_games", Spec(scrollTags = homeLists, focused = tagged(HomeTags.game(FakeHomeContent.BOARD_REMEMBERED_ROW)), shoot = true)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused) }

    /** A thousand games in every system: the peek column's four-digit counts. */
    @Test
    fun homeGamesWithFourDigitCounts() = check("home_games_1000", Spec(scrollTags = homeLists)) { HomeAt(FakeHomeContent.large(18_000), BoardGamesFocused) }

    @Test
    fun homeSearchResults() =
        check("home_search", Spec(scrollTags = homeLists + SearchTags.RESULTS, isAnimated = true)) { HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, search = searchOf("super")) }

    // Settings
    @Test
    fun settingsAppearanceTextSize() =
        check("settings_text_size", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.TextSize)), shoot = true)) { SettingsAt(optionsAt(SettingsOption.TextSize)) }

    @Test
    fun settingsBackgroundPanel() =
        check("settings_background", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.Background)), shoot = true)) { SettingsAt(optionsAt(SettingsOption.Background)) }

    @Test
    fun settingsAdvanced() =
        check("settings_advanced", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.UsageStatistics)))) { SettingsAt(optionsAt(SettingsOption.UsageStatistics)) }

    @Test
    fun settingsControls() =
        check("settings_controls", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.ShowButtonHints)))) { SettingsAt(optionsAt(SettingsOption.ShowButtonHints)) }

    @Test
    fun settingsHomeScreen() =
        check("settings_home_screen", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.ShowBattery)))) { SettingsAt(optionsAt(SettingsOption.ShowBattery)) }

    @Test
    fun settingsLibrary() =
        check("settings_library", Spec(scrollTags = settingsLists + SettingsTags.PANEL_LIST, focused = tagged(SettingsTags.option(SettingsOption.RomFolders)))) { SettingsAt(optionsAt(SettingsOption.RomFolders), content = ShotContent.library) }

    @Test
    fun settingsRescanWhileScanning() =
        check("settings_rescan_scanning", Spec(scrollTags = settingsLists + SettingsTags.PANEL_LIST, focused = tagged(SettingsTags.option(SettingsOption.Rescan)))) {
            SettingsAt(optionsAt(SettingsOption.Rescan), content = ShotContent.library.copy(isScanning = true))
        }

    @Test
    fun settingsRescanWalking() =
        check("settings_rescan_walking", Spec(scrollTags = settingsLists + SettingsTags.PANEL_LIST, focused = tagged(SettingsTags.option(SettingsOption.Rescan)))) {
            SettingsAt(optionsAt(SettingsOption.Rescan), content = RescanShots.walking)
        }

    @Test
    fun settingsRescanDone() =
        check("settings_rescan_done", Spec(scrollTags = settingsLists + SettingsTags.PANEL_LIST, focused = tagged(SettingsTags.option(SettingsOption.Rescan)))) {
            SettingsAt(optionsAt(SettingsOption.Rescan), content = RescanShots.mixed)
        }

    @Test
    fun settingsEmulators() =
        check("settings_emulators", Spec(scrollTags = settingsLists + SettingsTags.PANEL_LIST, focused = tagged(SettingsTags.system(0)))) {
            SettingsAt(
                SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0)),
                content = ShotContent.library,
            )
        }

    // Library › System order and Systems; the carried row at the bottom stays whole.
    @Test
    fun settingsSystemOrder() = check("settings_system_order", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.SystemOrder)))) { SystemsShot(SettingsOption.SystemOrder) }

    @Test
    fun settingsSystemsPanel() = systems("settings_systems", 1, setOf("arcade", "psp")) { move(Direction.Down) }

    @Test
    fun settingsSystemsCarried() = systems("settings_systems_carried", SystemsShots.systems.lastIndex) { press(Action.Enter).also { press(Action.NextSystem) } }

    private fun systems(name: String, focused: Int, hidden: Set<String> = emptySet(), presses: ShotPresses.() -> Unit) =
        check(name, Spec(scrollTags = settingsLists + SettingsTags.SYSTEMS_LIST, focused = tagged(SettingsTags.panelItem(focused)), shoot = true)) { SystemsShot(SettingsOption.Systems, true, hidden = hidden, presses = presses) }

    @Test
    fun settingsAbout() =
        check("settings_about", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.option(SettingsOption.Version)))) { SettingsAt(optionsAt(SettingsOption.Version)) }

    @Test
    fun settingsSections() =
        check("settings_sections", Spec(scrollTags = settingsLists, focused = tagged(SettingsTags.section(SettingsSection.Appearance)))) { SettingsAt(SettingsFocus(section = SettingsSection.Appearance)) }

    // The game panel
    @Test
    fun gamePanelMenu() =
        OpenPanel(game, PanelScreen.Menu(facts), focus = 1).let { panel ->
            check("game_menu", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS), focused = focusedRow(panel), shoot = true), panelOver(panel))
        }

    @Test
    fun gamePanelInfo() =
        InfoReadThrough(OpenPanel(game, PanelScreen.Info(facts))).let { info -> check("game_info", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS, GamePanelTags.FACTS_SCROLL), ellipsisUnder = GamePanelTags.FACTS, shoot = true, readThrough = info), info.screen) }

    @Test
    fun gamePanelPlayWith() {
        val risky = EmulatorItem("risky", "Risky Emu", "org.example.risky", isLegalRisk = true)
        val listing = sampleListing(current = Beetle.id, installed = listOf(DuckStation, Beetle), notInstalled = listOf(Epsxe, risky)).copy(systemName = "Super Nintendo")
        val panel = OpenPanel(game, PanelScreen.PlayWith(listing), focus = 2)
        check("game_play_with", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS), focused = focusedRow(panel)), panelOver(panel))
    }

    @Test
    fun gamePanelChooseEmulator() {
        val offers =
            listOf(
                EmulatorOffer("snes9x", "Snes9x EX+", "com.explusalpha.Snes9xPlus"),
                EmulatorOffer("retroarch-snes9x", "RetroArch: Snes9x", "com.retroarch.aarch64"),
                EmulatorOffer("retroarch-bsnes-mercury-accuracy", "RetroArch: bsnes-mercury Accuracy", "com.retroarch.aarch64"),
            )
        val message = launchMessage(LaunchOutcome.EmulatorNotChosen(6, "snes", "Super Nintendo", offers, "snes9x"), sampleListing().copy(systemName = "Super Nintendo"))!!
        val panel = OpenPanel(game, PanelScreen.Message(message))
        check("game_choose_emulator", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS), focused = focusedRow(panel)), panelOver(panel))
    }

    @Test
    fun gamePanelLaunchError() {
        val outcome = LaunchOutcome.FileMissing(6, "Super Metroid.sfc", FileProblem.Gone)
        check("game_error_file_missing", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS)), panelOver(OpenPanel(game, PanelScreen.Message(launchMessage(outcome, null)!!))))
    }

    @Test
    fun gamePanelFixName() {
        val state = FixNameState("super metroid", finder)
        state.showResults("super metroid", finder.find("super metroid"))
        state.onAction(Action.Move(Direction.Down))
        check("game_fix_name", Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS, "fix_name_results")), panelOver(OpenPanel(game, PanelScreen.FixName(state))))
    }

    // Fix box art: the games with their thumbnails, the lines of the phases, the menu's seventh row.
    private fun boxArtCheck(name: String, panel: OpenPanel, focused: (SemanticsNode) -> SemanticsNode?, shoot: Boolean = false, tooTall: ((SemanticsNode) -> SemanticsNode?)? = null) =
        check(name, Spec(GamePanelTags.PANEL, setOf(GamePanelTags.ROWS, FixBoxArtTags.ROWS), focused = focused, shoot = shoot, focusedWhenTooTall = tooTall, slidingText = setOf(FixNameTags.typedText(FixBoxArtTags.FIELD)))) { BoxArtPanelFixtures.Screen(panel) }

    private fun boxArtPanel(downs: Int, change: (FixBoxArtState) -> Unit = {}): OpenPanel =
        BoxArtPanelFixtures.results().also { state -> repeat(downs) { state.onAction(Action.Move(Direction.Down)) }; change(state) }.let(BoxArtPanelFixtures::panel)

    private fun boxArtRow(hit: BoxArtHit) = tagged(GamePanelTags.row(boxArtHitKey(hit)))

    @Test
    fun gamePanelFixBoxArtResults() = boxArtCheck("game_fix_box_art", boxArtPanel(3), boxArtRow(BoxArtPanelFixtures.landscape), shoot = true)

    @Test
    fun gamePanelFixBoxArtOnTheField() = boxArtCheck("game_fix_box_art_field", boxArtPanel(0), tagged(FixBoxArtTags.FIELD))

    @Test
    fun gamePanelFixBoxArtOnNoBoxArt() = boxArtCheck("game_fix_box_art_none", boxArtPanel(BoxArtPanelFixtures.hits.size + 2), tagged(GamePanelTags.row(BOX_ART_NONE_KEY)))

    @Test
    fun gamePanelFixBoxArtDailyLimit() {
        val state = FixBoxArtState("super metroid", "Super Nintendo").also { it.onAction(Action.Move(Direction.Down)); it.searching("super metroid") }
        state.showSearch("super metroid", BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(2_115_360_000_000L)))
        // Search cannot work now, so the cursor rests on the first row that can.
        boxArtCheck("game_fix_box_art_daily_limit", BoxArtPanelFixtures.panel(state), tagged(GamePanelTags.row(BOX_ART_NONE_KEY)))
    }

    // The store line leads the picked game's item. The picked row must be whole at every size; only at Largest in Serif or Mono, in the
    // 248 wide panel, line and row together are taller than the list, and there the line (what the person must read) is what must be whole.
    @Test
    fun gamePanelFixBoxArtWaiting() =
        boxArtCheck("game_fix_box_art_waiting", boxArtPanel(2) { it.storing(); it.waitingForPictures() }, tagged(FixBoxArtTags.fill(boxArtHitKey(BoxArtPanelFixtures.portrait))), tooTall = tagged(FixBoxArtTags.LINE))

    @Test
    fun gamePanelFixBoxArtNoBoxFront() =
        boxArtCheck("game_fix_box_art_no_box_front", boxArtPanel(2) { it.storing(); it.stored(BoxArtStoreOutcome.NoBoxFront, BoxArtPanelFixtures.missing) }, tagged(FixBoxArtTags.fill(boxArtHitKey(BoxArtPanelFixtures.portrait))), tooTall = tagged(FixBoxArtTags.LINE))

    @Test
    fun gamePanelFixBoxArtSaved() = OpenPanel(game, PanelScreen.Message(boxArtStoredContent(BoxArtPanelFixtures.portrait))).let { boxArtCheck("game_fix_box_art_saved", it, focusedRow(it)) }

    /** Seven rows: the cursor on each of the last two, at every size, fully on screen. */
    @Test
    fun gamePanelMenuWithFixBoxArtOnInfo() = OpenPanel(game, PanelScreen.Menu(facts.copy(canFixBoxArt = true)), focus = 6).let { boxArtCheck("game_menu_fix_box_art_info", it, focusedRow(it), shoot = true) }

    @Test
    fun gamePanelMenuWithFixBoxArtOnItsRow() = OpenPanel(game, PanelScreen.Menu(facts.copy(canFixBoxArt = true)), focus = 5).let { boxArtCheck("game_menu_fix_box_art_row", it, focusedRow(it)) }

    // Setup
    @Test
    fun setupFolderEmpty() =
        check("setup_folder_empty", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(SetupTags.control(SetupControl.ChooseFolder)), shoot = true)) { SetupAt(SetupFakes.chooseFolder) }

    @Test
    fun setupFolderCard() =
        check("setup_folder_card", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(SetupTags.control(SetupControl.Continue)))) { SetupAt(SetupFakes.folderChosen) }

    @Test
    fun setupScanning() = check("setup_scan", Spec(scrollTags = setOf(SetupTags.SCROLL))) { SetupAt(SetupFakes.scanning) }

    @Test
    fun setupReady() =
        check("setup_ready", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(SetupTags.control(SetupControl.UsageStatistics)), shoot = true)) {
            SetupAt(SetupFakes.ready.copy(focus = SetupControl.UsageStatistics))
        }

    @Test
    fun setupBoxArtQuestion() =
        check("setup_box_art_question", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(SetupTags.control(SetupControl.UseBoxArt)), shoot = true)) {
            SetupAt(SetupFakes.boxArtQuestion)
        }

    @Test
    fun setupReadyWithBoxArt() =
        check("setup_ready_box_art", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(SetupTags.control(SetupControl.StartPlaying)))) {
            SetupAt(SetupFakes.readyWithBoxArt)
        }

    // Setup's box art step: empty (the cursor on the username, keyboard down), the longest message, signed in.
    private fun boxArt(state: AccountShots.State, focusedTag: String, isAnimated: Boolean = false) =
        check("setup_box_art_${state.name.lowercase()}", Spec(scrollTags = setOf(SetupTags.SCROLL), focused = tagged(focusedTag), isAnimated = isAnimated)) {
            SetupAt(SetupFakes.boxArt(remember { AccountShots.form(state) }))
        }

    @Test fun setupBoxArtEmpty() = boxArt(AccountShots.State.Empty, SignInTags.USERNAME, isAnimated = true)

    @Test fun setupBoxArtDailyLimit() = boxArt(AccountShots.State.DailyLimit, SignInTags.item(SignInItem.SignIn))

    @Test fun setupBoxArtSignedIn() = boxArt(AccountShots.State.SignedIn, SignInTags.item(SignInItem.Continue))

    // Settings › Library's ScreenScraper rows and the sign-in form in the panel.
    private fun accountRows(
        name: String,
        account: AccountStatus,
        option: SettingsOption,
        page: PanelPage? = null,
        focusedTag: String = SettingsTags.option(option),
    ) = check(name, Spec(scrollTags = settingsLists + SettingsTags.ACCOUNT_PANEL, focused = tagged(focusedTag))) {
        val content = AccountShotContent.of(account)
        SettingsAt(AccountShotContent.focusOn(content, option, page), content = content)
    }

    @Test
    fun settingsAccountSignedOut() = accountRows("settings_account_signed_out", AccountStatus.SignedOut, SettingsOption.ScraperAccount)

    @Test
    fun settingsAccountRefused() = accountRows("settings_account_refused", AccountStatus.SignedIn("plumtester", isRefused = true), SettingsOption.ScraperAccount)

    @Test
    fun settingsAccountDailyLimit() =
        accountRows("settings_account_daily_limit", AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = AccountShots.RESUME_AT_MILLIS), SettingsOption.ScraperAccount)

    @Test
    fun settingsAccountNotAvailable() = accountRows("settings_account_not_available", AccountStatus.NotInThisBuild, SettingsOption.ScraperStatus)

    @Test
    fun settingsBoxArtRegion() = accountRows("settings_box_art_region", AccountStatus.SignedIn("plumtester"), SettingsOption.BoxArtRegion)

    @Test
    fun settingsAccountSignOutPanel() =
        accountRows(
            "settings_account_refused_panel",
            AccountStatus.SignedIn("plumtester", isRefused = true),
            SettingsOption.ScraperAccount,
            PanelPage.ScraperAccount,
            focusedTag = SettingsTags.panelItem(0),
        )

    // Box art's row, panel, confirm and systems picker.
    private fun boxArt(
        name: String,
        content: SettingsContent,
        page: PanelPage?,
        focusedTag: String,
        ellipsisUnder: String? = null,
        tune: (SettingsFocus) -> SettingsFocus = { it },
    ) = check(name, Spec(scrollTags = settingsLists + SettingsTags.ACCOUNT_PANEL + SettingsTags.PANEL_LIST, ellipsisUnder = ellipsisUnder, focused = tagged(focusedTag), shoot = page != null)) {
        SettingsAt(tune(AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, page)), content = content)
    }

    @Test
    fun settingsBoxArtRowRunning() =
        boxArt("settings_box_art_row_running", BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running), null, SettingsTags.option(SettingsOption.ScraperAccount))

    @Test
    fun settingsBoxArtPanelRunning() =
        boxArt("settings_box_art_panel_running", BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running).copy(accountLimits = BoxArtShots.limits), PanelPage.ScraperAccount, SettingsTags.panelItem(0))

    @Test
    fun settingsBoxArtPanelSignOutFocused() {
        // With the facts block at its longest: the used-up words.
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.dailyLimit).copy(accountLimits = BoxArtShots.limitsUsedUp)
        val last = accountPageItems(content).lastIndex
        boxArt("settings_box_art_panel_sign_out", content, PanelPage.ScraperAccount, SettingsTags.panelItem(last)) { it.copy(panelIndex = last) }
    }

    @Test
    fun settingsBoxArtPanelEmpty() = boxArt("settings_box_art_panel_empty", BoxArtShots.emptyContent, PanelPage.ScraperAccount, SettingsTags.panelItem(0))

    @Test
    fun settingsBoxArtConfirm() =
        boxArt("settings_box_art_confirm", BoxArtShots.longSystems, PanelPage.ScraperConfirm, SettingsTags.panelItem(BoxArtConfirmItem.Cancel.ordinal)) {
            it.copy(panelIndex = BoxArtConfirmItem.Cancel.ordinal, redownloadSystems = setOf("pce"))
        }

    // The shots' system names are longer than any in the platform data: past three lines a name is cut
    // with an ellipsis by design (docs/conventions/ui.md), so the list may hold one.
    @Test
    fun settingsBoxArtSystems() =
        boxArt("settings_box_art_systems", BoxArtShots.longSystems, PanelPage.ScraperSystems, SettingsTags.panelItem(3), ellipsisUnder = SettingsTags.BOX_ART_SYSTEMS) {
            it.copy(panelIndex = 3, chosenSystems = setOf("nes", "pce"))
        }

    @Test
    fun settingsBoxArtSystemsLastAction() {
        val last = BoxArtShots.longSystems.systems.size + 1
        boxArt("settings_box_art_systems_action", BoxArtShots.longSystems, PanelPage.ScraperSystems, SettingsTags.panelItem(last), ellipsisUnder = SettingsTags.BOX_ART_SYSTEMS) {
            it.copy(panelIndex = last, chosenSystems = setOf("arcade"))
        }
    }

    private fun signInForm(state: AccountShots.State, focusedTag: String, isAnimated: Boolean = false) =
        check("settings_sign_in_${state.name.lowercase()}", Spec(scrollTags = settingsLists + SettingsTags.ACCOUNT_PANEL, focused = tagged(focusedTag), isAnimated = isAnimated)) {
            SignInFormAt(state)
        }

    @Test fun settingsSignInFormDailyLimit() = signInForm(AccountShots.State.DailyLimit, SignInTags.item(SignInItem.SignIn))

    @Test fun settingsSignInFormEmpty() = signInForm(AccountShots.State.Empty, SignInTags.USERNAME, isAnimated = true)

    @Test
    fun setupHomeRolePrompt() = check("home_role_prompt", Spec(HomeRoleTags.PROMPT, setOf(HomeRoleTags.SCROLL), focused = tagged(HomeRoleTags.button(0)))) { HomeRolePromptAt() }
}

private const val ANIMATED_STEP_MS = 400L
