package com.plumemu.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.IdentifyDao
import com.plumemu.coredata.db.NewPinnedShortcut
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AppGraph
import com.plumemu.telemetry.LaunchFrom
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Other versions on the real graph: what the menu's facts hold, a choice, and the launch opening
 * the chosen copy's file for the row launched from. Made-up titles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppGameActionsVersionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = AppGraph(context, log = {})
    private val actions = AppGameActions(graph, context)

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    private fun addGames(vararg files: Pair<String, String>, method: String? = null, systemId: String = "snes"): List<Long> = runBlocking {
        val scan = graph.database.scanDao()
        val folder = scan.folderByTreeUri(TREE)?.id ?: scan.insertFolder(RomFolderEntity(treeUri = TREE, displayName = "ROMs", addedAt = 0))
        val games = files.map { (file, regions) -> gameRow(folder, file, regions, method, systemId) }
        scan.applyBatch(ScanBatch(folder, listOf(SystemEntity(systemId, systemId)), games))
        val rows = scan.gamesInFolder(folder)
        files.map { (file, _) -> rows.single { it.fileName == file }.id }
    }

    @Test
    fun aRowOfTwoRegionalCopiesListsBothAndTheChosenOneIsCurrentAndLaunched() = runBlocking {
        val (usa, eur) = addGames("Zork Quest (USA).sfc" to "USA", "Zork Quest (Europe).sfc" to "EUR")
        addGames("Zork Quest (USA) (Rev 1).sfc" to "USA")

        val facts = requireNotNull(actions.facts(usa.toString()))
        val versions = requireNotNull(facts.versions)
        assertFalse(facts.versionsPending)
        assertEquals(listOf(UiText.Plain("Europe"), UiText.Plain("USA")), versions.versions.map { it.label })
        assertEquals(usa.toString(), versions.current?.key)

        assertTrue(actions.chooseVersion(usa.toString(), eur.toString()))
        assertEquals(eur.toString(), actions.facts(usa.toString())?.versions?.current?.key)
        assertFalse("a revision is another game", actions.chooseVersion(usa.toString(), rev1Of().toString()))

        ShadowLog.clear()
        actions.launch(usa.toString(), LaunchFrom.List)
        assertTrue(
            "the launch opens the chosen copy's file for the row",
            ShadowLog.getLogs().any { it.msg == "launch: game $usa opens the file of its copy $eur" },
        )

        assertTrue(actions.chooseVersion(usa.toString(), usa.toString()))
        assertEquals(usa.toString(), actions.facts(usa.toString())?.versions?.current?.key)
        ShadowLog.clear()
        actions.launch(usa.toString(), LaunchFrom.List)
        assertFalse(ShadowLog.getLogs().any { it.msg.contains("opens the file of its copy") })
    }

    @Test
    fun aSingleCopyAShortcutAndAnArcadeSetHaveNoVersionsInTheFacts() = runBlocking {
        val (alone) = addGames("Galfen Island (USA).sfc" to "USA")
        assertEquals(1, actions.facts(alone.toString())?.versions?.versions?.size)
        assertFalse(gameMenuEntries(requireNotNull(actions.facts(alone.toString()))).any { it.key == GameMenuEntryId.OtherVersions.name })

        val shortcut = graph.database.pinnedShortcutDao().add(
            NewPinnedShortcut("app.gamenative", "game_274190", 0, "Broforce", "broforce", "steam", "Steam", addedAt = 1),
        ).gameId
        val shortcutFacts = requireNotNull(actions.facts(shortcut.toString()))
        assertNull(shortcutFacts.versions)
        assertFalse(shortcutFacts.versionsPending)

        val (set) = addGames("dinou.zip" to "", method = IdentifyDao.ARCADE_NAME, systemId = "arcade")
        val arcadeFacts = requireNotNull(actions.facts(set.toString()))
        assertNull("an arcade set's versions are read after the menu opens", arcadeFacts.versions)
        assertTrue(arcadeFacts.versionsPending)
    }

    /**
     * A chosen copy of another file type (`Hades.steam` shown, `Hades.desktop` chosen): Play with,
     * Info's emulator and the second screen's played line name the emulator that opens the file A
     * really opens (WinNative for `.desktop`), never the shown file's (GameNative opens `.steam` only).
     */
    @Test
    fun aChosenCopyOfAnotherFileTypeNamesTheEmulatorThatReallyOpensIt() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        com.plumemu.installLauncherApp(app, "app.gamenative", "app.gamenative.MainActivity", "GameNative")
        com.plumemu.installLauncherApp(app, "com.winnative.cmod", "com.winlator.cmod.runtime.display.XServerDisplayActivity", "WinNative")
        val (steam, desktop) = addGames("Hades.steam" to "", "Hades.desktop" to "", systemId = "steam")
        graph.database.emulatorChoiceDao().setPersonSystemChoice("steam", "gamenative")
        fun EmulatorListing.ids() = (installed + notInstalled).map { it.id }

        assertEquals("GameNative", actions.facts(steam.toString())?.emulator?.name)
        assertTrue("gamenative" in requireNotNull(actions.emulators(steam.toString())).ids())
        assertFalse("winnative" in requireNotNull(actions.emulators(steam.toString())).ids())

        assertTrue(actions.chooseVersion(steam.toString(), desktop.toString()))
        assertEquals("Info names the emulator that opens the .desktop", "WinNative", actions.facts(steam.toString())?.emulator?.name)
        assertEquals("Play with lists only what opens it", listOf("winnative"), requireNotNull(actions.emulators(steam.toString())).ids())
        assertEquals("the second screen's played line too", "WinNative", graph.emulatorToStart(steam)?.name)

        assertTrue(actions.chooseVersion(steam.toString(), steam.toString()))
        assertEquals("GameNative", actions.facts(steam.toString())?.emulator?.name)
        assertEquals("GameNative", graph.emulatorToStart(steam)?.name)
    }

    /**
     * an arcade row with a set chosen in its directory. No arcade emulator opens
     * some file types only, so the menu's facts and the details column's emulator use the row's own
     * file and look no copy up: the arcade pack is not read (it is only when Other versions asks).
     */
    @Test
    fun anArcadeRowWithAChoiceNearbyReadsNoPackForItsFactsOrItsEmulator() = runBlocking {
        val (dino, dinou) = addGames("dino.zip" to "", "dinou.zip" to "", method = IdentifyDao.ARCADE_NAME, systemId = "arcade")
        graph.database.openHelper.writableDatabase.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES ($dino, 5)")
        ShadowLog.clear()

        val facts = requireNotNull(actions.facts(dinou.toString()))
        graph.emulatorToStart(dinou)

        assertTrue(facts.versionsPending)
        assertFalse("no arcade pack unpacked for the facts", packsUnpacked().any { it.startsWith("arcade") })
        assertFalse("no copy lookup reached the pack", ShadowLog.getLogs().any { it.msg.contains("arcade") })

        // Other versions itself reads the pack (proves the probe above sees a read).
        actions.versions(dinou.toString())
        assertTrue(
            "Other versions reads the arcade pack",
            packsUnpacked().any { it.startsWith("arcade") } || ShadowLog.getLogs().any { it.msg.contains("arcade pack") },
        )
    }

    /** The second screen's File line names the file Play launches: the chosen version's. */
    @Test
    fun theSecondScreensFileLineIsTheChosenVersionsFile() = runBlocking {
        val (usa, eur) = addGames("Zork Quest (USA).sfc" to "USA", "Zork Quest (Europe).sfc" to "EUR")
        suspend fun card(id: Long): String? {
            val facts = com.plumemu.secondscreen.SecondScreenPlayFacts(plays = { graph.database.playDao() }, fileNameOf = { graph.launchedFileName(it) })
            return (facts.fill(com.plumemu.secondscreen.SecondScreenContent.Game("Zork Quest", "Super Nintendo", gameId = id, isPlayKnown = false)) as com.plumemu.secondscreen.SecondScreenContent.Game).fileName
        }
        assertEquals("Zork Quest (USA).sfc", card(usa))
        assertTrue(actions.chooseVersion(usa.toString(), eur.toString()))
        assertEquals("Zork Quest (Europe).sfc", card(usa))
        assertTrue(actions.chooseVersion(usa.toString(), usa.toString()))
        assertEquals("Zork Quest (USA).sfc", card(usa))
        assertNull(graph.launchedFileName(99_999))
    }

    private fun packsUnpacked(): List<String> = java.io.File(context.noBackupFilesDir, "identify-packs").list()?.toList().orEmpty()

    /**
     * The menu's facts with versions in a directory of 5,000 games: printed for the record, and
     * the versions part compared with the facts without it (a console game's copies are one
     * indexed read, so the menu does not open later).
     */
    @Test
    fun theMenusFactsWithVersionsAmongFiveThousandGames() = runBlocking {
        val (usa) = addGames("Zork Quest (USA).sfc" to "USA", "Zork Quest (Europe).sfc" to "EUR")
        addGames(*(1..5_000).map { "Filler Game $it (USA).sfc" to "USA" }.toTypedArray())
        actions.facts(usa.toString()) // warm: catalogue, packs of the graph
        val withVersions = (1..5).map { timed { actions.facts(usa.toString()) } }
        val versionsAlone = (1..5).map { timed { com.plumemu.library.naming.copiesOf(graph.database, null, usa) } }
        println("AppGameActionsVersionsTest: facts ${withVersions.joinToString()} ms, of which versions ${versionsAlone.joinToString()} ms")
        assertTrue("versions took $versionsAlone ms", versionsAlone.min() < 50)
    }

    private suspend fun rev1Of(): Long = graph.database.scanDao().gamesInFolder(requireNotNull(graph.database.scanDao().folderByTreeUri(TREE)).id).single { it.fileName.contains("Rev 1") }.id

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun gameRow(folder: Long, file: String, regions: String, method: String?, systemId: String) =
        GameEntity(
            folderId = folder, documentId = "primary:ROMs/$systemId/$file", parentDocumentId = "primary:ROMs/$systemId",
            relativePath = "$systemId/$file", fileName = file, sizeBytes = 1, modifiedAt = 1, title = file.substringBefore(" (").substringBeforeLast('.'),
            sortName = file.substringBefore(" (").substringBeforeLast('.').lowercase(), regions = regions, discGroup = null, discCount = 1, systemId = systemId,
            isPrimaryCopy = true, idMethod = method,
        )

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3AROMs"
    }
}
