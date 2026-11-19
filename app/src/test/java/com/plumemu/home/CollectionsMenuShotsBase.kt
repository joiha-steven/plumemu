package com.plumemu.home

import com.plumemu.collections.PickerCollection
import com.plumemu.collections.PickerState
import com.plumemu.coreinput.Action
import com.plumemu.game.CollectionNaming
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnCollectionRef
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.gameMenuEntries
import com.plumemu.game.sampleFacts

/** The game menu's collection panels as data: the menu, the picker, the name keyboard and the menu inside an own collection. */
internal object CollectionsMenuShotsBase {
    private val game = GameRef("snes-6", "Super Metroid")

    /** Inside the own collection RPG (its first game), for Remove from <name>. */
    private val rpgGame = GameRef("s0", "Chrono Trigger")
    private val picker =
        PickerState(
            listOf(PickerCollection(7, "RPG"), PickerCollection(8, "Couch co-op"), PickerCollection(9, "Road trip with a very long name that cannot fit")),
            holding = setOf(7L, 8L),
        )

    private fun menuAt(entry: GameMenuEntryId, own: OwnCollectionRef? = null, game: GameRef = this.game): OpenPanel {
        val facts = sampleFacts(title = game.title).copy(collections = picker, ownCollection = own)
        val focus = gameMenuEntries(facts).filter { it.isEnabled }.indexOfFirst { it.key == entry.name }
        return OpenPanel(game, PanelScreen.Menu(facts), focus = focus)
    }

    fun menu(): OpenPanel = menuAt(GameMenuEntryId.AddToCollection)

    fun menuRemove(): OpenPanel = menuAt(GameMenuEntryId.RemoveFromCollection, OwnCollectionRef(7, "RPG"), rpgGame)

    fun picker(): OpenPanel = OpenPanel(game, PanelScreen.CollectionPicker(picker), focus = 1, parent = menu())

    fun name(): OpenPanel {
        val typing = OwnNameState("", maxLength = 40).apply { onAction(Action.Enter) }
        return OpenPanel(game, PanelScreen.OwnName(typing, CollectionNaming(withGame = true)), parent = picker())
    }
}
