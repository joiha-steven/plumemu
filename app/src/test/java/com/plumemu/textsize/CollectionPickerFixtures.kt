package com.plumemu.textsize

import com.plumemu.collections.PickerCollection
import com.plumemu.collections.PickerState
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen

/** The game menu's Add to collection… picker for a game with a very long title: its one-line subtitle ends in an ellipsis at every text size. */
internal val longPickerPanel =
    OpenPanel(
        GameRef("snes-6", "The Legend of Zelda: A Link to the Past and Four Swords - Special Collector's Edition"),
        PanelScreen.CollectionPicker(PickerState(listOf(PickerCollection(7, "RPG"), PickerCollection(8, "Couch co-op")), holding = setOf(7L))),
        focus = 1,
    )
