package com.plumemu.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import com.plumemu.launcher.play.SystemPlayClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's second screen hears of a game start only when an Intent really goes out: right before
 * `startActivity` ([GameStartObserver.beforeStart]), with [GameStartObserver.startFailed] when that
 * call throws, and nothing for a launch that stops earlier (missing file, cancelled).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameStartObserverTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.small
    private val documents = mutableMapOf<String, DocumentState>()
    private var startFailure: RuntimeException? = null

    /** What happened, in order: the observer's calls and the starts themselves. */
    private val events = mutableListOf<String>()
    private var onProbe: () -> Unit = {}
    private lateinit var launcher: GameLauncher

    private val observer =
        object : GameStartObserver {
            override fun beforeStart() {
                events += "before start"
            }

            override fun startFailed() {
                events += "start failed"
            }
        }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val io = UnconfinedTestDispatcher()
        val log = RecordingLog()
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val playTime = PlayTimeTracker(SharedPreferencesSessionStore(app), db.playDao(), db.gameDao(), SystemPlayClock(app.contentResolver), log, io)
        val starter = ActivityStarter { intent: Intent -> startFailure?.let { throw it } ?: events.add("started ${intent.`package` ?: intent.component?.packageName}") }
        launcher =
            GameLauncher(
                catalog,
                db.gameDao(),
                EmulatorChoices(db.emulatorChoiceDao(), catalog, { it.packageName == "com.ra" }, log, io),
                { uri ->
                    onProbe()
                    documents[uri] ?: DocumentState.Missing
                },
                { _, _ -> error("only PS Vita emulators read a game file") },
                starter.observedBy(observer),
                playTime,
                log,
                io,
                storageRoots = OWNER_STORAGE,
            )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun game(present: Boolean = true): Long {
        val documentId = "primary:ROMs/snes/Secret Game.sfc"
        val id = db.addGame("snes", documentId, INTERNAL_TREE)
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "ra-snes9x")
        if (present) {
            val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(INTERNAL_TREE), documentId).toString()
            documents[uri] = DocumentState.Present("application/octet-stream")
        }
        return id
    }

    @Test
    fun theObserverHearsRightBeforeTheStart() = runTest {
        val id = game()
        assertTrue(launcher.launch(id) is LaunchOutcome.Started)
        assertEquals(listOf("before start", "started com.ra"), events)
    }

    @Test
    fun aStartThatThrowsIsReportedAsFailed() = runTest {
        val id = game()
        startFailure = ActivityNotFoundException("test")
        assertTrue(launcher.launch(id) is LaunchOutcome.Failed)
        assertEquals(listOf("before start", "start failed"), events)
    }

    @Test
    fun aLaunchThatStopsBeforeTheStartTellsNothing() = runTest {
        val id = game(present = false)
        assertTrue(launcher.launch(id) is LaunchOutcome.FileMissing)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun aLaunchCancelledBeforeTheStartTellsNothing() = runTest {
        val id = game()
        val job = launch(start = CoroutineStart.LAZY) { launcher.launch(id) }
        // Cancelled while the file is being checked: the start never comes.
        onProbe = { job.cancel() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), events)
    }
}
