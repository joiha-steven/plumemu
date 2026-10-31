package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.SettingsWriter
import com.plumemu.settings.withGridView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SELECT's list or grid is remembered per system in the stored settings
 * (`appearance.grid_systems`), not only in the screen's saved state: on an AYN Thor every grid
 * fell back to a list once Android killed the process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeGridViewRememberedTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter

    /** The writes SELECT asked for, in order, with each one's failure callback. */
    private val writes = mutableListOf<Pair<String, Boolean>>()
    private val failures = mutableListOf<() -> Unit>()

    /** What the stand-in store holds; a test moves it to say a write landed. */
    private var stored by mutableStateOf(emptySet<String>())

    private fun showWithStandInStore() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default) {
                HomeScreen(
                    FakeHomeContent.withArtwork(),
                    router,
                    HomeCallbacks(onGridView = { id, isGrid, onFailed ->
                        writes += id to isGrid
                        failures += onFailed
                    }),
                    gridSystems = stored,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun openSnes() = openEntry(FakeHomeContent.BOARD_SNES_INDEX)

    /** From the first frame (Recent focused in the system column): entry [index]'s games. */
    private fun openEntry(index: Int) = send(*List(index) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    private fun gridCount() = compose.onAllNodesWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun eachPressWritesOnceAndShowsBeforeTheWriteComesBack() {
        showWithStandInStore()
        openSnes()
        assertEquals("a list until SELECT", 0, gridCount())
        send(Action.ToggleView)
        assertEquals("one write for one press", listOf("snes" to true), writes.toList())
        assertEquals("the grid shows before the store answers", 1, gridCount())
        send(Action.ToggleView)
        assertEquals(listOf("snes" to true, "snes" to false), writes.toList())
        assertEquals(0, gridCount())
    }

    @Test
    fun anEarlierWriteLandingBetweenTwoQuickPressesDoesNotFlashTheGrid() {
        showWithStandInStore()
        openSnes()
        send(Action.ToggleView, Action.ToggleView)
        // The first write ("grid") lands only now; the second ("list") is still on its way.
        stored = setOf("snes")
        compose.waitForIdle()
        assertEquals("the last press is what shows", 0, gridCount())
        stored = emptySet()
        compose.waitForIdle()
        assertEquals(0, gridCount())
        assertEquals("nothing written by the echoes", 2, writes.size)
    }

    @Test
    fun aStoredGridShowsWithoutAPressAndAnIdNoSystemHasIsIgnored() {
        stored = setOf("gone-system", "snes")
        showWithStandInStore()
        openSnes()
        assertEquals("Super Nintendo opens as the stored grid", 1, gridCount())
        assertEquals("reading the stored choice writes nothing", emptyList<Pair<String, Boolean>>(), writes.toList())
        send(Action.Back, Action.Move(Direction.Down), Action.Enter)
        assertEquals("the next system is still a list", 0, gridCount())
    }

    @Test
    fun selectOnAnEntryWithoutGamesWritesNothingAndTheSavedGridStays() {
        stored = setOf("favorites")
        val plain = FakeHomeContent.withArtwork()
        val noFavorites = HomeContent.of(
                plain.entries[0].rows,
                emptyList(),
                plain.entries[2].rows,
                plain.entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows) },
                hasBoxArt = true,
            )
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default) {
                HomeScreen(noFavorites, router, HomeCallbacks(onGridView = { id, isGrid, onFailed -> writes += id to isGrid; failures += onFailed }), gridSystems = stored)
            }
        }
        compose.waitForIdle()
        send(Action.Move(Direction.Down), Action.ToggleView) // Favorites, which is empty
        assertEquals("nothing is written", emptyList<Pair<String, Boolean>>(), writes.toList())
        assertEquals("the saved choice is still the grid", setOf("favorites"), stored)
        assertEquals("and it shows as a list", 0, gridCount())
    }

    @Test
    fun aFailedWriteKeepsTheGridAndSaysSo() {
        showWithStandInStore()
        openSnes()
        send(Action.ToggleView)
        compose.runOnIdle { failures.single()() }
        compose.waitForIdle()
        assertTrue("the save message is shown", hasText("That change could not be saved"))
        send(Action.Enter)
        assertTrue("the message closed", !hasText("That change could not be saved"))
        assertEquals("the person's choice stays on screen", 1, gridCount())
        assertEquals("no second write", 1, writes.size)
    }

    /** Recent and Favorites switch like a system, each under its own entry id. */
    @Test
    fun selectOnRecentAndOnFavoritesShowsTheirGridsAndWritesTheirIds() {
        showWithStandInStore()
        openEntry(RECENT)
        assertEquals("Recent is a list until SELECT", 0, gridCount())
        send(Action.ToggleView)
        assertEquals(listOf(HomeContent.RECENT_ID to true), writes.toList())
        assertEquals("Recent's grid shows", 1, gridCount())
        send(Action.Back, Action.Move(Direction.Down), Action.Enter)
        assertEquals("Favorites is still a list", 0, gridCount())
        send(Action.ToggleView)
        assertEquals(listOf(HomeContent.RECENT_ID to true, HomeContent.FAVORITES_ID to true), writes.toList())
        assertEquals("Favorites' grid shows", 1, gridCount())
        send(Action.ToggleView)
        assertEquals("and back to a list", HomeContent.FAVORITES_ID to false, writes.last())
        assertEquals(0, gridCount())
    }

    @Test
    fun storedRecentAndFavoritesGridsShowWithoutAPress() {
        stored = setOf(HomeContent.RECENT_ID, HomeContent.FAVORITES_ID)
        showWithStandInStore()
        openEntry(RECENT)
        assertEquals("Recent opens as the stored grid", 1, gridCount())
        send(Action.Back, Action.Move(Direction.Down), Action.Enter)
        assertEquals("Favorites opens as the stored grid", 1, gridCount())
        send(Action.Back, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
        assertEquals("Super Nintendo is not in the set: a list", 0, gridCount())
        assertEquals("reading the stored choice writes nothing", emptyList<Pair<String, Boolean>>(), writes.toList())
    }

    /** Apps' grid of icons (a stored "apps" used to be ignored): a stored "apps" opens as the grid, SELECT writes once. */
    @Test
    fun aStoredAppsOpensAsTheGridAndSelectWritesItsListOnce() {
        stored = setOf(HomeContent.APPS_ID)
        showWithStandInStore()
        openEntry(APPS)
        assertEquals("a stored apps id shows the grid", 1, gridCount())
        send(Action.ToggleView)
        assertEquals("one write for one press", listOf(HomeContent.APPS_ID to false), writes.toList())
        assertEquals(0, gridCount())
    }

    // A real settings file, written through the app's writer queue, then a new repository on it.

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val jobs = mutableListOf<Job>()
    private var process by mutableStateOf<Process?>(null)
    private var shownStored: Set<String>? = null
    private val failedWrites = mutableListOf<String>()

    /** A process's store, its settings as `AppGraph.settings` holds them (read eagerly in the process's scope) and its writer queue. */
    private class Process(
        val repository: SettingsRepository,
        val settings: StateFlow<PlumSettings?>,
        val writer: SettingsWriter,
    )

    private fun openProcess(): Process {
        val scope = CoroutineScope(Dispatchers.IO + Job().also { jobs += it })
        val repo = SettingsRepository.create(context, scope)
        return Process(repo, repo.settings.stateIn(scope, SharingStarted.Eagerly, null), SettingsWriter(scope, { transform -> repo.update(transform) }, log = { failedWrites += it }))
    }

    /** Ends the process's store and writer, as a killed process does (only one store may be open on the file). */
    private fun closeRepository() = runBlocking { jobs.forEach { it.cancelAndJoin() }.also { jobs.clear() } }

    @After
    fun tearDown() = closeRepository()

    private fun showWithRealStore() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            // A new repository is a new composition: nothing the old screen remembered or saved is there.
            process?.let { running -> key(running) { HomeOnStore(running) } }
        }
        compose.waitUntil(LOAD_MS) { shownStored != null }
        compose.waitForIdle()
    }

    @Composable
    private fun HomeOnStore(running: Process) {
        val settings by running.settings.collectAsState()
        // As MainScreen: nothing composes before the settings are read.
        val loaded = settings
        if (loaded != null) {
            SideEffect { shownStored = loaded.appearance.gridSystems }
            PlumTheme(PlumThemeSettings.Default) {
                HomeScreen(
                    FakeHomeContent.withArtwork(),
                    router,
                    HomeCallbacks(onGridView = { id, isGrid, onFailed ->
                        writes += id to isGrid
                        running.writer.submitTold({ it.withGridView(id, isGrid) }, onFailed)
                    }),
                    gridSystems = loaded.appearance.gridSystems,
                )
            }
        }
    }

    @Test
    fun aGridChoiceSurvivesANewCompositionAndANewSettingsRepository() {
        process = openProcess().also { first -> compose.waitUntil(LOAD_MS) { first.settings.value != null } }
        showWithRealStore()
        openSnes()
        send(Action.ToggleView)
        assertEquals(1, gridCount())
        // Waited for on the screen's own collection of the store, with the main thread turning.
        compose.waitUntil(LOAD_MS) { shownStored == setOf("snes") }
        assertEquals("one write for the press", listOf("snes" to true), writes.toList())

        // The process ends; a new one opens the same file and composes the screen from nothing.
        closeRepository()
        shownStored = null
        val second = openProcess()
        // As MainActivity: the screen composes once the process's settings are read.
        compose.waitUntil(LOAD_MS) { second.settings.value != null }
        // Applied as a snapshot of its own: a plain write here was sometimes never seen by the composition.
        compose.runOnIdle { Snapshot.withMutableSnapshot { process = second } }
        compose.waitUntil(LOAD_MS) { shownStored != null }
        compose.waitForIdle()
        assertEquals("the new repository reads the file back", setOf("snes"), shownStored)
        openSnes()
        assertEquals("Super Nintendo is still a grid after the restart", 1, gridCount())
        assertEquals("reading it back writes nothing", 1, writes.size)

        // And back to a list, stored for the next start too.
        send(Action.ToggleView)
        assertEquals(0, gridCount())
        compose.waitUntil(LOAD_MS) { shownStored == emptySet<String>() }
        assertEquals(PlumSettings.Defaults.appearance, runBlocking { second.repository.readOnce().appearance })
        assertEquals("no write failed", emptyList<String>(), failedWrites.toList())
    }

    /** Favorites' grid, like a system's, outlives the process: written to the file and read back by a new repository. */
    @Test
    fun favoritesGridSurvivesANewSettingsRepository() {
        process = openProcess().also { first -> compose.waitUntil(LOAD_MS) { first.settings.value != null } }
        showWithRealStore()
        openEntry(FAVORITES)
        send(Action.ToggleView)
        compose.waitUntil(LOAD_MS) { shownStored == setOf(HomeContent.FAVORITES_ID) }

        closeRepository()
        shownStored = null
        val second = openProcess()
        compose.waitUntil(LOAD_MS) { second.settings.value != null }
        compose.runOnIdle { Snapshot.withMutableSnapshot { process = second } }
        compose.waitUntil(LOAD_MS) { shownStored != null }
        compose.waitForIdle()
        assertEquals("the new repository reads it back", setOf(HomeContent.FAVORITES_ID), shownStored)
        openEntry(FAVORITES)
        assertEquals("Favorites is still a grid after the restart", 1, gridCount())
        assertEquals("one write in all", 1, writes.size)
        assertEquals("no write failed", emptyList<String>(), failedWrites.toList())
    }

    private companion object {
        const val LOAD_MS = 5_000L
        const val RECENT = 0
        const val FAVORITES = 1
        const val APPS = 2
    }
}
