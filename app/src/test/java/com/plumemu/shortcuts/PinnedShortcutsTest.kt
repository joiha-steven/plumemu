package com.plumemu.shortcuts

import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.LibraryHomeContent
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.EntryKind
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The accept flow of a pin request on a real (in-memory) library: Android's accept, then one game
 * per shortcut, listed in its system with the others; refusals store nothing; the availability
 * mark follows the apps; a host that throws never crashes the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PinnedShortcutsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val host = FakeShortcutHost()
    private val logged = CopyOnWriteArrayList<String>()
    private val shortcuts =
        PinnedShortcuts(
            dao = { db.pinnedShortcutDao() },
            catalog = { shippedCatalog },
            host = host,
            shortcutsName = "Shortcuts",
            scope = scope,
            clock = { 1_000 },
            log = { logged += it },
        )

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun aGameNativeShortcutIsOfferedForSteamAndAddedOnceAccepted() = runBlocking {
        val request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        host.request = request

        val offer = shortcuts.offerOf(Intent()) as PinOffer.Ask
        assertEquals(listOf("Broforce", "steam", "Steam"), listOf(offer.label, offer.systemId, offer.systemName))
        assertEquals("nothing is pinned before the person says Add", 0, request.acceptCalls.get())

        val added = shortcuts.add(offer) as PinAddOutcome.Added
        assertEquals(1, request.acceptCalls.get())
        assertTrue(added.isNew)
        val record = db.pinnedShortcutDao().byGame(added.gameId)!!
        assertEquals(listOf("app.gamenative", "game_274190", "Broforce", "0", "1000"), listOf(record.packageName, record.shortcutId, record.label, "${record.userSerial}", "${record.addedAt}"))
        assertTrue(logged.none { "Broforce" in it })
    }

    @Test
    fun theSameShortcutPinnedTwiceIsOneGame() = runBlocking {
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        val first = shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask) as PinAddOutcome.Added
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        val second = shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask) as PinAddOutcome.Added

        assertEquals(first.gameId, second.gameId)
        assertFalse(second.isNew)
        assertEquals(1, db.gameDao().observeSystemGames("steam").first().size)
    }

    @Test
    fun theShortcutGameIsListedInSteamWithItsCountAndCanBeFavourited() = runBlocking {
        val home = LibraryHomeContent(db.gameDao(), { shippedCatalog }, flowOf(emptyList()), flowOf(false), Dispatchers.Default)
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        val id = (shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask) as PinAddOutcome.Added).gameId
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut(id = "game_1", label = "Among Us"))
        shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask)

        val steam = withTimeout(TIMEOUT_MS) { home.content.first { c -> c.entries.any { it.id == "steam" } } }.entries.single { it.id == "steam" }
        assertEquals(listOf("Among Us", "Broforce"), steam.rows.map { it.title })
        assertEquals(2, steam.shownCount)
        assertEquals("Steam", steam.name)

        db.gameDao().setFavorite(id, true)
        val favorites = withTimeout(TIMEOUT_MS) { home.content.first { c -> c.entries.any { it.kind == EntryKind.Favorites } } }
        assertEquals(listOf("Broforce"), favorites.entries.single { it.kind == EntryKind.Favorites }.rows.map { it.title })
    }

    @Test
    fun anotherAppsShortcutGoesToTheShortcutsList() = runBlocking {
        val home = LibraryHomeContent(db.gameDao(), { shippedCatalog }, flowOf(emptyList()), flowOf(false), Dispatchers.Default)
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut(packageName = "com.example.notes", id = "note", label = "Groceries"))

        val offer = shortcuts.offerOf(Intent()) as PinOffer.Ask
        assertEquals(listOf(ShortcutFiling.SHORTCUTS, "Shortcuts"), listOf(offer.systemId, offer.systemName))
        shortcuts.add(offer)

        val list = withTimeout(TIMEOUT_MS) { home.content.first { c -> c.entries.any { it.id == ShortcutFiling.SHORTCUTS } } }
        assertEquals("Shortcuts", list.entries.single { it.id == ShortcutFiling.SHORTCUTS }.name)
    }

    @Test
    fun theShortcutsListsGamesShowItsShortNameInTheMixedLists() = runBlocking {
        val abbr = ApplicationProvider.getApplicationContext<android.content.Context>().getString(com.plumemu.R.string.shortcuts_system_abbr)
        val home =
            LibraryHomeContent(
                db.gameDao(), { shippedCatalog }, flowOf(emptyList()), flowOf(false), Dispatchers.Default,
                extraAbbrs = mapOf(ShortcutFiling.SHORTCUTS to abbr),
            )
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut(packageName = "com.example.notes", id = "note", label = "Groceries"))
        val id = (shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask) as PinAddOutcome.Added).gameId
        db.gameDao().setFavorite(id, true)

        val favorites = withTimeout(TIMEOUT_MS) { home.content.first { c -> c.entries.any { it.kind == EntryKind.Favorites } } }
        assertEquals("Link", abbr)
        assertEquals(listOf("Link"), favorites.entries.single { it.kind == EntryKind.Favorites }.rows.map { it.systemAbbr })
    }

    @Test
    fun aWidgetIsRefusedAndNothingIsPinnedOrStored() = runBlocking {
        val request = FakePinRequest(shortcut = null, kind = PinKind.Widget)
        host.request = request

        assertEquals(PinOffer.Refused(PinRefusal.Widget), shortcuts.offerOf(Intent()))
        assertEquals(0, request.acceptCalls.get())
        assertEquals(emptyList<Any>(), db.pinnedShortcutDao().keys())
    }

    @Test
    fun anAnsweredOrMissingRequestIsRefused() = runBlocking {
        host.request = null
        assertEquals(PinOffer.Refused(PinRefusal.Expired), shortcuts.offerOf(Intent()))
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut(), valid = false)
        assertEquals(PinOffer.Refused(PinRefusal.Expired), shortcuts.offerOf(Intent()))
    }

    @Test
    fun aPinAndroidDoesNotAcceptStoresNothing() = runBlocking {
        val request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut(), accepts = false)
        host.request = request

        assertEquals(PinAddOutcome.NotAccepted, shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask))
        assertEquals(emptyList<Any>(), db.pinnedShortcutDao().keys())
    }

    @Test
    fun aForgedRequestThatAndroidNeverPinnedIsNotStored() = runBlocking {
        // An app's own binder answers accept() with true, but Android pinned nothing for Plumemu.
        val forged = FakePinRequest(gameNativeShortcut(), pinsInto = null)
        host.request = forged

        assertEquals(PinAddOutcome.NotAccepted, shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask))
        assertEquals(1, forged.acceptCalls.get())
        assertEquals(emptyList<Any>(), db.pinnedShortcutDao().keys())
        assertEquals(emptyList<Any>(), db.gameDao().observeSystemGames("steam").first())
    }

    @Test
    fun withoutThePlatformDataNothingIsFiledYet() = runBlocking {
        val starting =
            PinnedShortcuts({ db.pinnedShortcutDao() }, { null }, host, "Shortcuts", scope, log = { logged += it })
        val request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        host.request = request

        assertEquals(PinOffer.Refused(PinRefusal.NotReady), starting.offerOf(Intent()))
        assertEquals(0, request.acceptCalls.get())
        assertEquals(emptyList<Any>(), db.pinnedShortcutDao().keys())
    }

    @Test
    fun aHostThatThrowsReadingTheRequestRefusesIt() = runBlocking {
        host.failure = ClassCastException("a Bundle where the request should be")
        assertEquals(PinOffer.Refused(PinRefusal.Expired), shortcuts.offerOf(Intent()))
    }

    @Test
    fun aLibraryThatCannotStoreSaysSoInsteadOfThrowing() = runBlocking {
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        val offer = shortcuts.offerOf(Intent()) as PinOffer.Ask
        db.close()

        assertEquals(PinAddOutcome.NotSaved, shortcuts.add(offer))
        assertTrue(logged.any { "could not be stored" in it })
    }

    @Test
    fun aHostThatThrowsSecurityExceptionIsLoggedNeverThrown() = runBlocking {
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask)
        host.failure = SecurityException("not the home app")

        shortcuts.checkAvailability()

        assertTrue(logged.any { "SecurityException" in it })
        assertTrue("nothing marked from a failed read", db.pinnedShortcutDao().keys().single().isAvailable)
    }

    @Test
    fun notBeingTheHomeAppChangesNothing() = runBlocking {
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask)
        host.pinned["app.gamenative"] = null

        shortcuts.checkAvailability()

        assertTrue(db.pinnedShortcutDao().keys().single().isAvailable)
    }

    @Test
    fun aDisabledOrRemovedShortcutIsMarkedUnavailableAndComesBack() = runBlocking {
        host.request = FakePinRequest(pinsInto = host.acceptedPins, shortcut = gameNativeShortcut())
        val id = (shortcuts.add(shortcuts.offerOf(Intent()) as PinOffer.Ask) as PinAddOutcome.Added).gameId

        host.pinned["app.gamenative"] = listOf(gameNativeShortcut().copy(isEnabled = false))
        shortcuts.checkAvailability()
        assertFalse(db.pinnedShortcutDao().byGame(id)!!.isAvailable)
        assertEquals("the game stays listed", 1, db.gameDao().observeSystemGames("steam").first().size)

        host.pinned["app.gamenative"] = listOf(gameNativeShortcut())
        shortcuts.checkAvailability()
        assertTrue(db.pinnedShortcutDao().byGame(id)!!.isAvailable)
        assertEquals("listens once per process", 1, host.listenCalls.get())

        // The app removed (Android's callback with an empty list).
        host.listener!!("app.gamenative", 0, emptyList())
        withTimeout(TIMEOUT_MS) { while (db.pinnedShortcutDao().byGame(id)!!.isAvailable) delay(10) }
    }

    @Test
    fun belowAndroid8NothingChanges() = runBlocking {
        val none =
            PinnedShortcuts({ db.pinnedShortcutDao() }, { shippedCatalog }, ShortcutHost.None, "Shortcuts", scope, log = { logged += it })

        assertEquals(PinOffer.Refused(PinRefusal.Expired), none.offerOf(Intent()))
        none.checkAvailability()
        assertEquals(emptyList<Any>(), db.pinnedShortcutDao().keys())
        assertNull(ShortcutHost.None.pinnedShortcuts("app.gamenative", 0))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
