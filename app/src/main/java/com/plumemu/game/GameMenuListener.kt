package com.plumemu.game

import androidx.compose.runtime.Composable
import com.plumemu.keyboard.KeyboardChoice
import androidx.compose.runtime.SideEffect
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
): GameMenuController {
    val scope = rememberCoroutineScope()
    val controller = remember(actions, apps, scope) { GameMenuController(actions, scope, apps) }
    SideEffect { controller.keyboardKind = keyboard }
    return controller
}
