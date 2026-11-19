package com.plumemu.game

import com.plumemu.R
import com.plumemu.collections.PickerEffect
import com.plumemu.collections.PickerRow
import com.plumemu.collections.PickerState
import com.plumemu.coredata.db.CollectionNames
import com.plumemu.graph.GraphCollections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

/**
 * The game menu's collections, kept out of [GameMenuController] (at the file limit): the picker, the name keyboard's Save and
 * Remove from "<name>". Each write is kept like Favorite's: leaving the panel does not lose it.
 */
internal class CollectionMenuWork(
    private val menu: GameMenuController,
    private val collections: GraphCollections,
    private val log: (String) -> Unit,
) {
    /** The picker's rows and ticks for [game], read when its menu opens; null when it could not be read (the entry is then left out). */
    suspend fun read(game: GameRef): PickerState? = game.key.toLongOrNull()?.let { id -> readLogged(log, "collections", game) { collections.pickerFor(id).first() } }

    /** The keyboard for a collection's name: holding [game] (from the picker, [parent]), or empty (the folders page). */
    fun nameKeyboard(game: GameRef, kind: com.plumemu.keyboard.KeyboardChoice, withGame: Boolean, parent: OpenPanel?): OpenPanel =
        OpenPanel(game, PanelScreen.OwnName(OwnNameState("", kind, CollectionNames.MAX_LENGTH), CollectionNaming(withGame)), parent = parent)

    /** Runs a collection command of the open panel; the row's write job, or null when it started none. */
    fun run(open: OpenPanel, command: PanelCommand): Job? =
        when (command) {
            PanelCommand.OpenCollectionPicker -> null.also { (open.screen as? PanelScreen.Menu)?.facts?.collections?.let { menu.panel = OpenPanel(open.game, PanelScreen.CollectionPicker(it), parent = open) } }
            is PanelCommand.ToggleCollection -> toggle(open, command.collectionId)
            PanelCommand.OpenNewCollection -> null.also { menu.panel = nameKeyboard(open.game, menu.keyboardKind, withGame = true, parent = open) }
            PanelCommand.RemoveFromCollection -> remove(open)
            else -> null
        }

    /**
     * A row of the picker: ticks or unticks at once and writes; the picker stays open. A write that fails puts the tick back
     * and says so over the picker (B returns to it).
     */
    private fun toggle(open: OpenPanel, collectionId: Long): Job? {
        val state = (open.screen as? PanelScreen.CollectionPicker)?.state ?: return null
        val gameId = open.game.key.toLongOrNull() ?: return null
        val row = state.rows.filterIsInstance<PickerRow.Collection>().firstOrNull { it.id == collectionId } ?: return null
        val choice = state.onChoose(row)
        if (choice.effect == PickerEffect.None) return null
        val shown = open.withPicker(choice.state)
        menu.panel = shown
        return menu.keptWrite {
            val stored =
                try {
                    collections.apply(choice.effect, gameId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("collection: game ${open.game.key} could not be saved (${e.javaClass.simpleName})")
                    false
                }
            if (!stored) {
                log("collection: game ${open.game.key} could not be added or removed")
                // Whatever is in front now shows the failure over it, and the ticks and the menu's value are read again from the data.
                val fresh = freshState(gameId)
                val front = menu.panel
                menu.panel = OpenPanel(open.game, PanelScreen.Message(saveFailedMessage()), parent = if (fresh != null) front?.withData(fresh) else front)
            }
        }
    }

    private suspend fun freshState(gameId: Long): PickerState? =
        try {
            collections.pickerFor(gameId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("collection: the picker could not be read again (${e.javaClass.simpleName})")
            null
        }

    /**
     * The keyboard's Save on a collection name. From the picker the collection holds the game and the picker returns with it
     * ticked; from the folders page it is empty and the panel closes (the folders list follows the database).
     */
    fun create(open: OpenPanel, naming: CollectionNaming, name: String): Job =
        menu.keptWrite {
            val game = open.game
            val picker = open.parent
            val gameId = game.key.toLongOrNull()
            var state: PickerState? = null
            var createdId = 0L
            val stored =
                menu.write("new collection", game, open, closeAfter = !naming.withGame) {
                    val trimmed = name.trim()
                    if (naming.withGame) {
                        val id = checkNotNull(gameId) { "no game" }
                        createdId = checkNotNull(collections.createWith(trimmed, id)) { "the game is gone" }
                        state = collections.pickerFor(id).first()
                    } else {
                        collections.create(trimmed)
                    }
                }
            val fresh = state
            if (!stored || fresh == null || picker == null) return@keptWrite
            val back = picker.withPicker(fresh)
            val focus = back.content.focusableRows.indexOfFirst { it.key == COLLECTION_KEY_PREFIX + createdId }.coerceAtLeast(0)
            // Still on the keyboard: the picker, ticked at the new row. Left meanwhile (B, another panel): what is in front is read again from the data.
            if (menu.panel === open) menu.panel = back.copy(focus = focus) else menu.panel?.let { menu.panel = it.withData(fresh) }
        }

    /** Remove from "<name>": once asked, finished; Undo is offered for [RecentUndo.SHOWN_MS] as Remove from Recent offers it. */
    private fun remove(open: OpenPanel): Job? {
        val own = (open.screen as? PanelScreen.Menu)?.facts?.ownCollection ?: return null
        val game = open.game
        val gameId = game.key.toLongOrNull() ?: return null
        return menu.keptWrite {
            val removed = menu.write("remove from collection", game, open) { collections.apply(PickerEffect.Remove(own.id), gameId) }
            if (removed && menu.panel == null) {
                menu.undo.offer(game, R.string.collections_notice_removed) { back ->
                    menu.keptWrite { menu.write("restore to collection", back, null) { check(collections.apply(PickerEffect.Add(own.id), gameId)) { "the collection or the game is gone" } } }
                }
            }
        }
    }
}

/** [this] and the panels behind it with [state] where they show it (the menu's value, the picker's ticks). */
internal fun OpenPanel.withData(state: PickerState): OpenPanel {
    val behind = parent?.withData(state)
    val shown =
        when (val s = screen) {
            is PanelScreen.Menu -> if (s.facts.collections != null) s.copy(facts = s.facts.copy(collections = state)) else s
            is PanelScreen.CollectionPicker -> PanelScreen.CollectionPicker(state)
            else -> s
        }
    return if (shown == screen && behind === parent) this else copy(screen = shown, parent = behind)
}

/** [this] showing [state] as the picker; the menu behind it shows the new value too, so B returns to the menu as it is now. */
internal fun OpenPanel.withPicker(state: PickerState): OpenPanel = copy(screen = PanelScreen.CollectionPicker(state)).withData(state)
