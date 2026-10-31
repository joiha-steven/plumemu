package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.WidthClass

/*
 * Focus set-ups for the box-art screens, from the boards:
 * RP5-2-games and Nova-3-details (Super Metroid), RP5-3 / Nova-4 (Mega Man X / Final Fantasy VI,
 * row 2 of the Super Nintendo grid), RP5-4 / Nova-5 (Persona 3 Portable, row 2 of the PSP grid).
 */

/** Where PSP sits in the system column (Recent, Favorites, Apps, then the board's order). */
internal const val PSP_INDEX = 6

/** The board's details: games focused with Super Metroid; on narrow screens the details stop. */
@Composable
internal fun ArtDetailsBoard() {
    val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val actions = if (narrow) BoardGamesFocused + Action.Move(Direction.Right) else BoardGamesFocused
    HomeAt(FakeHomeContent.withArtwork(), actions)
}

/** The board's landscape grid: Super Nintendo, the focused cover on row 2, at the Large grid size (the board's). */
@Composable
internal fun ArtGridLandscapeBoard() {
    val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val focused = if (narrow) FINAL_FANTASY_VI else MEGA_MAN_X
    ProvideGridSize(GridSize.Large) {
        HomeAt(FakeHomeContent.withArtwork(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, focused), gridIds = setOf("snes"))
    }
}

/** The board's portrait grid: PSP with Persona 3 Portable focused (row 2 at both sizes). */
@Composable
internal fun ArtGridPortraitBoard() {
    HomeAt(FakeHomeContent.withArtwork(), gridActions(PSP_INDEX, PERSONA_3_PORTABLE), gridIds = setOf("psp"))
}

/** Where Nintendo DS sits in the system column. */
internal const val NDS_INDEX = 7

/** The DS's measured US box front (platform data): a square cover. */
internal val NDS_SHAPE = CoverShape(1.12f)

/** The game focused in the square grid: row 2 at both sizes (4 columns at 768, 3 at 576). */
internal const val SQUARE_GRID_FOCUS = 5

/**
 * A square grid (not on the board): the board's content with the DS at its measured
 * shape, as the library gives it ([com.plumemu.content.SystemCoverShapes]); the fakes keep the
 * board's two shapes so the board's shots stay as drawn.
 */
@Composable
internal fun ArtGridSquareBoard() {
    val content =
        remember {
            FakeHomeContent.withArtwork().let { board ->
                board.withEntries(board.entries.map { if (it.id == "nds") it.copy(coverShape = NDS_SHAPE) else it })
            }
        }
    HomeAt(content, gridActions(NDS_INDEX, SQUARE_GRID_FOCUS), gridIds = setOf("nds"))
}

/** Focus system [systemIndex], open its games and focus game [index]. */
internal fun gridActions(
    systemIndex: Int,
    index: Int,
): List<Action> = listOf(Action.TapItem(Column.Systems, systemIndex), Action.Enter, Action.TapItem(Column.Games, index))

internal const val MEGA_MAN_X = 4
internal const val FINAL_FANTASY_VI = 3
internal const val PERSONA_3_PORTABLE = 7
