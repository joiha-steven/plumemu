package com.plumemu.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.collections.PickerEffect
import com.plumemu.collections.PickerRow
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import kotlinx.coroutines.CompletableDeferred
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.GraphCollections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The game menu's collections, through the controller on a real in-memory library: the entry and its value, the picker
 * (ticks, a failed write), New collection… from the picker and from the folders page, Remove from "<name>" with Undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CollectionMenuTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    /** Lets a test hold a write in flight: [applyGate] answers the next apply, [createGate] holds the next create. */
    private class Gated(database: PlumDatabase) : GraphCollections(database, { 1_000L }) {
        var applyGate: CompletableDeferred<Boolean>? = null
        var createGate: CompletableDeferred<Unit>? = null

        override suspend fun apply(effect: PickerEffect, gameId: Long): Boolean = applyGate?.await() ?: super.apply(effect, gameId)

        override suspend fun createWith(name: String, gameId: Long): Long? {
            createGate?.await()
            return super.createWith(name, gameId)
        }
    }

    private val graph = Gated(db)

    @After
    fun tearDown() = db.close()

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    // The tests never suspend on a real thread: virtual time (the Undo timer) moves only when a test advances it.
    private fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    private fun create(name: String): Long = blocking { db.collectionDao().create(name, 1) }

    private fun add(collectionId: Long, game: GameRef) = blocking { graph.apply(com.plumemu.collections.PickerEffect.Add(collectionId), game.key.toLong()) }

    private fun game(): GameRef = blocking {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        val row = GameEntity(
            folderId = folder, documentId = "a.sfc", parentDocumentId = "root", relativePath = "a.sfc", fileName = "a.sfc", sizeBytes = 1,
            modifiedAt = 1, title = "A", sortName = "a", regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
        )
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "snes")), listOf(row)))
        GameRef(db.scanDao().gamesInFolder(folder).single().id.toString(), "A")
    }

    private fun TestScope.menu(wired: Boolean = true) = GameMenuController(FakeGameActions(), this, collections = if (wired) graph else null) {}

    private fun GameMenuController.open(game: GameRef, own: OwnCollectionRef? = null) {
        openMenu(game, ownCollection = own)
        blocking { idle() }
    }

    private fun GameMenuController.keys() = panel!!.content.focusableRows.map { it.key }

    private fun GameMenuController.row(key: String) = panel!!.content.focusableRows.first { it.key == key }

    private fun GameMenuController.press(action: Action, times: Int = 1) = repeat(times) { onAction(action) }

    /** Gamepad: Down to the row, A. */
    private fun GameMenuController.pick(key: String) {
        val at = keys().indexOf(key)
        assertTrue("$key is listed", at >= 0)
        press(Action.Move(Direction.Down), at - panel!!.focus)
        press(Action.Enter)
        blocking { idle() }
    }

    /** Touch: a tap on the row. */
    private fun GameMenuController.tap(key: String) {
        onAction(Action.TapItem(Column.Details, keys().indexOf(key)))
        blocking { idle() }
    }

    private fun names() = blocking { db.collectionDao().observeCollections().first().map { it.name } }

    private fun holding(game: GameRef) = blocking { db.collectionDao().observeCollectionsOf(game.key.toLong()).first().toSet() }

    private fun ownName(menu: GameMenuController) = (menu.panel!!.screen as PanelScreen.OwnName)

    private fun pickerRows(menu: GameMenuController) = (menu.panel!!.screen as PanelScreen.CollectionPicker).state.rows

    @Test
    fun notWiredThereIsNoEntryAndNewCollectionDoesNothing() =
        run {
            val g = game()
            val menu = menu(wired = false)
            menu.open(g)
            assertFalse(GameMenuEntryId.AddToCollection.name in menu.keys())
            menu.close()
            assertFalse(menu.openNewCollection())
            assertNull(menu.panel)
        }

    @Test
    fun theValueIsNothingTheNameOrTheCount() =
        run {
            val g = game()
            val a = create("Road trip")
            val b = create("RPG")
            val menu = menu()
            menu.open(g)
            assertEquals(R.string.collections_add_to, (menu.row(GameMenuEntryId.AddToCollection.name).label as UiText.Res).id)
            assertNull("none: no value", menu.row(GameMenuEntryId.AddToCollection.name).value)
            menu.close()
            add(a, g)
            menu.open(g)
            assertEquals(UiText.Plain("Road trip"), menu.row(GameMenuEntryId.AddToCollection.name).value)
            menu.close()
            add(b, g)
            menu.open(g)
            assertEquals(UiText.Plural(R.plurals.collections_count, 2), menu.row(GameMenuEntryId.AddToCollection.name).value)
        }

    @Test
    fun gamepadTicksAndUnticksAtOnceTheMenuStaysOnPickerAndBackShowsTheValue() =
        run {
            val g = game()
            val a = create("Road trip")
            create("RPG")
            val menu = menu()
            menu.open(g)
            menu.pick(GameMenuEntryId.AddToCollection.name)
            assertEquals(listOf(false, false, false), pickerRows(menu).map { (it as? PickerRow.Collection)?.ticked ?: false })
            menu.pick(COLLECTION_KEY_PREFIX + a)
            assertTrue("ticked", (pickerRows(menu).first() as PickerRow.Collection).ticked)
            assertTrue("the picker stays open", menu.panel!!.screen is PanelScreen.CollectionPicker)
            assertEquals(setOf(a), holding(g))
            menu.press(Action.Back)
            assertTrue(menu.panel!!.screen is PanelScreen.Menu)
            assertEquals(UiText.Plain("Road trip"), menu.row(GameMenuEntryId.AddToCollection.name).value)
            menu.pick(GameMenuEntryId.AddToCollection.name)
            menu.pick(COLLECTION_KEY_PREFIX + a)
            assertFalse((pickerRows(menu).first() as PickerRow.Collection).ticked)
            assertEquals(emptySet<Long>(), holding(g))
            menu.press(Action.Back)
            assertNull("none again", menu.row(GameMenuEntryId.AddToCollection.name).value)
        }

    @Test
    fun touchTicksAndUnticks() =
        run {
            val g = game()
            val a = create("Road trip")
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            menu.tap(COLLECTION_KEY_PREFIX + a)
            assertEquals(setOf(a), holding(g))
            assertNotNull(menu.panel!!.content.focusableRows.firstOrNull { it.isTicked })
            menu.tap(COLLECTION_KEY_PREFIX + a)
            assertEquals(emptySet<Long>(), holding(g))
            assertTrue(menu.panel!!.content.focusableRows.none { it.isTicked })
        }

    @Test
    fun aFailedWriteUndoesTheTickAndSaysSo() =
        run {
            val g = game()
            val a = create("Road trip")
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            blocking { db.collectionDao().delete(a) } // gone since the picker was read: the add returns false
            menu.tap(COLLECTION_KEY_PREFIX + a)
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertEquals(emptySet<Long>(), holding(g))
            menu.press(Action.Back)
            assertTrue("B returns to the picker", menu.panel!!.screen is PanelScreen.CollectionPicker)
            assertTrue("the tick is not there", menu.panel!!.content.focusableRows.none { it.isTicked })
            menu.press(Action.Back)
            assertNull("the menu's value did not change", menu.row(GameMenuEntryId.AddToCollection.name).value)
        }

    @Test
    fun newCollectionFromThePickerMakesItHoldTheGameAndReturnsTicked() =
        run {
            val g = game()
            create("Road trip")
            val menu = menu()
            menu.open(g)
            menu.pick(GameMenuEntryId.AddToCollection.name)
            menu.pick(NEW_COLLECTION_KEY)
            val screen = ownName(menu)
            assertEquals(UiText.Res(R.string.collections_name_title), menu.panel!!.content.title)
            assertEquals(40, screen.typing.maxLength)
            assertTrue(screen.typing.fieldText.isEmpty())
            screen.typing.replaceText("Road trip") // a name in use
            menu.press(Action.Move(Direction.Down))
            menu.press(Action.Enter) // Save
            blocking { menu.idle() }
            assertEquals(listOf("Road trip", "Road trip 2"), names())
            val picker = menu.panel!!.screen as PanelScreen.CollectionPicker
            val ticked = picker.state.rows.filterIsInstance<PickerRow.Collection>().single { it.ticked }
            assertEquals("Road trip 2", ticked.name)
            assertEquals(setOf(ticked.id), holding(g))
        }

    @Test
    fun touchSavesTheNewNameAndAnEmptyNameIsRefused() =
        run {
            val g = game()
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            menu.tap(NEW_COLLECTION_KEY)
            val typing = ownName(menu).typing
            menu.onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
            blocking { menu.idle() }
            assertTrue("refused", typing.isRefused)
            assertTrue(menu.panel!!.screen is PanelScreen.OwnName)
            assertEquals(emptyList<String>(), names())
            typing.replaceText("   ")
            menu.onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
            assertTrue("only spaces: refused", typing.isRefused)
            typing.replaceText("RPG")
            menu.onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
            blocking { menu.idle() }
            assertEquals(listOf("RPG"), names())
            assertEquals(1, holding(g).size)
            assertTrue(menu.panel!!.screen is PanelScreen.CollectionPicker)
        }

    /** The 41st character is refused while typing (own keyboard and a straight text change), so Save never cuts a name unseen. */
    @Test
    fun theFortyFirstCharacterIsRefusedWhileTyping() =
        run {
            val g = game()
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            menu.tap(NEW_COLLECTION_KEY)
            val typing = ownName(menu).typing
            typing.replaceText("x".repeat(40))
            typing.replaceText("x".repeat(41))
            assertEquals(40, typing.fieldText.length)
            typing.onKeyboardEvent(KeyboardEvent.Type('y'))
            assertEquals("x".repeat(40), typing.fieldText)
            menu.onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
            blocking { menu.idle() }
            assertEquals(listOf("x".repeat(40)), names())
        }

    @Test
    fun aFailureAfterLeavingThePickerStillSaysSoAndTheMenuValueIsRight() =
        run {
            val g = game()
            val a = create("Road trip")
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            graph.applyGate = CompletableDeferred()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf(COLLECTION_KEY_PREFIX + a))) // the write is held
            menu.press(Action.Back) // B while it runs
            assertEquals("the menu shows the optimistic tick", UiText.Plain("Road trip"), menu.row(GameMenuEntryId.AddToCollection.name).value)
            graph.applyGate!!.complete(false)
            blocking { menu.idle() }
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            menu.press(Action.Back)
            assertTrue(menu.panel!!.screen is PanelScreen.Menu)
            assertNull("the value is read again from the data", menu.row(GameMenuEntryId.AddToCollection.name).value)
            assertEquals(emptySet<Long>(), holding(g))
        }

    @Test
    fun aCreateFinishingAfterBackRefreshesThePickerAndTheMenuValue() =
        run {
            val g = game()
            val menu = menu()
            menu.open(g)
            menu.tap(GameMenuEntryId.AddToCollection.name)
            menu.tap(NEW_COLLECTION_KEY)
            ownName(menu).typing.replaceText("RPG")
            graph.createGate = CompletableDeferred()
            menu.press(Action.Move(Direction.Down))
            menu.press(Action.Enter) // Save: the create is held
            menu.press(Action.Back) // B on the keyboard
            assertTrue(menu.panel!!.screen is PanelScreen.CollectionPicker)
            graph.createGate!!.complete(Unit)
            blocking { menu.idle() }
            val picker = menu.panel!!.screen as PanelScreen.CollectionPicker
            assertEquals(listOf("RPG"), picker.state.rows.filterIsInstance<PickerRow.Collection>().filter { it.ticked }.map { it.name })
            menu.press(Action.Back)
            assertEquals(UiText.Plain("RPG"), menu.row(GameMenuEntryId.AddToCollection.name).value)
        }

    @Test
    fun newCollectionOnTheFoldersPageMakesAnEmptyOneAndCloses() =
        run {
            val menu = menu()
            assertTrue(menu.openNewCollection())
            assertEquals(GameMenuController.NO_GAME_KEY, menu.panel!!.game.key)
            ownName(menu).typing.replaceText("Kids")
            menu.press(Action.Move(Direction.Down))
            menu.press(Action.Enter)
            blocking { menu.idle() }
            assertNull("the folders list follows the database", menu.panel)
            assertEquals(listOf("Kids"), names())
            assertEquals(0, blocking { db.collectionDao().observeCollections().first().single().gameCount })
        }

    @Test
    fun removeFromCollectionIsOnlyInsideAnOwnCollectionAndUndoLastsFourSeconds() =
        run {
            val g = game()
            val a = create("Road trip")
            add(a, g)
            val menu = menu()
            menu.open(g)
            assertFalse("built-in folders and lists have none", GameMenuEntryId.RemoveFromCollection.name in menu.keys())
            menu.close()
            menu.open(g, OwnCollectionRef(a, "Road trip"))
            val entry = menu.row(GameMenuEntryId.RemoveFromCollection.name)
            assertEquals(UiText.Res(R.string.collections_remove_from, listOf("Road trip")), entry.label)
            val keys = menu.keys()
            assertEquals(GameMenuEntryId.Hide.name, keys[keys.indexOf(entry.key) + 1])
            menu.pick(entry.key)
            assertNull(menu.panel)
            assertEquals(emptySet<Long>(), holding(g))
            assertEquals(g, menu.undo.game)
            assertEquals(R.string.collections_notice_removed, menu.undo.notice)
            testScheduler.advanceTimeBy(RecentUndo.SHOWN_MS - 1)
            assertTrue(menu.undo.take())
            blocking { menu.idle() }
            assertEquals(setOf(a), holding(g))
            // Again, and let the 4 s pass.
            menu.open(g, OwnCollectionRef(a, "Road trip"))
            menu.tap(GameMenuEntryId.RemoveFromCollection.name)
            // The offer and its timer come once the write lands on its own thread: wait for it, as above, or the clock moves before the timer exists.
            blocking { menu.idle() }
            assertEquals("offered again", g, menu.undo.game)
            testScheduler.advanceTimeBy(RecentUndo.SHOWN_MS + 2)
            testScheduler.runCurrent()
            assertNull(menu.undo.game)
            assertFalse(menu.undo.take())
            assertEquals(emptySet<Long>(), holding(g))
        }
}
