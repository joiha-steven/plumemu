package com.plumemu.launcher

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.ConfirmedCores
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.choice.SharedPreferencesConfirmedCores
import com.plumemu.launcher.play.PlayClock
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class MovingClock(var wall: Long = 1_700_000_000_000, var realtime: Long = 50_000) : PlayClock {
    override fun wallMs() = wall

    override fun realtimeMs() = realtime

    override fun bootCount() = 3

    fun advance(seconds: Long) {
        wall += seconds * 1_000
        realtime += seconds * 1_000
    }
}

/**
 * A RetroArch core Plumemu cannot see: the launch carries it
 * through the play session to the outcome until the person confirms it, and the session it recorded
 * can be taken back exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreConfirmLaunchTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.small
    private val installed = mutableSetOf("com.ra", "com.snes9x")
    private val documents = mutableMapOf<String, DocumentState>()
    private val started = mutableListOf<Intent>()
    private val clock = MovingClock()
    private val log = RecordingLog()
    private lateinit var cores: ConfirmedCores
    private lateinit var playTime: PlayTimeTracker
    private lateinit var launcher: GameLauncher

    private val snes9xCore = CoreToConfirm("com.ra", "snes9x_libretro_android.so")

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val io = UnconfinedTestDispatcher()
        cores = SharedPreferencesConfirmedCores(ApplicationProvider.getApplicationContext())
        playTime = PlayTimeTracker(SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()), db.playDao(), db.gameDao(), clock, log, io)
        launcher = GameLauncher(
            catalog,
            db.gameDao(),
            EmulatorChoices(db.emulatorChoiceDao(), catalog, { it.packageName in installed }, log, io),
            { uri -> documents[uri] ?: DocumentState.Missing },
            { _, _ -> error("no game file is read here") },
            { intent -> started.add(intent) },
            playTime,
            log,
            io,
            StorageVolumes.AllPresent,
            cores,
            storageRoots = OWNER_STORAGE,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun game(systemChoice: String): Long {
        val documentId = "primary:ROMs/snes/Secret Game.sfc"
        val id = db.addGame("snes", documentId, INTERNAL_TREE)
        db.emulatorChoiceDao().setPersonSystemChoice("snes", systemChoice)
        val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(INTERNAL_TREE), documentId).toString()
        documents[uri] = DocumentState.Present("application/octet-stream")
        return id
    }

    @Test
    fun anUnconfirmedCoreComesBackWithTheRecordedSessionWhichCanBeTakenBackOnce() = runTest {
        val id = game("ra-snes9x")
        val outcome = launcher.launch(id) as LaunchOutcome.Started
        assertEquals("snes9x_libretro_android.so", outcome.coreToConfirm)
        clock.advance(62)
        val recorded = playTime.onResume() as PlaySessionOutcome.Recorded
        assertEquals(snes9xCore, recorded.coreToConfirm)
        val added = checkNotNull(recorded.added) { "the session is kept for a No" }
        assertEquals(62L, db.playDao().play(id)?.totalSeconds)
        assertTrue(playTime.takeBack(added))
        assertNull("the game is as never played: out of Recent", db.playDao().play(id))
        assertTrue(db.playDao().sessions(id).isEmpty())
        assertFalse("a second No undoes nothing more", playTime.takeBack(added))
    }

    @Test
    fun aShortPlayWithAnUnconfirmedCoreCarriesTheCore() = runTest {
        val id = game("ra-snes9x")
        launcher.launch(id)
        clock.advance(4)
        assertEquals(PlaySessionOutcome.TooShort(id, 4, "ra-snes9x", snes9xCore), playTime.onResume())
    }

    @Test
    fun aConfirmedCoreIsNeverAskedAboutAgain() = runTest {
        val id = game("ra-snes9x")
        assertTrue(cores.confirm(snes9xCore))
        val outcome = launcher.launch(id) as LaunchOutcome.Started
        assertNull(outcome.coreToConfirm)
        clock.advance(62)
        val recorded = playTime.onResume() as PlaySessionOutcome.Recorded
        assertNull(recorded.coreToConfirm)
        assertNull("nothing to take back", recorded.added)
        assertTrue("the play is counted", (db.playDao().play(id)?.totalSeconds ?: 0) == 62L)
    }

    @Test
    fun aCoreConfirmedForAnotherRetroArchBuildIsStillAsked() = runTest {
        val id = game("ra-snes9x")
        cores.confirm(snes9xCore.copy(packageName = "com.retroarch.ra32"))
        assertEquals("snes9x_libretro_android.so", (launcher.launch(id) as LaunchOutcome.Started).coreToConfirm)
    }

    @Test
    fun anEmulatorWithoutCoresHasNothingToConfirm() = runTest {
        val id = game("snes9x")
        assertNull((launcher.launch(id) as LaunchOutcome.Started).coreToConfirm)
        clock.advance(4)
        assertNull((playTime.onResume() as PlaySessionOutcome.TooShort).coreToConfirm)
    }

    @Test
    fun theCoreSurvivesPlumemuBeingKilledDuringThePlay() = runTest {
        val id = game("ra-snes9x")
        launcher.launch(id)
        // A new process: a new tracker reads the same pending session from disk.
        val afterDeath = PlayTimeTracker(
            SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()), db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(),
        )
        clock.advance(30)
        assertEquals(snes9xCore, (afterDeath.onResume() as PlaySessionOutcome.Recorded).coreToConfirm)
    }
}
