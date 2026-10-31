package com.plumemu.launcher

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import com.plumemu.launcher.play.SystemPlayClock
import com.plumemu.platforms.VitaTitleId
import java.io.FileNotFoundException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PS Vita games with the shipped platform data: the `.psvita` file holds the title ID (ES-DE's
 * convention) and Vita3K gets it as `AppStartParameters` = ["-r", titleId].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VitaLaunchTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.shipped
    private val installed = mutableSetOf<String>()
    private val documents = mutableMapOf<String, DocumentState>()
    private val files = mutableMapOf<String, SmallFileRead>()
    private val reads = mutableListOf<Pair<String, Int>>()
    private val started = mutableListOf<Intent>()
    private val log = RecordingLog()
    private var games = 0
    private lateinit var playTime: PlayTimeTracker
    private lateinit var launcher: GameLauncher

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        val io = UnconfinedTestDispatcher()
        playTime = PlayTimeTracker(
            SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
            db.playDao(), db.gameDao(),
            SystemPlayClock(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver), log, io,
        )
        launcher = GameLauncher(
            catalog,
            db.gameDao(),
            EmulatorChoices(db.emulatorChoiceDao(), catalog, { it.packageName in installed }, log, io),
            { uri -> documents[uri] ?: DocumentState.Missing },
            { uri, maxBytes ->
                reads += uri to maxBytes
                files[uri] ?: SmallFileRead.Unreadable(FileNotFoundException("no such test file"))
            },
            { intent -> started.add(intent) },
            playTime,
            log,
            io,
            storageRoots = OWNER_STORAGE,
        )
    }

    @After
    fun tearDown() {
        // No line may carry a file name, a path or the title ID's file content.
        for (line in log.lines) {
            assertFalse(line, line.contains("Secret") || line.contains("/storage") || line.contains("ROMs"))
        }
        db.close()
    }

    /** A PS Vita game whose file holds [content] (null: nothing registered, the read fails). */
    private suspend fun vitaGame(content: String?, complete: Boolean = true, present: Boolean = true): Pair<Long, String> {
        installed += "org.vita3k.emulator"
        // A folder per game, so one test can add several games with the same file name.
        val documentId = "primary:ROMs/psvita/${++games}/Secret Game.psvita"
        val id = db.addGame("psvita", documentId)
        db.emulatorChoiceDao().setPersonSystemChoice("psvita", "vita3k")
        val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(INTERNAL_TREE), documentId).toString()
        if (present) documents[uri] = DocumentState.Present("application/octet-stream")
        if (content != null) files[uri] = SmallFileRead.Read(content.toByteArray(Charsets.UTF_8), complete)
        return id to uri
    }

    @Test
    fun vita3kStartsWithTheTitleIdFromTheFileAndNothingElse() = runTest {
        val (id, uri) = vitaGame("PCSE00000\r\n")
        assertEquals(LaunchOutcome.Started(id, "vita3k", "org.vita3k.emulator", ChoiceSource.PersonForSystem, null), launcher.launch(id))
        assertEquals(listOf(uri to VitaTitleId.MAX_FILE_BYTES), reads)
        val intent = started.single()
        assertEquals(ComponentName("org.vita3k.emulator", "org.vita3k.emulator.Emulator"), intent.component)
        assertNull(intent.action)
        assertNull(intent.categories)
        assertNull(intent.data)
        assertNull(intent.type)
        assertEquals(setOf("AppStartParameters"), intent.extras?.keySet())
        assertArrayEquals(arrayOf("-r", "PCSE00000"), intent.getStringArrayExtra("AppStartParameters"))
        // The game is inside Vita3K: no file and no grant are passed on.
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertNull(intent.clipData)
        assertTrue(playTime.onResume() is PlaySessionOutcome.TooShort)
    }

    @Test
    fun anEmptyFileIsSaidPlainlyAndNothingStarts() = runTest {
        for (content in listOf("", "  \n", Char(0xFEFF).toString())) {
            started.clear()
            val (id, _) = vitaGame(content)
            assertEquals(content, LaunchOutcome.BadTitleIdFile(id, "Secret Game.psvita", TitleIdProblem.Empty), launcher.launch(id))
            assertTrue(started.isEmpty())
            assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
        }
        assertTrue(log.lines.any { it == "W launch: game 1: the file holds no PS Vita title ID (Empty)" })
    }

    @Test
    fun aFileThatIsNotATitleIdIsRefused() = runTest {
        for (content in listOf("pcse00000", "PCSE0000", "Persona 4 Golden", "PCSE00000\nPCSE00001")) {
            val (id, _) = vitaGame(content)
            assertEquals(content, LaunchOutcome.BadTitleIdFile(id, "Secret Game.psvita", TitleIdProblem.NotATitleId), launcher.launch(id))
        }
        // More bytes than a title ID file has (a game image renamed): refused even if it starts like one.
        val (id, _) = vitaGame("PCSE00000", complete = false)
        assertEquals(LaunchOutcome.BadTitleIdFile(id, "Secret Game.psvita", TitleIdProblem.NotATitleId), launcher.launch(id))
        assertTrue(started.isEmpty())
        assertTrue(reads.all { it.second == VitaTitleId.MAX_FILE_BYTES })
    }

    @Test
    fun aMissingFileIsNotReadAndAnUnreadableOneIsAFolderProblem() = runTest {
        val (gone, _) = vitaGame("PCSE00000", present = false)
        assertEquals(LaunchOutcome.FileMissing(gone, "Secret Game.psvita", FileProblem.Gone), launcher.launch(gone))
        assertTrue(reads.isEmpty())
        val (unreadable, _) = vitaGame(null)
        assertEquals(LaunchOutcome.FileMissing(unreadable, "Secret Game.psvita", FileProblem.FolderUnreachable), launcher.launch(unreadable))
        assertTrue(log.lines.any { it.contains("reading the game file failed (java.io.FileNotFoundException)") })
        assertTrue(started.isEmpty())
    }

    @Test
    fun aUtf16FileFromNotepadStartsTheGame() = runTest {
        val (id, uri) = vitaGame("")
        files[uri] = SmallFileRead.Read(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "PCSE00000\r\n".toByteArray(Charsets.UTF_16LE), true)
        assertTrue(launcher.launch(id) is LaunchOutcome.Started)
        assertArrayEquals(arrayOf("-r", "PCSE00000"), started.single().getStringArrayExtra("AppStartParameters"))
    }

    @Test
    fun otherEmulatorsNeverReadTheGameFile() = runTest {
        installed += "com.retroarch.aarch64"
        val documentId = "primary:ROMs/snes/Secret Game.sfc"
        val id = db.addGame("snes", documentId)
        db.emulatorChoiceDao().setPersonSystemChoice("snes", "retroarch-snes9x")
        val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(android.net.Uri.parse(INTERNAL_TREE), documentId).toString()
        documents[uri] = DocumentState.Present(null)
        assertTrue(launcher.launch(id) is LaunchOutcome.Started)
        assertTrue(reads.isEmpty())
    }
}
