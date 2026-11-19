package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.game.PanelCommand
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.game.onKeyboardEvent
import com.plumemu.sound.Outcome

/**
 * What the panel column does on Settings › Library › Collections: the list, a collection's page, Rename's keyboard and the
 * Delete confirm (`SettingsCollectionsPanels.kt`). [SettingsColorModel] adds the other panels on top. A page works on the
 * collection in [SettingsFocus.collectionId], by id: the list changes while a page is open.
 */
internal abstract class SettingsCollectionsModel(
    initialFocus: SettingsFocus,
    work: SettingsWork,
    private val collectionActions: SettingsCollectionActions,
) : SettingsFolderModel(initialFocus, work) {
    /** The Rename page open over a collection's page. */
    val collectionRename = SettingsCollectionRename()

    /** A or a tap on the list's collection, on the page's item, or on the Delete confirm's button. */
    protected fun onCollectionsEnter(
        page: PanelPage,
        index: Int,
    ) {
        when (page) {
            PanelPage.Collections -> content.collections.getOrNull(index)?.let { moveTo(focus.copy(page = PanelPage.Collection, panelIndex = 0, collectionId = it.id)) }
            PanelPage.Collection -> onCollectionItem(collectionPageItems(content, focus.collectionId).getOrNull(index))
            else -> onDeleteConfirm(index)
        }
    }

    private fun onCollectionItem(item: CollectionPageItem?) {
        val collection = content.collections.firstOrNull { it.id == focus.collectionId } ?: return backToCollections()
        when (item ?: return) {
            CollectionPageItem.Rename -> {
                collectionRename.start(collection, keyboardChoice)
                moveTo(focus.copy(page = PanelPage.CollectionRename, panelIndex = 0))
            }
            CollectionPageItem.MoveUp -> move(collection, up = true)
            CollectionPageItem.MoveDown -> move(collection, up = false)
            // Asks first; the confirm opens on Cancel.
            CollectionPageItem.Delete -> moveTo(focus.copy(page = PanelPage.CollectionDelete, panelIndex = SettingsModel.CONFIRM_CANCEL))
        }
    }

    /** The order the last Move asked for, until the list read back holds it: a second Move before then starts from it, not from the old list. */
    private var askedOrder: List<Long>? = null

    private fun move(
        collection: SettingsCollection,
        up: Boolean,
    ) {
        val shown = content.collections.map { it.id }
        val from = askedOrder?.takeIf { it != shown && it.toSet() == shown.toSet() } ?: shown
        val order = movedOrder(from, collection.id, up) ?: return feel.report(Outcome.Nothing)
        askedOrder = order
        collectionActions.reorder(order) { stored ->
            if (!stored) {
                if (askedOrder == order) askedOrder = null
                work.say(SettingsOption.Collections, SettingsNotice.CollectionFailed)
            }
        }
        feel.report(Outcome.Activated)
    }

    /** Delete asked for the collection the confirm named (by id); afterwards the list, on the collection that took its place. */
    private fun onDeleteConfirm(index: Int) {
        val id = focus.collectionId
        val at = content.collections.indexOfFirst { it.id == id }
        if (index != SettingsModel.CONFIRM_RESET || at < 0) return backToCollectionPage(CollectionPageItem.Delete)
        collectionActions.delete(id!!) { stored -> if (!stored) work.say(SettingsOption.Collections, SettingsNotice.CollectionFailed) }
        feel.report(Outcome.Activated)
        moveTo(focus.copy(page = PanelPage.Collections, panelIndex = at.coerceAtMost(content.collections.size - 2).coerceAtLeast(0), collectionId = null))
    }

    /** Rename's field takes every action while its page is open; Back and a saved name return to the page on Rename. */
    protected fun onCollectionRenameAction(action: Action) = onRenameStep { collectionRename.onTyping { it.onAction(action) } }

    /** A tap on a key of Plumemu's tray over Rename's field: as the same key pressed with A. */
    protected fun onCollectionRenameKey(event: KeyboardEvent) = onRenameStep { collectionRename.onTyping { it.onKeyboardEvent(event) } }

    private inline fun onRenameStep(stepOf: () -> SettingsNameFix.Step) {
        val typing = collectionRename.open?.typing
        val cursor = typing?.cursor
        val key = typing?.fieldKeys?.keys?.focus
        val step = stepOf() as? SettingsNameFix.Step.Leave
        if (step == null) {
            if (typing?.cursor != cursor || typing?.fieldKeys?.keys?.focus != key) feel.report(Outcome.Moved)
            return
        }
        val id = collectionRename.open?.collectionId
        val name = (step.command as? PanelCommand.SaveOwnName)?.name
        collectionRename.close()
        if (id != null && name != null) {
            collectionActions.rename(id, name) { stored -> if (!stored) work.say(SettingsOption.Collections, SettingsNotice.CollectionFailed) }
            feel.report(Outcome.Activated)
        }
        backToCollectionPage(CollectionPageItem.Rename)
    }

    /** Back from a collection's page, the Rename page or the Delete confirm. */
    protected fun onCollectionBack(page: PanelPage) {
        when (page) {
            PanelPage.Collection -> backToCollections()
            PanelPage.CollectionRename -> {
                collectionRename.close()
                backToCollectionPage(CollectionPageItem.Rename)
            }
            else -> backToCollectionPage(CollectionPageItem.Delete)
        }
    }

    private fun backToCollections() =
        moveTo(focus.copy(page = PanelPage.Collections, panelIndex = content.collections.indexOfFirst { it.id == focus.collectionId }.coerceAtLeast(0), collectionId = null))

    private fun backToCollectionPage(item: CollectionPageItem) {
        if (content.collections.none { it.id == focus.collectionId }) return backToCollections()
        moveTo(focus.copy(page = PanelPage.Collection, panelIndex = collectionPageItems(content, focus.collectionId).indexOf(item).coerceAtLeast(0)))
    }

    /**
     * Where the focus goes when the collections change under an open panel: the list and a page keep the same collection (by id) and the
     * same item; a collection that left (deleted from elsewhere) sends its page back to the list at its place. Null: nothing to move.
     */
    protected fun collectionFocusKept(
        focus: SettingsFocus,
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        val at = focus.panelIndex
        if (focus.page == PanelPage.Collections) {
            val kept = before.collections.getOrNull(at)?.let { held -> after.collections.indexOfFirst { it.id == held.id } }?.takeIf { it >= 0 }
            return focus.copy(panelIndex = kept ?: at.coerceAtMost(after.collections.lastIndex).coerceAtLeast(0))
        }
        if (after.collections.none { it.id == focus.collectionId }) {
            collectionRename.close()
            val place = before.collections.indexOfFirst { it.id == focus.collectionId }.coerceAtLeast(0)
            return focus.copy(page = PanelPage.Collections, panelIndex = place.coerceAtMost(after.collections.lastIndex).coerceAtLeast(0), collectionId = null)
        }
        if (focus.page != PanelPage.Collection) return null
        // The item that was under the cursor stays under it; a Move that ran out of room (the first or last place) leaves for the other way.
        val now = collectionPageItems(after, focus.collectionId)
        val held = collectionPageItems(before, focus.collectionId).getOrNull(at)
        val next =
            when {
                held != null && held in now -> now.indexOf(held)
                held == CollectionPageItem.MoveUp -> now.indexOf(CollectionPageItem.MoveDown)
                held == CollectionPageItem.MoveDown -> now.indexOf(CollectionPageItem.MoveUp)
                else -> at
            }.coerceIn(0, now.lastIndex)
        return if (next == at) null else focus.copy(panelIndex = next)
    }
}
