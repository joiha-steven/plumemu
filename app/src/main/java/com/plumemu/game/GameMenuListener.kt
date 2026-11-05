package com.plumemu.game

import androidx.compose.runtime.Composable
import com.plumemu.keyboard.KeyboardChoice
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

/** Hears favorites as they are asked for, before the library answers (the main screen's [com.plumemu.home.FavoriteMarks]). */
internal interface FavoriteListener {
    fun requested(
        game: GameRef,
        isFavorite: Boolean,
    )

    fun failed(
        game: GameRef,
        isFavorite: Boolean,
    )

    /** [game] was hidden from its menu: it leaves every list, a pending favorite with it. */
    fun hidden(game: GameRef)
}

/**
 * The controller for a screen; its work ends when the screen leaves the composition. [keyboard] is
 * read when a panel with a field opens: a panel already open keeps the keyboard it opened with.
 */
@Composable
internal fun rememberGameMenuController(
    actions: GameActions,
    apps: AppActions = AppActions.None,
    keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    sorts: SortActions = SortActions.None,
): GameMenuController {
    val scope = rememberCoroutineScope()
    val controller = remember(actions, apps, sorts, scope) { GameMenuController(actions, scope, apps, sorts) }
    SideEffect { controller.keyboardKind = keyboard }
    // Back from the store (or a package change meanwhile): a panel about emulators shows what is installed now.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.emulatorsMayHaveChanged() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(actions, controller) { actions.emulatorChanges.collect { controller.emulatorsMayHaveChanged() } }
    return controller
}
