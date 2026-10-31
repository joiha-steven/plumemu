package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screen with Settings' switches off: Show button hints (the strip takes the
 * bar's place), Clock and Show battery (the top bar keeps its place line), Show Android apps (no
 * Apps entry; the systems move up).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeBarsScreenshotTest {
    @Test
    fun hintsOff() = ReferenceShots.capture("home_hints_off") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, showHints = false) }

    @Test
    fun clockAndBatteryOff() =
        ReferenceShots.capture("home_clock_battery_off") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, clock = { null }, battery = { null }) }

    /** Push right to play on: the hint bar adds "→ Twice to play" beside "A Play" (a list). */
    @Test
    fun pushRightToPlayHint() =
        ReferenceShots.capture("home_push_right") { HomeAt(FakeHomeContent.board(), BoardGamesFocused, pushRightToPlay = true) }

    /** After the first Right the hint says "→ Again to play". */
    @Test
    fun pushRightToPlayArmedHint() =
        ReferenceShots.capture("home_push_right_armed") {
            HomeAt(FakeHomeContent.board(), BoardGamesFocused + Action.Move(com.plumemu.coreinput.Direction.Right), pushRightToPlay = true)
        }

    @Test
    fun appsOff() = ReferenceShots.capture("home_apps_off") { HomeAt(withoutApps(FakeHomeContent.board()), boardWithoutApps) }

    /** Super Nintendo focused as on the board, one row higher without Apps. */
    private val boardWithoutApps =
        listOf(
            Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX - 1),
            Action.Enter,
            Action.TapItem(Column.Games, FakeHomeContent.BOARD_REMEMBERED_ROW),
            Action.Back,
        )

    private fun withoutApps(board: HomeContent): HomeContent =
        HomeContent.of(
            recent = board.entries[0].rows,
            favorites = board.entries[1].rows,
            apps = emptyList(),
            systems = board.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), it.rows, it.coverShape) },
            hasBoxArt = board.hasBoxArt,
            showApps = false,
        )
}
