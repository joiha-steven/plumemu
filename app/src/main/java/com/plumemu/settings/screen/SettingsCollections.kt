package com.plumemu.settings.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.collections.CollectionOrder
import com.plumemu.coredata.db.CollectionNames
import com.plumemu.game.CollectionNaming
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelCommand
import com.plumemu.game.PanelScreen
import com.plumemu.game.TypingOutcome
import com.plumemu.game.TypingState
import com.plumemu.keyboard.KeyboardChoice

/*
 * Settings › Library › Collections: the own collections in their order (the built-in folders are not listed: they cannot be
 * renamed, deleted or moved). A on one opens its page ([PanelPage.Collection]): Rename (Plumemu's keyboard, as the game menu's
 * New collection…), Move up, Move down, Delete… (the confirm opens on Cancel). Back returns to the same item.
 */

/** An own collection as the list shows it. */
@Immutable
internal data class SettingsCollection(
    val id: Long,
    val name: String,
    val gameCount: Int,
)

/** What Settings asks of the graph for collections. Each call returns at once; [onDone] gets whether it was stored, on the main thread. */
internal interface SettingsCollectionActions {
    /** [name] is trimmed and made unique by the database (a name in use gets " 2"). */
    fun rename(id: Long, name: String, onDone: (Boolean) -> Unit)

    /** Deletes the collection with its memberships; the games stay. */
    fun delete(id: Long, onDone: (Boolean) -> Unit)

    /** Puts the own collections in the order of [ids]. */
    fun reorder(ids: List<Long>, onDone: (Boolean) -> Unit)

    object None : SettingsCollectionActions {
        override fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) = onDone(false)

        override fun delete(id: Long, onDone: (Boolean) -> Unit) = onDone(false)

        override fun reorder(ids: List<Long>, onDone: (Boolean) -> Unit) = onDone(false)
    }
}

/** One item of a collection's page, in order. Move up is missing on the first collection and Move down on the last. */
internal enum class CollectionPageItem { Rename, MoveUp, MoveDown, Delete }

/** The items of [collectionId]'s page in [content]. */
internal fun collectionPageItems(
    content: SettingsContent,
    collectionId: Long?,
): List<CollectionPageItem> {
    val at = content.collections.indexOfFirst { it.id == collectionId }
    return listOfNotNull(
        CollectionPageItem.Rename,
        CollectionPageItem.MoveUp.takeIf { at > 0 },
        CollectionPageItem.MoveDown.takeIf { at >= 0 && at < content.collections.lastIndex },
        CollectionPageItem.Delete,
    )
}

/** The ids in the order that moving [id] by [up] gives; null when it cannot move that way. */
internal fun movedOrder(
    ids: List<Long>,
    id: Long,
    up: Boolean,
): List<Long>? {
    val order = CollectionOrder(ids).pickUp(id)
    return (if (up) order.moveUp() else order.moveDown()).drop().newOrder
}

/** The Rename page open for one collection: the name field, typed with Plumemu's keyboard (or Android's, as Settings › Controls says). */
@Immutable
internal data class OpenCollectionRename(
    val collectionId: Long,
    val typing: OwnNameState,
) {
    val screen: PanelScreen.OwnName get() = PanelScreen.OwnName(typing, CollectionNaming(withGame = false))
}

/** The Rename page's state: its field holds at most [CollectionNames.MAX_LENGTH] characters and refuses a blank name. Main thread only. */
@Stable
internal class SettingsCollectionRename {
    var open: OpenCollectionRename? by mutableStateOf(null)
        private set

    fun start(
        collection: SettingsCollection,
        keyboard: KeyboardChoice,
    ) {
        open = OpenCollectionRename(collection.id, OwnNameState(collection.name, keyboard, CollectionNames.MAX_LENGTH))
    }

    fun close() {
        open = null
    }

    /** What the open field made of [outcomeOf] (an action, or a tap on a key of the tray), as the Fix name… page reads it. */
    inline fun onTyping(outcomeOf: (TypingState) -> TypingOutcome): SettingsNameFix.Step {
        val typing = open?.typing ?: return SettingsNameFix.Step.Leave(null)
        return when (val outcome = outcomeOf(typing)) {
            TypingOutcome.Handled -> SettingsNameFix.Step.Stay
            TypingOutcome.NotHandled -> SettingsNameFix.Step.Leave(null)
            is TypingOutcome.Run -> (outcome.command as? PanelCommand.SaveOwnName)?.let { SettingsNameFix.Step.Leave(it) } ?: SettingsNameFix.Step.Stay
        }
    }
}
