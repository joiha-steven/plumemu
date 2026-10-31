package com.plumemu.game

/** A write the main screen made for the person (a grid size) failed: the save message over the list. Ignored while a panel is open. */
internal fun GameMenuController.showSaveFailed(game: GameRef) {
    if (panel == null) panel = OpenPanel(game, PanelScreen.Message(saveFailedMessage()))
}

/** A message the screen has for [game] (an emulator closed at once): shown over the list; false while a panel is open (the caller waits and asks again). */
internal fun GameMenuController.showMessage(game: GameRef, content: PanelContent): Boolean {
    if (panel != null) return false
    panel = OpenPanel(game, PanelScreen.Message(content))
    return true
}
