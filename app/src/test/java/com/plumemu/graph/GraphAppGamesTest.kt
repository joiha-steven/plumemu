package com.plumemu.graph

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.AppKind
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.apps.LaunchableApp
import com.plumemu.launcher.apps.PackagePresence
import com.plumemu.platforms.PlatformCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
 * The app games' sync on the real database and the shipped platform data: what it lists,
 * and above all when it must not write, because [com.plumemu.coredata.db.AppGameDao.sync] deletes the
 * rows (with play time) of every package missing from the list it is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAppGamesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val logged = mutableListOf<String>()
    private var installed: List<LaunchableApp>? = emptyList()
    private var readFailure: RuntimeException? = null
    private var reads = 0

    /** Android's answer for a package missing from the list; uninstalled unless a test says otherwise. */
    private val presences = mutableMapOf<String, PackagePresence>()
    private var presenceFailure: RuntimeException? = null
    private val catalog: PlatformCatalog by lazy { runBlocking { checkNotNull(graph.catalog()) } }

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun appGames(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) = GraphAppGames(
        dao = { graph.database.appGameDao() },
        catalog = { catalog },
        readApps = {
            reads++
            readFailure?.let { throw it }
            installed
        },
        presence = { packageName ->
            presenceFailure?.let { throw it }
            presences[packageName] ?: PackagePresence.NotInstalled
        },
        ownPackage = "com.plumemu",
        scope = scope,
        io = io,
        log = { logged += it },
        clock = { 1_000L },
    )

    private fun app(packageName: String, label: String, isGame: Boolean = false) = LaunchableApp(label, packageName, "$packageName.Main", isGame)

    private val settings = app("com.android.settings", "Settings")
    private val celeste = app("com.celeste", "Celeste", isGame = true)
    private val deltaTouch = app("com.opentouchgaming.deltatouch", "Delta Touch")
    private val retroArch = app("com.retroarch", "RetroArch", isGame = true)

    private suspend fun listed(): Map<String, String> {
        val dao = graph.database.appGameDao()
        return dao.records().associate { it.packageName to checkNotNull(graph.database.gameDao().game(it.gameId)).systemId }
    }

    @Test
    fun aSyncListsGamesAndKnownPortsButNotEmulatorsNorPlainApps() = runBlocking {
        installed = listOf(settings, celeste, deltaTouch, retroArch)

        val outcome = appGames().syncNow()

        val expected = mapOf("com.celeste" to "android", "com.opentouchgaming.deltatouch" to "ports")
        assertTrue("$outcome", outcome is AppGamesSyncOutcome.Synced)
        assertEquals(expected, listed())
        val celesteGame = graph.database.gameDao().game(graph.database.appGameDao().records().first { it.packageName == "com.celeste" }.gameId)!!
        assertEquals("Celeste", celesteGame.title)
    }

    @Test
    fun theMarkedFlowNamesTheAppsListedAsGames() = runBlocking {
        val games = appGames()
        assertNull("nothing known before the first sync", games.marked.value)
        installed = listOf(settings, celeste, deltaTouch)

        games.syncNow()

        assertEquals(mapOf("com.celeste" to "android", "com.opentouchgaming.deltatouch" to "ports"), games.marked.value)
    }

    @Test
    fun aFailedReadWritesNothingAndKeepsTheGamesWithTheirPlayTime() = runBlocking {
        installed = listOf(settings, celeste)
        val games = appGames()
        games.syncNow()
        val before = listed()

        readFailure = IllegalStateException("package manager died")
        assertTrue(games.syncNow() is AppGamesSyncOutcome.Skipped)
        readFailure = null
        installed = null
        assertTrue("no launcher: skipped", games.syncNow() is AppGamesSyncOutcome.Skipped)
        installed = emptyList()
        assertTrue("an empty read is a failed read", games.syncNow() is AppGamesSyncOutcome.Skipped)

        assertEquals(before, listed())
        assertEquals(mapOf("com.celeste" to "android"), games.marked.value)
        assertTrue(logged.any { it.startsWith("App games: not synced") })
    }

    @Test
    fun anUninstalledGameLeavesOnAWholeRead() = runBlocking {
        installed = listOf(settings, celeste)
        val games = appGames()
        games.syncNow()

        installed = listOf(settings)
        games.syncNow()

        assertEquals(emptyMap<String, String>(), listed())
    }

    @Test
    fun aDisabledGameMissingFromTheListKeepsItsRowAndPlayTime() = runBlocking {
        installed = listOf(settings, celeste)
        val games = appGames()
        games.syncNow()
        val id = graph.database.appGameDao().records().single().gameId
        graph.database.gameDao().setFavorite(id, true)
        graph.database.playDao().addSession(id, endedAt = 90_000, seconds = 600)

        // Disabled in Android's Settings: the launcher list omits it, Android still has it.
        installed = listOf(settings)
        presences["com.celeste"] = PackagePresence.Installed
        games.syncNow()

        assertEquals(mapOf("com.celeste" to "android"), listed())
        assertEquals(id, graph.database.appGameDao().records().single().gameId)
        assertTrue(graph.database.gameDao().game(id)!!.isFavorite)
        assertEquals(600L, graph.database.playDao().play(id)?.totalSeconds)

        // Turned on again: the same row, the same play time.
        installed = listOf(settings, celeste)
        games.syncNow()
        assertEquals(id, graph.database.appGameDao().records().single().gameId)
        assertEquals(600L, graph.database.playDao().play(id)?.totalSeconds)
    }

    @Test
    fun aDisabledGameThePersonCallsNotAGameLeaves() = runBlocking {
        installed = listOf(settings, celeste)
        val games = appGames()
        games.syncNow()

        // Disabled, so missing from the list, but still installed: kept until the person says it is no game.
        installed = listOf(settings)
        presences["com.celeste"] = PackagePresence.Installed
        games.syncNow()
        assertEquals(mapOf("com.celeste" to "android"), listed())

        games.setChoice("com.celeste", AppKind.APP)
        assertEquals(emptyMap<String, String>(), listed())

        // Asking it to be a port again keeps the missing app's row from then on.
        installed = listOf(settings, celeste)
        games.setChoice("com.celeste", AppKind.PORT)
        installed = listOf(settings)
        games.syncNow()
        assertEquals(mapOf("com.celeste" to "ports"), listed())
    }

    @Test
    fun aPresenceCheckThatFailsKeepsTheRow() = runBlocking {
        installed = listOf(settings, celeste)
        val games = appGames()
        games.syncNow()

        installed = listOf(settings)
        presences["com.celeste"] = PackagePresence.Unknown
        games.syncNow()
        assertEquals(mapOf("com.celeste" to "android"), listed())

        presences.clear()
        presenceFailure = IllegalStateException("package manager died")
        games.syncNow()
        assertEquals(mapOf("com.celeste" to "android"), listed())
    }

    @Test
    fun thePersonsChoiceIsStoredAndFollowedAtOnce() = runBlocking {
        installed = listOf(settings, celeste, retroArch)
        val games = appGames()
        games.syncNow()

        games.setChoice("com.celeste", AppKind.APP)
        games.setChoice("com.retroarch", AppKind.PORT)
        games.setChoice("com.android.settings", AppKind.GAME)
        assertEquals(mapOf("com.retroarch" to "ports", "com.android.settings" to "android"), listed())

        games.clearChoice("com.celeste")
        assertEquals("Android's mark decides again", "android", listed()["com.celeste"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aBurstOfRequestsMakesOneReadAndAnUpdatesRemovalNone() = runTest {
        installed = null // reads only counted: nothing is written
        val dispatcher = StandardTestDispatcher(testScheduler)
        val games = appGames(scope = backgroundScope, io = dispatcher)

        games.onPackageChange(isTemporaryRemoval = true)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("an update's removal asks for nothing", 0, reads)

        repeat(3) {
            games.onPackageChange(isTemporaryRemoval = false)
            advanceTimeBy(500)
        }
        assertEquals("still inside the burst", 0, reads)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("one read for the burst", 1, reads)
    }

    @Test
    fun onlyAnUpdatesRemovalOrStorageGoingAwayIsTemporary() {
        val uri = Uri.parse("package:com.celeste")
        assertTrue(PackageChanges.isTemporaryRemoval(Intent(Intent.ACTION_PACKAGE_REMOVED, uri).putExtra(Intent.EXTRA_REPLACING, true)))
        assertFalse(PackageChanges.isTemporaryRemoval(Intent(Intent.ACTION_PACKAGE_REMOVED, uri)))
        assertFalse(PackageChanges.isTemporaryRemoval(Intent(Intent.ACTION_PACKAGE_ADDED, uri).putExtra(Intent.EXTRA_REPLACING, true)))
        assertTrue("storage with apps went away", PackageChanges.isTemporaryRemoval(Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)))
        assertFalse("storage came back: sync", PackageChanges.isTemporaryRemoval(Intent(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)))
    }
}
