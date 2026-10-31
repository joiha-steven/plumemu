package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.WidthClass

/*
 * The main screen with ScreenScraper results, the way the library gives them: rows
 * with scrape keys and no fixed facts, results and pictures from a [FakeArtSource]. The focus
 * set-ups are the boards' (HomeArtHarness.kt).
 */

/** The board's content as the library gives it, with box art set up. */
internal val CoverArtLibrary: HomeContent by lazy { FakeHomeContent.withArtwork().asLibrary() }

/**
 * Results for the board's games: pictures for all but [noArt], nothing at all for [absent];
 * pictures at [pending] paths never land, at [unreadable] paths their file cannot be read.
 */
internal fun coverArtSource(
    noArt: Set<String> = emptySet(),
    absent: Set<String> = emptySet(),
    pending: Set<String> = emptySet(),
    synopses: Map<String, String> = mapOf(artKey(SUPER_METROID_ID) to SUPER_METROID_SYNOPSIS),
    content: HomeContent = FakeHomeContent.withArtwork(),
    unreadable: Set<String> = emptySet(),
    // As the library: the rating comes with the synopsis, read for the focused game only.
    ratings: Map<String, Int> = mapOf(artKey(SUPER_METROID_ID) to SUPER_METROID_RATING),
): FakeArtSource = FakeArtSource(artFor(content, noArt, absent), synopses, FakeCoverImages(pending, unreadable), ratings)

/** Super Metroid's rating out of 20 in the fakes: 8.5 out of 10. */
internal const val SUPER_METROID_RATING = 17

/** [HomeAt] with [source]'s results, as `MainScreen` provides them. */
@Composable
internal fun HomeWithArt(
    source: HomeArtSource,
    actions: List<Action>,
    content: HomeContent = CoverArtLibrary,
    gridIds: Set<String> = emptySet(),
    onState: (com.plumemu.coreinput.strip.StripState) -> Unit = {},
    detailsOnSecondScreen: Boolean = false,
    charging: Boolean = false,
) {
    ProvideHomeArt(source, content.hasBoxArt) {
        HomeAt(content, actions, gridIds = gridIds, onState = onState, detailsOnSecondScreen = { detailsOnSecondScreen }, charging = { charging })
    }
}

/**
 * The Super Nintendo grid with art (RP5-3 / Nova-4 focus), at [gridSize]: Large by default, the
 * board's landscape grid, so the shots and bounds taken before the grid sizes stay as drawn.
 */
@Composable
internal fun CoverGridBoard(
    source: HomeArtSource,
    gridSize: GridSize = GridSize.Large,
) {
    val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val focused = if (narrow) FINAL_FANTASY_VI else MEGA_MAN_X
    ProvideGridSize(gridSize) { HomeWithArt(source, gridActions(FakeHomeContent.BOARD_SNES_INDEX, focused), gridIds = setOf("snes")) }
}

/** The PSP grid with art (RP5-4 / Nova-5 focus), at [gridSize] (Medium, the board's portrait grid, by default). */
@Composable
internal fun CoverGridPortraitBoard(
    source: HomeArtSource,
    gridSize: GridSize = GridSize.Medium,
) = ProvideGridSize(gridSize) { HomeWithArt(source, gridActions(PSP_INDEX, PERSONA_3_PORTABLE), gridIds = setOf("psp")) }

/** Super Metroid's details (RP5-2-games beside the list; Nova-3-details as the third stop). */
@Composable
internal fun CoverDetailsBoard(
    source: HomeArtSource,
    content: HomeContent = CoverArtLibrary,
) {
    val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val actions = if (narrow) BoardGamesFocused + Action.Move(Direction.Right) else BoardGamesFocused
    HomeWithArt(source, actions, content)
}

/** The board's content with Super Metroid renamed to [title] (a long title on the details). */
internal fun withSuperMetroidTitled(title: String): HomeContent =
    CoverArtLibrary.withEntries(
        CoverArtLibrary.entries.map { entry -> entry.copy(rows = entry.rows.map { if (it.id == SUPER_METROID_ID) it.copy(title = title) else it }) },
    )

/** Remembers [make] for the composition (a source must outlive recompositions). */
@Composable
internal fun rememberSource(make: () -> FakeArtSource): FakeArtSource = remember { make() }

/** The board's Super Metroid row (Super Nintendo, row 6). */
internal val SUPER_METROID_ID: String by lazy { FakeHomeContent.board().entries[FakeHomeContent.BOARD_SNES_INDEX].rows[FakeHomeContent.BOARD_REMEMBERED_ROW].id }

internal const val SUPER_METROID_SYNOPSIS =
    "Samus Aran returns to planet Zebes to recover a stolen Metroid larva, exploring a vast connected world " +
        "and gaining new abilities along the way. Space pirates, Ridley and Mother Brain wait in the depths."

internal const val LONG_DETAILS_TITLE = "Super Metroid: The Complete Expedition Through Zebes and the Wrecked Ship"
internal const val LONG_PUBLISHER = "Nintendo Research & Development 1 with Intelligent Systems"
