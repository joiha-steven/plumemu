package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import android.view.KeyEvent
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.OpenPanel
import com.plumemu.home.search.SearchState

/** The board's clock and battery, so shots and bounds do not depend on the test machine. */
internal const val BOARD_CLOCK = "21:40"
internal const val BOARD_BATTERY = "82%"

/** The board's systems screen: Super Nintendo focused, Super Metroid remembered. */
internal val BoardSystemsFocused: List<Action> =
    listOf(
        Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX),
        Action.Enter,
        Action.TapItem(Column.Games, FakeHomeContent.BOARD_REMEMBERED_ROW),
        Action.Back,
    )

/** The board's games screen: Super Nintendo current, Super Metroid focused. */
internal val BoardGamesFocused: List<Action> = BoardSystemsFocused.dropLast(1)

/**
 * The main screen's layout with its own strip state, brought to a focus by [actions] before the
 * first frame. With [isMotionOn] false every move jumps, as with animations off.
 * [detailsOnSecondScreen] is read here once, as `HomeScreen` reads the second screen's window state.
 */
@Composable
internal fun HomeAt(
    content: HomeContent,
    actions: List<Action> = emptyList(),
    isMotionOn: Boolean = false,
    onAction: (Action) -> Unit = {},
    onState: (StripState) -> Unit = {},
    clock: () -> String? = { BOARD_CLOCK },
    gridIds: Set<String> = emptySet(),
    panel: OpenPanel? = null,
    battery: () -> String? = { BOARD_BATTERY },
    showHints: Boolean = true,
    search: SearchState? = null,
    detailsOnSecondScreen: () -> Boolean = { false },
    wifi: () -> Boolean = { false },
    charging: () -> Boolean = { false },
    pushRightToPlay: Boolean = false,
    showSystemInfo: Boolean = true,
) {
    val listOnly = detailsOnSecondScreen()
    val state = rememberHomeStripState(content, gridIds, isMotionOn, listOnly)
    // Applied once, synchronously, before the first frame. A dispatch that changes column starts
    // a slide when motion is on, so with [isMotionOn] pass only moves within the system column.
    // Not observed: reads inside the dispatches would otherwise subscribe this harness to the
    // focus, and every later move would recompose it (a test artefact, not the screen's cost).
    remember(state) {
        state.pushRightPlays = pushRightToPlay
        Snapshot.withoutReadObservation { actions.forEach { state.dispatch(it) } }
        actions.size
    }
    onState(state)
    // As HomeScreen: one battery reading for the screen, which the picking line under the details and in Info reads.
    CompositionLocalProvider(LocalBatteryReading provides rememberBatteryReading()) {
        HomeLayout(
            content,
            state,
            onAction,
            clock = clock,
            battery = battery,
            wifi = wifi,
            charging = charging,
            gridIds = gridIds,
            panel = panel,
            showHints = showHints,
            pushRightToPlay = pushRightToPlay,
            showSystemInfo = showSystemInfo,
            search = search,
            detailsOnSecondScreen = listOnly,
        )
    }
}

/**
 * A gamepad button pressed and released through the router's key door, as MainActivity hands it
 * keys: what it sends depends on the router's state (B erases while Search's keyboard is up).
 */
internal fun InputRouter.pressButton(keyCode: Int) {
    onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}
