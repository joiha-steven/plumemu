package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what Collections asks of the graph and answers when a test says so. */
internal class FakeCollectionActions : SettingsCollectionActions {
    val renames = mutableListOf<Pair<Long, String>>()
    val deletes = mutableListOf<Long>()
    val orders = mutableListOf<List<Long>>()
    val answers = mutableListOf<(Boolean) -> Unit>()

    override fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) {
        renames += id to name
        answers += onDone
    }

    override fun delete(id: Long, onDone: (Boolean) -> Unit) {
        deletes += id
        answers += onDone
    }

    override fun reorder(ids: List<Long>, onDone: (Boolean) -> Unit) {
        orders += ids
        answers += onDone
    }
}

/**
 * Settings › Library › Collections by gamepad and by touch: the row opens the list of own collections, one opens its page
 * (Rename, Move up, Move down, Delete…), Back returns to the same item, Delete asks first with Cancel focused.
 */
internal class SettingsCollectionsTest : SettingsModelHarness() {
    private val a = SettingsCollection(1, "RPG", 12)
    private val b = SettingsCollection(2, "Couch co-op", 3)
    private val c = SettingsCollection(3, "Road trip", 0)
    private val library = ShotContent.library.copy(collections = listOf(a, b, c))

    private fun onRow(content: SettingsContent = library) {
        model.content = content
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Collections)
    }

    private fun openList(content: SettingsContent = library) {
        onRow(content)
        press(Action.Enter)
    }

    /** The page of the collection at [index] of the list. */
    private fun openPage(index: Int) {
        openList()
        repeat(index) { move(Direction.Down) }
        press(Action.Enter)
    }

    private fun downTo(item: CollectionPageItem) {
        val items = collectionPageItems(model.content, model.focus.collectionId)
        repeat(items.indexOf(item) - model.focus.panelIndex) { move(Direction.Down) }
        assertEquals(item, items[model.focus.panelIndex])
    }

    private fun assertOnList(index: Int) {
        assertEquals(PanelPage.Collections, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(index, model.focus.panelIndex)
        assertNull(model.focus.collectionId)
    }

    @Test
    fun theRowComesAfterEmulators() =
        assertTrue(libraryRowsIn(library).indexOf(SettingsOption.Collections) > libraryRowsIn(library).indexOf(SettingsOption.Emulators))

    @Test
    fun aAndRightOpenTheListOnTheFirstCollection() {
        openList()
        assertOnList(0)
        press(Action.Back)
        move(Direction.Right)
        assertOnList(0)
    }

    @Test
    fun aOnACollectionOpensItsPageOnRename() {
        openPage(1)
        assertEquals(PanelPage.Collection, model.focus.page)
        assertEquals(2L, model.focus.collectionId)
        assertEquals(0, model.focus.panelIndex)
        assertEquals(listOf(CollectionPageItem.Rename, CollectionPageItem.MoveUp, CollectionPageItem.MoveDown, CollectionPageItem.Delete), collectionPageItems(model.content, 2))
    }

    @Test
    fun theFirstCollectionCannotMoveUpAndTheLastCannotMoveDown() {
        assertEquals(listOf(CollectionPageItem.Rename, CollectionPageItem.MoveDown, CollectionPageItem.Delete), collectionPageItems(library, 1))
        assertEquals(listOf(CollectionPageItem.Rename, CollectionPageItem.MoveUp, CollectionPageItem.Delete), collectionPageItems(library, 3))
    }

    @Test
    fun bFromThePageReturnsToTheListOnTheSameCollectionAndBAgainToTheRow() {
        openPage(2)
        press(Action.Back)
        assertOnList(2)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Collections, model.focusedOption)
    }

    @Test
    fun touchTapFocusesAndASecondTapOpensThePage() {
        openList()
        model.onTapPanelItem(1, actions.shown, wide)
        assertOnList(1)
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals(PanelPage.Collection, model.focus.page)
        assertEquals(2L, model.focus.collectionId)
    }

    // Rename

    /** A opens the keyboard on the field, START saves what it holds. */
    private fun saveWithStart() {
        press(Action.Enter)
        press(Action.Menu)
    }

    private fun openRename(index: Int = 0) {
        openPage(index)
        press(Action.Enter)
        assertEquals(PanelPage.CollectionRename, model.focus.page)
    }

    @Test
    fun renameOpensWithTheNameAndStartSavesWhatWasTyped() {
        openRename(1)
        val typing = model.collectionRename.open!!.typing
        assertEquals("Couch co-op", typing.text)
        typing.onTextChange("Couch")
        saveWithStart()
        assertEquals(listOf(2L to "Couch"), actions.collectionsFake.renames)
        assertEquals("back on the page, on Rename", PanelPage.Collection, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
        assertNull(model.collectionRename.open)
    }

    @Test
    fun anEmptyNameIsRefusedAndNothingIsSaved() {
        openRename()
        val typing = model.collectionRename.open!!.typing
        typing.onTextChange("   ")
        saveWithStart()
        assertTrue(typing.isRefused)
        assertEquals(emptyList<Pair<Long, String>>(), actions.collectionsFake.renames)
        assertEquals(PanelPage.CollectionRename, model.focus.page)
    }

    @Test
    fun theFortyFirstCharacterIsRefusedWhileTyping() {
        openRename()
        val typing = model.collectionRename.open!!.typing
        typing.onTextChange("x".repeat(40))
        typing.onTextChange("x".repeat(41))
        assertEquals(40, typing.text.length)
    }

    @Test
    fun bLeavesRenameWithoutSavingAndLandsOnRename() {
        openRename(1)
        model.collectionRename.open!!.typing.onTextChange("Other")
        // B with the keyboard not up leaves the page.
        press(Action.Back)
        assertEquals(emptyList<Pair<Long, String>>(), actions.collectionsFake.renames)
        assertEquals(PanelPage.Collection, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun aFailedRenameIsSaidOnThePanel() {
        openRename()
        model.collectionRename.open!!.typing.onTextChange("New")
        saveWithStart()
        actions.collectionsFake.answers.single()(false)
        assertEquals(SettingsNotice.CollectionFailed, model.work.notice?.second)
    }

    @Test
    fun touchOnRenameThenASecondTapOpensTheKeyboardPage() {
        openPage(0)
        model.onTapPanelItem(0, actions.shown, wide)
        assertEquals(PanelPage.CollectionRename, model.focus.page)
    }

    // Delete

    @Test
    fun deleteAsksFirstWithCancelFocusedAndCancelKeepsIt() {
        openPage(1)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        assertEquals(PanelPage.CollectionDelete, model.focus.page)
        assertEquals("Cancel is focused", SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(emptyList<Long>(), actions.collectionsFake.deletes)
        assertEquals(PanelPage.Collection, model.focus.page)
        assertEquals("back on Delete…", collectionPageItems(model.content, 2).indexOf(CollectionPageItem.Delete), model.focus.panelIndex)
    }

    @Test
    fun bOnTheConfirmKeepsItToo() {
        openPage(1)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(emptyList<Long>(), actions.collectionsFake.deletes)
        assertEquals(PanelPage.Collection, model.focus.page)
    }

    @Test
    fun deleteRemovesItAndTheListLandsOnTheNeighbour() {
        openPage(1)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(listOf(2L), actions.collectionsFake.deletes)
        assertOnList(1)
        // The list refreshes without it: the cursor is on Road trip, the one that took its place.
        model.content = library.copy(collections = listOf(a, c))
        assertOnList(1)
        assertEquals(3L, model.content.collections[model.focus.panelIndex].id)
    }

    @Test
    fun deletingTheLastLandsOnThePreviousAndTheOnlyOneClosesTheRow() {
        openPage(2)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertOnList(1)
        model.content = library.copy(collections = listOf(a, b))
        assertOnList(1)
        press(Action.Back)
        focusOption(SettingsOption.Collections)
        model.content = library.copy(collections = listOf(a))
        press(Action.Enter)
        press(Action.Enter)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        val rows = SettingsSection.Library.rowsIn(model.content)
        val after = rows[rows.indexOf(SettingsOption.Collections) + 1]
        model.content = library.copy(collections = emptyList())
        // The row leaves with the last collection: its panel closes on the row that took its place.
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(after, model.focusedOption)
        assertFalse(SettingsOption.Collections in SettingsSection.Library.rowsIn(model.content))
    }

    @Test
    fun theRowIsShownOnlyWhileThereIsACollection() {
        assertFalse(SettingsOption.Collections in SettingsSection.Library.rowsIn(library.copy(collections = emptyList())))
        assertTrue(SettingsOption.Collections in SettingsSection.Library.rowsIn(library.copy(collections = listOf(a))))
    }

    @Test
    fun theLastCollectionDeletedElsewhereWhileItsPageIsOpenClosesThePanel() {
        onRow(library.copy(collections = listOf(a)))
        press(Action.Enter)
        press(Action.Enter)
        downTo(CollectionPageItem.Rename)
        press(Action.Enter)
        assertEquals(PanelPage.CollectionRename, model.focus.page)
        model.content = library.copy(collections = emptyList())
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertNull("Rename's page closed", model.collectionRename.open)
    }

    @Test
    fun touchOnDeleteThenOnItsButtonsNeedsTwoTapsEach() {
        openPage(1)
        val delete = collectionPageItems(model.content, 2).indexOf(CollectionPageItem.Delete)
        repeat(2) { model.onTapPanelItem(delete, actions.shown, wide) }
        assertEquals(PanelPage.CollectionDelete, model.focus.page)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.shown, wide)
        assertEquals("a first tap only focuses", emptyList<Long>(), actions.collectionsFake.deletes)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, actions.shown, wide)
        assertEquals(listOf(2L), actions.collectionsFake.deletes)
    }

    @Test
    fun aFailedDeleteIsSaidOnThePanel() {
        openPage(0)
        downTo(CollectionPageItem.Delete)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        actions.collectionsFake.answers.single()(false)
        assertEquals(SettingsNotice.CollectionFailed, model.work.notice?.second)
    }

    // Move

    @Test
    fun moveDownAsksForTheNewOrderAndTheCursorStaysOnMoveDown() {
        openPage(0)
        downTo(CollectionPageItem.MoveDown)
        press(Action.Enter)
        assertEquals(listOf(listOf(2L, 1L, 3L)), actions.collectionsFake.orders)
        model.content = library.copy(collections = listOf(b, a, c))
        assertEquals(PanelPage.Collection, model.focus.page)
        assertEquals(CollectionPageItem.MoveDown, collectionPageItems(model.content, 1)[model.focus.panelIndex])
    }

    @Test
    fun movingUpToTheFirstPlaceLeavesTheCursorOnMoveDown() {
        openPage(1)
        downTo(CollectionPageItem.MoveUp)
        press(Action.Enter)
        assertEquals(listOf(listOf(2L, 1L, 3L)), actions.collectionsFake.orders)
        model.content = library.copy(collections = listOf(b, a, c))
        assertEquals(CollectionPageItem.MoveDown, collectionPageItems(model.content, 2)[model.focus.panelIndex])
    }

    @Test
    fun movingDownToTheLastPlaceLeavesTheCursorOnMoveUp() {
        openPage(1)
        downTo(CollectionPageItem.MoveDown)
        press(Action.Enter)
        model.content = library.copy(collections = listOf(a, c, b))
        assertEquals(CollectionPageItem.MoveUp, collectionPageItems(model.content, 2)[model.focus.panelIndex])
    }

    @Test
    fun touchOnMoveDownMovesAfterASecondTap() {
        openPage(0)
        val at = collectionPageItems(model.content, 1).indexOf(CollectionPageItem.MoveDown)
        repeat(2) { model.onTapPanelItem(at, actions.shown, wide) }
        assertEquals(listOf(listOf(2L, 1L, 3L)), actions.collectionsFake.orders)
    }

    @Test
    fun twoQuickMovesBeforeTheListIsReadAgainMoveTwoPlaces() {
        openPage(0)
        downTo(CollectionPageItem.MoveDown)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(listOf(listOf(2L, 1L, 3L), listOf(2L, 3L, 1L)), actions.collectionsFake.orders)
    }

    @Test
    fun aMoveAfterAFailedOneStartsFromTheStoredOrder() {
        openPage(0)
        downTo(CollectionPageItem.MoveDown)
        press(Action.Enter)
        actions.collectionsFake.answers.single()(false)
        press(Action.Enter)
        assertEquals(listOf(listOf(2L, 1L, 3L), listOf(2L, 1L, 3L)), actions.collectionsFake.orders)
    }

    @Test
    fun aFailedMoveIsSaidOnThePanel() {
        openPage(0)
        downTo(CollectionPageItem.MoveDown)
        press(Action.Enter)
        actions.collectionsFake.answers.single()(false)
        assertEquals(SettingsNotice.CollectionFailed, model.work.notice?.second)
    }

    // Changes from outside

    @Test
    fun aCollectionDeletedElsewhereSendsItsPageBackToTheList() {
        openPage(1)
        model.content = library.copy(collections = listOf(a, c))
        assertOnList(1)
        assertFalse(model.collectionRename.open != null)
    }

    @Test
    fun aCollectionMadeBeforeTheCursorKeepsTheCursorOnTheSameCollectionInTheList() {
        openList()
        move(Direction.Down)
        model.content = library.copy(collections = listOf(SettingsCollection(9, "New", 0)) + library.collections)
        assertOnList(2)
        assertEquals(2L, model.content.collections[model.focus.panelIndex].id)
    }
}
