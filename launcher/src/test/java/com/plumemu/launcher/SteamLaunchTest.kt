package com.plumemu.launcher

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.play.PlaySessionOutcome
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.launcher.play.SharedPreferencesSessionStore
import com.plumemu.launcher.play.SystemPlayClock
import com.plumemu.platforms.SteamAppId
import java.io.FileNotFoundException
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

/**
 * Steam games with the shipped platform data, end to end through [GameLauncher]: a `.steam` file in
 * the library holds the Steam app id (ES-DE's convention, GameNative's "Export for frontend") and
 * each Steam app gets it in its own shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SteamLaunchTest {
    private lateinit var db: PlumDatabase
    private val catalog = TestCatalogs.shipped
    private val installed = mutableSetOf<String>()
    private val documents = mutableMapOf<String, DocumentState>()
    private val files = mutableMapOf<String, SmallFileRead>()
    private val reads = mutableListOf<Pair<String, Int>>()
    private val started = mutableListOf<Intent>()
    private val log = RecordingLog()

    /** The offer as the shipped data makes it: its page comes from `DOWNLOAD_PAGES`. */
    private fun offerOf(id: String, name: String) =
        EmulatorOffer(id, name, catalog.emulators.first { it.id == id }.page)
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
        // No line may carry a file name, a path or the file's content.
        for (line in log.lines) {
            assertFalse(line, line.contains("Broforce") || line.contains("274190") || line.contains("/storage") || line.contains("ROMs"))
        }
        db.close()
    }

    /** A Steam game whose `.steam` file holds [content] (null: nothing registered, the read fails). */
    private fun steamGame(content: String?, complete: Boolean = true, present: Boolean = true): Pair<Long, String> {
        // A folder per game, so one test can add several games with the same file name.
        val documentId = "primary:ROMs/steam/${++games}/Broforce.steam"
        val id = kotlinx.coroutines.runBlocking { db.addGame("steam", documentId) }
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(INTERNAL_TREE), documentId).toString()
        if (present) documents[uri] = DocumentState.Present("application/octet-stream")
        if (content != null) files[uri] = SmallFileRead.Read(content.toByteArray(Charsets.UTF_8), complete)
        return id to uri
    }

    private suspend fun choose(emulatorId: String) = db.emulatorChoiceDao().setPersonSystemChoice("steam", emulatorId)

    @Test
    fun gameNativeIsRecommendedThenStartsWithTheAppIdAsAnInt() = runTest {
        installed += "app.gamenative"
        val (id, uri) = steamGame("274190\n")
        // Nothing starts before a choice; the only installed Steam app is the recommendation.
        val question = launcher.launch(id) as LaunchOutcome.EmulatorNotChosen
        assertEquals("gamenative", question.recommendedId)
        assertEquals(listOf(offerOf("gamenative", "GameNative")), question.installed)
        assertTrue(started.isEmpty())

        choose("gamenative")
        assertEquals(LaunchOutcome.Started(id, "gamenative", "app.gamenative", ChoiceSource.PersonForSystem, null), launcher.launch(id))
        assertEquals(listOf(uri to SteamAppId.MAX_FILE_BYTES), reads)
        val intent = started.single()
        assertEquals(ComponentName("app.gamenative", "app.gamenative.MainActivity"), intent.component)
        assertEquals("app.gamenative.LAUNCH_GAME", intent.action)
        assertNull(intent.categories)
        assertNull(intent.data)
        assertNull(intent.type)
        assertEquals(setOf("app_id", "game_source"), intent.extras?.keySet())
        // An Int, not a String "274190": GameNative reads it with getIntExtra.
        @Suppress("DEPRECATION") // Bundle.get(String) has no typed getter that tells Int from String.
        assertEquals(274190, intent.extras?.get("app_id"))
        assertEquals("STEAM", intent.getStringExtra("game_source"))
        // The game is inside GameNative: no file and no grant are passed on.
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertNull(intent.clipData)
        assertTrue(playTime.onResume() is PlaySessionOutcome.TooShort)
    }

    /** A `.desktop` shortcut in the steam folder (WinNative's export). */
    private fun desktopGame(): Long {
        val documentId = "primary:ROMs/steam/${++games}/Broforce.desktop"
        val id = kotlinx.coroutines.runBlocking { db.addGame("steam", documentId) }
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(INTERNAL_TREE), documentId).toString()
        documents[uri] = DocumentState.Present("application/x-desktop")
        return id
    }

    private fun idsOf(pick: LaunchOutcome) = (pick as LaunchOutcome.EmulatorNotChosen).installed.map { it.emulatorId }

    @Test
    fun aSteamFileWithWinNativeAndGameNativeInstalledRecommendsGameNativeAndNeverOffersWinNative() = runTest {
        installed += setOf("app.gamenative", "com.winnative.cmod")
        val (id, _) = steamGame("274190")
        val question = launcher.launch(id) as LaunchOutcome.EmulatorNotChosen
        assertEquals("gamenative", question.recommendedId)
        assertEquals(listOf("gamenative"), idsOf(question))
        // Only WinNative installed: nothing can start a .steam file.
        installed -= "app.gamenative"
        assertEquals(LaunchOutcome.NoEmulatorForSystem(id, "steam", listOf(
            offerOf("gamenative", "GameNative"),
            offerOf("gamehub-lite", "GameHub Lite"),
            offerOf("droiddeck", "DroidDeck"),
        )), launcher.launch(id))
        assertTrue(started.isEmpty())
    }

    @Test
    fun aDesktopFileInSteamRecommendsWinNativeAndNeverOffersASteamApp() = runTest {
        installed += setOf("app.gamenative", "com.winnative.cmod")
        val id = desktopGame()
        val question = launcher.launch(id) as LaunchOutcome.EmulatorNotChosen
        assertEquals("winnative", question.recommendedId)
        assertEquals(listOf("winnative"), idsOf(question))
    }

    @Test
    fun aSystemChoiceOfWinNativeIsPassedOverForSteamFilesWithoutAskingAndStaysTheSystems() = runTest {
        // Steam's only emulator was WinNative before the Steam apps: people may have it as the system's choice.
        installed += setOf("app.gamenative", "com.winnative.cmod")
        choose("winnative")
        for (content in listOf("274190", "620")) {
            val (id, _) = steamGame(content)
            assertEquals(LaunchOutcome.Started(id, "gamenative", "app.gamenative", ChoiceSource.RecommendedForFileType, null), launcher.launch(id))
        }
        assertEquals(listOf("app.gamenative", "app.gamenative"), started.map { it.component?.packageName })
        val row = db.emulatorChoiceDao().systemChoice("steam")!!
        assertEquals("winnative", row.emulatorId)
        assertTrue(row.isPersonChoice)
        // .desktop games keep WinNative.
        val desktop = desktopGame()
        assertEquals(LaunchOutcome.Started(desktop, "winnative", "com.winnative.cmod", ChoiceSource.PersonForSystem, null), launcher.launch(desktop))
    }

    @Test
    fun aSystemChoiceOfGameNativeIsPassedOverForADesktopFileWithoutAsking() = runTest {
        installed += setOf("app.gamenative", "com.winnative.cmod")
        choose("gamenative")
        val id = desktopGame()
        assertEquals(LaunchOutcome.Started(id, "winnative", "com.winnative.cmod", ChoiceSource.RecommendedForFileType, null), launcher.launch(id))
        assertEquals("gamenative", db.emulatorChoiceDao().systemChoice("steam")?.emulatorId)
    }

    @Test
    fun aSystemChoiceThatCannotOpenTheFileWithNothingThatCanInstalledSaysSo() = runTest {
        installed += "com.winnative.cmod"
        choose("winnative")
        val (id, _) = steamGame("274190")
        assertEquals(LaunchOutcome.NoEmulatorForSystem(id, "steam", listOf(
            offerOf("gamenative", "GameNative"),
            offerOf("gamehub-lite", "GameHub Lite"),
            offerOf("droiddeck", "DroidDeck"),
        )), launcher.launch(id))
        assertTrue(started.isEmpty())
    }

    @Test
    fun aGameChoiceTheSystemNoLongerListsIsPassedOver() = runTest {
        // Half-Life.steam was a Windows game with Winlator chosen for it; it is a Steam game now.
        installed += setOf("app.gamenative", "com.winlator.cmod")
        val (id, _) = steamGame("70")
        db.emulatorChoiceDao().setPersonGameChoice(id, "windows", "winlator-cmod")
        choose("gamenative")
        assertEquals(LaunchOutcome.Started(id, "gamenative", "app.gamenative", ChoiceSource.PersonForSystem, null), launcher.launch(id))
    }

    /** An id file of GameNative's frontend export in [systemId]'s folder; [content] is what it holds. */
    private fun idFileGame(systemId: String, fileName: String, content: String): Pair<Long, String> {
        val documentId = "primary:ROMs/$systemId/${++games}/$fileName"
        val id = kotlinx.coroutines.runBlocking { db.addGame(systemId, documentId) }
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(INTERNAL_TREE), documentId).toString()
        documents[uri] = DocumentState.Present(null)
        files[uri] = SmallFileRead.Read(content.toByteArray(Charsets.UTF_8), true)
        return id to uri
    }

    @Test
    fun anEpicFileGoesToGameNativeNotWinNativeWithItsStore() = runTest {
        installed += setOf("app.gamenative", "com.winnative.cmod")
        val (id, _) = idFileGame("epic", "Fortnite.epic", "1234\n")
        val question = launcher.launch(id) as LaunchOutcome.EmulatorNotChosen
        assertEquals("gamenative", question.recommendedId)
        assertEquals(listOf("gamenative"), idsOf(question))
        db.emulatorChoiceDao().setPersonSystemChoice("epic", "gamenative")
        assertEquals(LaunchOutcome.Started(id, "gamenative", "app.gamenative", ChoiceSource.PersonForSystem, null), launcher.launch(id))
        val intent = started.single()
        assertEquals("app.gamenative.LAUNCH_GAME", intent.action)
        assertEquals(setOf("app_id", "game_source"), intent.extras?.keySet())
        @Suppress("DEPRECATION") // Bundle.get(String) has no typed getter that tells Int from String.
        assertEquals(1234, intent.extras?.get("app_id"))
        assertEquals("EPIC", intent.getStringExtra("game_source"))
        assertNull(intent.data)
    }

    @Test
    fun everyIdFileStartsWithItsOwnStoreInGameNative() = runTest {
        installed += "app.gamenative"
        db.emulatorChoiceDao().setPersonSystemChoice("windows", "gamenative")
        val wanted = listOf("Witcher.gog" to "GOG", "Game.AMAZON" to "AMAZON", "My Game.pcgame" to "CUSTOM_GAME", "Fortnite.Epic" to "EPIC")
        for ((fileName, source) in wanted) {
            val (id, _) = idFileGame("windows", fileName, "42")
            assertTrue(fileName, launcher.launch(id) is LaunchOutcome.Started)
            assertEquals(fileName, source, started.last().getStringExtra("game_source"))
        }
    }

    @Test
    fun anEpicFileWithGameNativeMissingNamesTheAppsThatCanOpenIt() = runTest {
        installed += "com.winnative.cmod"
        val (id, _) = idFileGame("epic", "Fortnite.epic", "1234")
        assertEquals(LaunchOutcome.NoEmulatorForSystem(id, "epic", listOf(offerOf("gamenative", "GameNative"))), launcher.launch(id))
        db.emulatorChoiceDao().setPersonSystemChoice("epic", "gamenative")
        assertEquals(
            LaunchOutcome.EmulatorMissing(id, offerOf("gamenative", "GameNative"), ChoiceSource.PersonForSystem),
            launcher.launch(id),
        )
        assertTrue(started.isEmpty())
    }

    @Test
    fun aGogFileIsNeverOfferedToWinNativeOrWinlator() = runTest {
        installed += setOf("com.winnative.cmod", "com.winlator.cmod", "com.winlator", "com.cmodded.winlator", "com.micewine.emu")
        val (id, _) = idFileGame("windows", "Witcher.gog", "1234")
        assertEquals(LaunchOutcome.NoEmulatorForSystem(id, "windows", listOf(offerOf("gamenative", "GameNative"))), launcher.launch(id))
        // Their own .desktop files still start there.
        val documentId = "primary:ROMs/windows/Witcher.desktop"
        val desktop = db.addGame("windows", documentId)
        documents[DocumentsContract.buildDocumentUriUsingTree(Uri.parse(INTERNAL_TREE), documentId).toString()] = DocumentState.Present(null)
        installed += "app.gamenative"
        assertEquals(listOf("winlator-cmod", "winlator-glibc", "winlator-proot", "winnative", "com-micewine-emu"), idsOf(launcher.launch(desktop)))
    }

    @Test
    fun aBadEpicFileIsRefusedLikeABadSteamFile() = runTest {
        installed += "app.gamenative"
        db.emulatorChoiceDao().setPersonSystemChoice("epic", "gamenative")
        val (id, _) = idFileGame("epic", "Fortnite.epic", "Fortnite")
        assertEquals(LaunchOutcome.BadSteamAppIdFile(id, "Fortnite.epic", SteamAppIdProblem.NotAnAppId), launcher.launch(id))
        val (empty, _) = idFileGame("epic", "Empty.epic", "")
        assertEquals(LaunchOutcome.BadSteamAppIdFile(empty, "Empty.epic", SteamAppIdProblem.Empty), launcher.launch(empty))
        assertTrue(started.isEmpty())
    }

    @Test
    fun anOldGameChoiceThatCannotOpenTheFileIsNotUsed() = runTest {
        installed += setOf("app.gamenative", "com.winnative.cmod")
        val (id, _) = steamGame("274190")
        db.emulatorChoiceDao().setPersonGameChoice(id, "steam", "winnative")
        choose("gamenative")
        assertEquals(LaunchOutcome.Started(id, "gamenative", "app.gamenative", ChoiceSource.PersonForSystem, null), launcher.launch(id))
    }

    @Test
    fun gameHubLiteGetsTheAppIdAsAStringUnderTheBuildThatIsInstalled() = runTest {
        installed += "emuready.gamehub.lite"
        choose("gamehub-lite")
        val (id, _) = steamGame("\uFEFF274190\r\n")
        assertEquals(LaunchOutcome.Started(id, "gamehub-lite", "emuready.gamehub.lite", ChoiceSource.PersonForSystem, null), launcher.launch(id))
        val intent = started.single()
        assertEquals(ComponentName("emuready.gamehub.lite", "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"), intent.component)
        assertEquals("gamehub.lite.LAUNCH_GAME", intent.action)
        assertEquals(setOf("steamAppId", "autoStartGame"), intent.extras?.keySet())
        assertEquals("274190", intent.getStringExtra("steamAppId"))
        assertTrue(intent.getBooleanExtra("autoStartGame", false))
        assertNull(intent.data)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test
    fun droidDeckGetsItsLinkWithTheAppId() = runTest {
        installed += "com.droiddeck.launcher"
        choose("droiddeck")
        val (id, _) = steamGame("274190")
        assertTrue(launcher.launch(id) is LaunchOutcome.Started)
        val intent = started.single()
        assertNull(intent.component)
        assertEquals("com.droiddeck.launcher", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("droiddeck://game/274190"), intent.data)
        assertNull(intent.type)
        assertNull(intent.extras)
        assertNull(intent.clipData)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test
    fun gameNativeNotInstalledIsTheUsualMissingEmulatorAndNothingIsRead() = runTest {
        choose("gamenative")
        val (id, _) = steamGame("274190")
        assertEquals(
            LaunchOutcome.EmulatorMissing(id, offerOf("gamenative", "GameNative"), ChoiceSource.PersonForSystem),
            launcher.launch(id),
        )
        assertTrue(reads.isEmpty())
        assertTrue(started.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
    }

    @Test
    fun anEmptyFileIsSaidPlainlyAndNothingStarts() = runTest {
        installed += "app.gamenative"
        choose("gamenative")
        for (content in listOf("", "  \n", "\uFEFF")) {
            val (id, _) = steamGame(content)
            assertEquals(content, LaunchOutcome.BadSteamAppIdFile(id, "Broforce.steam", SteamAppIdProblem.Empty), launcher.launch(id))
        }
        assertTrue(started.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, playTime.onResume())
        assertTrue(log.lines.toString(), log.lines.any { it == "W launch: game 1: the file holds no game id (Empty)" })
        assertTrue(log.lines.toString(), log.lines.any { it == "W launch: game 1: the id file holds no game id (Empty, 0 bytes)" })
    }

    @Test
    fun aFileThatIsNotAnAppIdIsRefusedAndALargeOneIsNotReadWhole() = runTest {
        installed += "app.gamenative"
        choose("gamenative")
        for (content in listOf("Broforce", "0", "-1", "274 190", "274190\n620", "2147483648")) {
            val (id, _) = steamGame(content)
            assertEquals(content, LaunchOutcome.BadSteamAppIdFile(id, "Broforce.steam", SteamAppIdProblem.NotAnAppId), launcher.launch(id))
        }
        // More bytes than an id file has (a game image renamed): refused even if it starts with digits.
        val (big, _) = steamGame("274190", complete = false)
        assertEquals(LaunchOutcome.BadSteamAppIdFile(big, "Broforce.steam", SteamAppIdProblem.NotAnAppId), launcher.launch(big))
        assertTrue(log.lines.toString(), log.lines.any { it.endsWith("holds no game id (NotAnAppId, over ${SteamAppId.MAX_FILE_BYTES} bytes)") })
        assertTrue(started.isEmpty())
        assertTrue(reads.all { it.second == SteamAppId.MAX_FILE_BYTES })
    }

    @Test
    fun aMissingFileIsNotReadAndAnUnreadableOneIsAFolderProblem() = runTest {
        installed += "app.gamenative"
        choose("gamenative")
        val (gone, _) = steamGame("274190", present = false)
        assertEquals(LaunchOutcome.FileMissing(gone, "Broforce.steam", FileProblem.Gone), launcher.launch(gone))
        assertTrue(reads.isEmpty())
        val (unreadable, _) = steamGame(null)
        assertEquals(LaunchOutcome.FileMissing(unreadable, "Broforce.steam", FileProblem.FolderUnreachable), launcher.launch(unreadable))
        assertTrue(started.isEmpty())
    }
}
