package com.plumemu.launcher

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.play.PlayClock
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row that plays another copy's file (the game menu's Other versions): the file is the copy's,
 * the emulator choice, play time, Recent and the outcome are the row's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyLaunchTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.small
    private val installed = mutableSetOf("com.ra", "com.snes9x")
    private val documents = mutableMapOf<String, DocumentState>()
    private val started = mutableListOf<Intent>()
    private val log = RecordingLog()
    private val clock = object : PlayClock {
        var now = 1_000_000L

        override fun wallMs(): Long = now

        override fun realtimeMs(): Long = now

        override fun bootCount(): Int? = 1
    }
    private lateinit var playTime: PlayTimeTracker
    private lateinit var launcher: GameLauncher

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val io = UnconfinedTestDispatcher()
        playTime = PlayTimeTracker(SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()), db.playDao(), db.gameDao(), clock, log, io)
        launcher = GameLauncher(
            catalog,
            db.gameDao(),
            EmulatorChoices(db.emulatorChoiceDao(), catalog, { it.packageName in installed }, log, io),
            { uri -> documents[uri] ?: DocumentState.Missing },
            { _, _ -> error("only PS Vita emulators read a game file") },
            { intent -> started.add(intent) },
            playTime,
            log,
            io,
            storageRoots = OWNER_STORAGE,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun copy(file: String, present: Boolean = true): Long {
        val documentId = "primary:ROMs/snes/$file"
        val id = db.addGame("snes", documentId)
        if (present) documents[documentUri(documentId)] = DocumentState.Present("application/octet-stream")
        return id
    }

    private fun documentUri(documentId: String): String =
        android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(INTERNAL_TREE), documentId).toString()

    @Test
    fun theCopysFileStartsAndThePlayTimeGoesToTheRowLaunchedFrom() = runTest {
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "ra-snes9x")
        val shown = copy("Zork (USA).sfc")
        val chosen = copy("Zork (Europe).sfc")
        // The shown row's own emulator choice applies to the copy it plays.
        db.emulatorChoiceDao().setPersonGameChoice(shown, "snes", "snes9x")

        val outcome = launcher.launch(shown, fileOf = chosen)

        assertEquals(LaunchOutcome.Started(shown, "snes9x", "com.snes9x", ChoiceSource.PersonForGame, null), outcome)
        assertEquals(documentUri("primary:ROMs/snes/Zork (Europe).sfc"), started.single().getStringExtra("uri"))
        clock.now += 120_000
        assertEquals(PlaySessionOutcome.Recorded(shown, 120, "snes9x"), playTime.onResume())
        assertEquals(120L, db.playDao().play(shown)?.totalSeconds)
        assertNull("nothing is recorded on the copy", db.playDao().play(chosen))
    }

    @Test
    fun aMissingCopyIsTheRowsMissingFileAndAGoneRowIsNotInTheLibrary() = runTest {
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "ra-snes9x")
        val shown = copy("Zork (USA).sfc")
        val gone = copy("Zork (Japan).sfc", present = false)
        assertEquals(LaunchOutcome.FileMissing(shown, "Zork (Japan).sfc", FileProblem.Gone), launcher.launch(shown, fileOf = gone))
        assertEquals(LaunchOutcome.Failed(99_999, FailureReason.GameNotInLibrary), launcher.launch(99_999, fileOf = shown))
        assertEquals(LaunchOutcome.Failed(shown, FailureReason.GameNotInLibrary), launcher.launch(shown, fileOf = 99_999))
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
        assertEquals(true, log.lines.any { it == "I launch: game $shown opens the file of its copy $gone" })
    }
}
