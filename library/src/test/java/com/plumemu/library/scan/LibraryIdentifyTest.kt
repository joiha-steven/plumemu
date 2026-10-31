package com.plumemu.library.scan

import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.IdName
import com.plumemu.coredata.db.FixKeys
import com.plumemu.identify.LaunchFileSystems
import com.plumemu.library.TestRules
import com.plumemu.library.naming.NAMING_RULES_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * The scan names games with the identify module, over a small tree of generated files:
 * a GBA cartridge header with a game code, a zipped SNES ROM whose CRC a pack lists, a file whose
 * name matches a pack title, one nobody knows, an arcade set and an arcade BIOS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryIdentifyTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private lateinit var naming: GameNaming

    @Before
    fun setUp() {
        ShadowLog.clear()
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
        naming = TestNaming.naming(temp.newFolder("packs"))
        write("gba/Zork Quest 2 (USA).gba", TestNaming.gbaRom())
        write("snes/zq.zip", TestNaming.zip("zq.sfc", TestNaming.ZIPPED_ROM))
        write("snes/Moonlit Harbour (Japan).sfc", ByteArray(64))
        write("snes/Unknown Thing (Europe).sfc", ByteArray(64))
        write("arcade/zorkfght.zip", TestNaming.zip("zf.rom", ByteArray(16)))
        write("arcade/testbios.zip", TestNaming.zip("bios.rom", ByteArray(16)))
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, bytes: ByteArray, time: Long = FILE_TIME) =
        File(fx.roms, path).apply { parentFile?.mkdirs(); writeBytes(bytes); setLastModified(time) }

    private suspend fun scan(): List<ScanEvent> {
        fx.tree.opened.clear()
        return fx.scanner(naming = naming).scan().toList()
    }

    @Test
    fun firstScanNamesGamesFromHeadersZipsPacksAndNames() = runTest {
        val events = scan()
        assertTrue("the walk reports first, names follow", events.indexOfFirst { it is ScanEvent.Named } > events.indexOfLast { it is ScanEvent.FolderFinished })

        val gba = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Zork Quest II", gba.title)
        assertEquals("IDENTIFIER_EXACT", gba.nameTrust)
        assertEquals("PRODUCT_CODE", gba.idMethod)
        assertEquals("USA", gba.regions)
        assertEquals("Zork Quest 2", gba.fileTitle)
        assertEquals(listOf("code:GBA:ZZZE"), FixKeys.split(gba.fixKeys))

        val zipped = fx.game("zq.zip")
        assertEquals("Zork Quest", zipped.title)
        assertEquals("CRC", zipped.idMethod)
        assertEquals("USA,EUR", zipped.regions)
        assertEquals("zork quest", zipped.sortName)

        val matched = fx.game("Moonlit Harbour (Japan).sfc")
        assertEquals("Moonlit Harbor", matched.title)
        assertEquals("IDENTIFIER_LIKELY", matched.nameTrust)
        assertEquals("JPN", matched.regions)

        val unknown = fx.game("Unknown Thing (Europe).sfc")
        assertEquals("Unknown Thing", unknown.title)
        assertEquals(NameTrustNames.FILE_NAME, unknown.nameTrust)
        assertEquals("FILE_NAME", unknown.idMethod)
        assertEquals("EUR", unknown.regions)

        assertEquals("Zork Fighters", fx.game("zorkfght.zip").title)
        assertEquals("ARCADE_NAME", fx.game("zorkfght.zip").idMethod)
        assertTrue(fx.game("testbios.zip").isNotGame)
        assertEquals(listOf("Zork Fighters"), fx.db.gameDao().observeSystemGames("arcade").first().map { it.title })
        assertEquals(listOf("Unknown Thing"), fx.db.identifyDao().observeUnidentified(LaunchFileSystems.IDS).first().map { it.title })
        assertFalse(fx.games().any { it.needsIdentify })
        // Arcade sets are named from the short name: never opened.
        assertEquals(4, fx.tree.opened.size)
    }

    /**
     * Scrape rules 3: a cartridge whose crc+serial lookup stored another system's game (a 3DS answer
     * for "ZZZE") gets its file's name at the repair, then the pack's name by its product code at the
     * scan that follows, without the file being read again.
     */
    @Test
    fun aGameWhoseWrongSystemMatchIsForgottenIsNamedByThePackAgain() = runTest {
        scan()
        val id = fx.game("Zork Quest 2 (USA).gba").id
        fx.db.scrapedGameDao().insertOrReplace(
            ScrapedGameEntity(
                scrapeKey = "code:GBA:ZZZE", gameId = null, outcome = ScrapeOutcome.MATCHED, screenScraperId = "900",
                matchedBy = ScrapeMatchedBy.HASH, scrapedAt = 1, detailsJson = GameDetails(system = IdName("17", "3DS")).toJson(),
                title = "Fish Eyes 3D", year = null, publisher = null, players = null, genre = null,
            ),
        )
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET title = 'Fish Eyes 3D', name_trust = 'SCRAPER_EXACT' WHERE id = $id")

        assertEquals(1, fx.scanner(naming = naming).askAgainForNewScrapeRules(2))
        assertEquals("the file's name at once", "Zork Quest 2", fx.game("Zork Quest 2 (USA).gba").title)

        scan()
        val named = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Zork Quest II", named.title)
        assertEquals("IDENTIFIER_EXACT", named.nameTrust)
        assertEquals("PRODUCT_CODE", named.idMethod)
        assertTrue("named from the cached fingerprint", fx.tree.opened.isEmpty())
    }

    @Test
    fun aSteamFileIsASteamGameNamedFromItsFileAndNeverOpened() = runTest {
        // GameNative's "Export for frontend" writes the app id; the person may also write it by hand.
        write("steam/Broforce.steam", "274190\n".toByteArray())
        write("Portal 2.steam", "620".toByteArray())
        // In a Windows folder too: windows does not claim .steam, and WinNative ties the two systems.
        write("windows/Half-Life.steam", "70".toByteArray())
        // The shipped rules: steam's folder and extension (windows lists .steam too).
        val shipped = com.plumemu.library.corpus.RealData.systemRules()
        suspend fun scanShipped() {
            fx.tree.opened.clear()
            fx.scanner(naming = naming, rules = shipped).scan().toList()
        }
        scanShipped()
        for ((file, title) in listOf("Broforce.steam" to "Broforce", "Portal 2.steam" to "Portal 2", "Half-Life.steam" to "Half-Life")) {
            val game = fx.game(file)
            assertEquals(file, "steam", game.systemId)
            assertEquals(file, title, game.title)
            assertEquals(file, NameTrustNames.FILE_NAME, game.nameTrust)
            assertEquals(file, "FILE_NAME", game.idMethod)
            assertFalse(file, game.needsIdentify)
        }
        // No DAT for Steam and nothing to hash: never opened, and the scan says why once.
        assertFalse(fx.tree.opened.toString(), fx.tree.opened.any { it.endsWith(".steam") })
        val lines = ShadowLog.getLogs().map { it.msg }
        assertEquals(lines.toString(), 1, lines.count { it.contains("games of steam are named from their file names, never opened") })
        // Named by design, not "unidentified" (Settings › Library › Unidentified games).
        val unidentified = fx.db.identifyDao().observeUnidentified(LaunchFileSystems.IDS).first().map { it.title }
        assertFalse(unidentified.toString(), unidentified.any { it in setOf("Broforce", "Portal 2", "Half-Life") })
        scanShipped()
        assertEquals(emptyList<String>(), fx.tree.opened)
    }

    @Test
    fun aSteamFileListedAsWindowsByOldDataBecomesSteamKeepingItsRowFavoriteAndPlays() = runTest {
        write("windows/Half-Life.steam", "70".toByteArray())
        val shipped = com.plumemu.library.corpus.RealData.systemRules()
        // Before Steam step 1, ES-DE's windows listed .steam: the file was a Windows game.
        val old = shipped.map { if (it.id == "windows") it.copy(extensions = it.extensions + "steam") else it }
        fx.scanner(naming = naming, rules = old).scan().toList()
        val before = fx.game("Half-Life.steam")
        assertEquals("windows", before.systemId)
        fx.db.gameDao().setFavorite(before.id, true)
        fx.db.playDao().addSession(before.id, endedAt = 5_000, seconds = 600)

        // The app lists every folder once when FOLDER_MATCH_VERSION rises (6 for this change).
        fx.scanner(naming = naming, rules = shipped).rescanEverything().toList()
        val after = fx.game("Half-Life.steam")
        assertEquals("the same row", before.id, after.id)
        assertEquals("steam", after.systemId)
        assertTrue(after.isFavorite)
        val play = fx.db.playDao().play(after.id)!!
        assertEquals(600L, play.totalSeconds)
        assertEquals(5_000L, play.lastPlayedAt)
        assertEquals(1, fx.games().count { it.fileName == "Half-Life.steam" })
    }

    @Test
    fun rescanWithNothingChangedOpensNoFile() = runTest {
        scan()
        val before = fx.games()
        scan()
        assertEquals(emptyList<String>(), fx.tree.opened)
        assertEquals(before, fx.games())
    }

    @Test
    fun aChangedFileIsReadAgainAndTheOthersComeFromTheCache() = runTest {
        scan()
        write("snes/zq.zip", TestNaming.zip("zq.sfc", ByteArray(4096)), time = FILE_TIME + 60_000)
        fx.touch("snes")
        scan()
        assertEquals(listOf("fs:snes/zq.zip"), fx.tree.opened)
        val changed = fx.game("zq.zip")
        // The file-name fallback is title-cased.
        assertEquals("the new content is not in the pack", "Zq", changed.title)
        assertEquals(NameTrustNames.FILE_NAME, changed.nameTrust)
    }

    @Test
    fun aMovedFileKeepsItsRowMarksAndNameWithoutBeingRead() = runTest {
        scan()
        val before = fx.game("Zork Quest 2 (USA).gba")
        fx.db.gameDao().setFavorite(before.id, true)
        fx.db.playDao().addSession(before.id, endedAt = 5, seconds = 120)
        fx.db.emulatorChoiceDao().setPersonGameChoice(before.id, "gba", "mgba")
        val target = File(fx.roms, "gba/moved/Zork Quest 2 (USA).gba").also { it.parentFile?.mkdirs() }
        File(fx.roms, "gba/Zork Quest 2 (USA).gba").renameTo(target)
        target.setLastModified(FILE_TIME)
        fx.touch("gba")
        // While the scan runs, the moved file's new row is on the lists: the person fixes its name
        // there (the row has no keys yet, so the fix goes under its row key).
        var fixedNewRow = false
        fx.tree.beforeQuery = { documentId ->
            if (!fixedNewRow && documentId == fx.tree.rootDocumentId) {
                runBlocking {
                    val newRow = fx.games().firstOrNull { it.isPending && it.relativePath == "gba/moved/Zork Quest 2 (USA).gba" }
                    if (newRow != null) {
                        fixedNewRow = fx.db.identifyDao().setFix(newRow.id, NameFixKind.OWN_NAME, "Fixed While Scanning", "fixed while scanning", null, 1)
                    }
                }
            }
        }
        scan()
        fx.tree.beforeQuery = {}
        assertTrue("the fix was made on the pending row", fixedNewRow)
        val after = fx.game("Zork Quest 2 (USA).gba")
        assertEquals(before.id, after.id)
        assertEquals("gba/moved/Zork Quest 2 (USA).gba", after.relativePath)
        assertEquals("the fix made on the new row shows on the kept one", "Fixed While Scanning", after.title)
        assertEquals(NameTrustNames.PERSON_FIX, after.nameTrust)
        assertTrue(after.isFavorite)
        assertEquals(120L, fx.db.playDao().play(after.id)?.totalSeconds)
        assertEquals("mgba", fx.db.emulatorChoiceDao().gameChoice(after.id)?.emulatorId)
        assertEquals(emptyList<String>(), fx.tree.opened)
    }

    @Test
    fun aRenamedFileIsNamedAgain() = runTest {
        scan()
        File(fx.roms, "gba/Zork Quest 2 (USA).gba").renameTo(File(fx.roms, "gba/zq2.gba"))
        fx.touch("gba")
        scan()
        val renamed = fx.game("zq2.gba")
        assertEquals("Zork Quest II", renamed.title)
        assertEquals("zq2", renamed.fileTitle)
        assertEquals(1, fx.games().count { it.systemId == "gba" })
    }

    @Test
    fun anUnreadableFileKeepsItsRowAndIsTriedAgainNextScan() = runTest {
        fx.tree.failingReads += "fs:gba/Zork Quest 2 (USA).gba"
        scan()
        val kept = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Zork Quest 2", kept.title)
        assertTrue(kept.needsIdentify)
        assertEquals("the other games are named", "Zork Quest", fx.game("zq.zip").title)
        val lines = ShadowLog.getLogs().map { it.msg }
        assertTrue(lines.any { it.contains("game ${kept.id} not read this time (ClosedChannelException)") })
        assertFalse(lines.any { it.contains("Zork") || it.contains(".gba") || it.contains("ROMs") })
        assertTrue(ShadowLog.getLogs().none { it.throwable != null })

        fx.tree.failingReads.clear()
        scan()
        assertEquals(listOf("fs:gba/Zork Quest 2 (USA).gba"), fx.tree.opened)
        assertEquals("Zork Quest II", fx.game("Zork Quest 2 (USA).gba").title)
    }

    @Test
    fun accessRevokedDuringThePassStopsItAndKeepsEveryRow() = runTest {
        fx.scanner().scan().toList()
        fx.tree.isPermissionRevoked = true
        // The walk now finds the folder unavailable; the pass for it does not run.
        val events = scan()
        assertTrue(events.none { it is ScanEvent.Named })
        assertEquals(6, fx.games().size)
        fx.tree.isPermissionRevoked = false
        var opens = 0
        fx.tree.beforeQuery = { if (it.startsWith("fs:snes/")) fx.tree.isPermissionRevoked = ++opens > 1 }
        scan()
        fx.tree.beforeQuery = {}
        fx.tree.isPermissionRevoked = false
        assertEquals(6, fx.games().size)
        assertTrue("some rows are left for the next scan", fx.games().any { it.needsIdentify })
        assertTrue(ShadowLog.getLogs().any { it.msg.contains("naming stopped (access revoked)") })
    }

    @Test
    fun theWalkAloneLeavesFileNames() = runTest {
        fx.scanner().scan().toList()
        assertEquals("zorkfght", fx.game("zorkfght.zip").title)
        assertTrue(fx.games().all { it.needsIdentify && it.nameTrust == NameTrustNames.FILE_NAME })
        assertNotNull(fx.db.identifyDao().gamesToIdentify(fx.folderId, 0, 10).firstOrNull())
    }

    @Test
    fun aSystemWhosePackCannotBeInstalledIsNamedByALaterScan() = runTest {
        fx.scanner(naming = TestNaming.naming(temp.newFolder("broken"), snesBroken = true)).scan().toList()
        val zipped = fx.game("zq.zip")
        assertEquals("Zq", zipped.title)
        assertTrue("left for a later scan, not stamped done", zipped.needsIdentify)
        assertEquals("other systems are named", "Zork Quest II", fx.game("Zork Quest 2 (USA).gba").title)
        assertTrue(ShadowLog.getLogs().any { it.msg.contains("name pack of snes unavailable") })

        scan() // packs that install
        assertEquals("Zork Quest", fx.game("zq.zip").title)
        assertFalse(fx.game("zq.zip").needsIdentify)
        assertEquals(1L, fx.game("zq.zip").packVersion)
    }

    @Test
    fun arcadeSetsShowTheRegionsTheirDescriptionsName() = runTest {
        write("arcade/zorkqst.zip", TestNaming.zip("zq.rom", ByteArray(16)))
        write("arcade/zorkfghtj.zip", TestNaming.zip("zj.rom", ByteArray(16)))
        scan()
        assertEquals("WLD", fx.game("zorkqst.zip").regions)
        assertEquals("a nested bracket inside the group", "JPN", fx.game("zorkfghtj.zip").regions)
        assertEquals("no region in the description", "", fx.game("zorkfght.zip").regions)
        // A person's fix keeps its name and still shows the set's region.
        val fixed = fx.game("zorkqst.zip")
        assertTrue(fx.db.identifyDao().setFix(fixed.id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1))
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 1 WHERE id = ${fixed.id}")
        scan()
        assertEquals("My Zork", fx.game("zorkqst.zip").title)
        assertEquals(NameTrustNames.PERSON_FIX, fx.game("zorkqst.zip").nameTrust)
        assertEquals("WLD", fx.game("zorkqst.zip").regions)
    }

    /**
     * Naming rules version 3 reaches every system (hack names, file-name cleaning): a named row of
     * any system is marked once, while a person's fix, favourite and play time stay.
     */
    @Test
    fun anUpgradeToRulesVersion3NamesEverySystemAgainKeepingFixesAndMarks() = runTest {
        write("gba/Zork Quest 2 (USA).gba", ByteArray(64))
        write("gba/Other Thing (USA).gba", ByteArray(64))
        scan()
        val fixed = fx.game("Zork Quest 2 (USA).gba")
        val other = fx.game("Other Thing (USA).gba")
        assertTrue(fx.db.identifyDao().setFix(fixed.id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1))
        fx.db.gameDao().setFavorite(other.id, true)
        fx.db.playDao().addSession(other.id, endedAt = 5_000, seconds = 600)
        assertTrue("named rows wait for nothing", fx.games().none { it.needsIdentify })

        val scanner = fx.scanner(naming = naming)
        assertEquals("every named row is marked", fx.games().size, scanner.nameAgainForNewRules(storedVersion = 2))
        assertTrue(fx.games().all { it.needsIdentify })
        assertEquals("a library already at the current version", 0, scanner.nameAgainForNewRules(NAMING_RULES_VERSION))
        scan()

        assertTrue(fx.games().none { it.needsIdentify })
        assertEquals("My Zork", fx.game("Zork Quest 2 (USA).gba").title)
        assertEquals(NameTrustNames.PERSON_FIX, fx.game("Zork Quest 2 (USA).gba").nameTrust)
        assertTrue(fx.game("Other Thing (USA).gba").isFavorite)
        assertEquals(600L, fx.db.playDao().play(other.id)!!.totalSeconds)
    }

    /**
     * 0.0.2-alpha named arcade rows without regions, with the arcade pack the app still ships: the
     * pack version alone never names them again, so the app's upgrade step marks them once
     * ([LibraryScanner.nameAgainForNewRules]) and the next scan names them from the short name.
     */
    @Test
    fun anUpgradeNamesArcadeRowsAgainOnceKeepingFixesAndMarks() = runTest {
        write("arcade/zorkqst.zip", TestNaming.zip("zq.rom", ByteArray(16)))
        write("arcade/zorkfghtu.zip", TestNaming.zip("zu.rom", ByteArray(16)))
        scan()
        // The rows as 0.0.2-alpha stored them: named with the current pack, no regions.
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET regions = '' WHERE system_id = 'arcade'")
        val fixed = fx.game("zorkqst.zip")
        val favorite = fx.game("zorkfghtu.zip")
        val hidden = fx.game("zorkfght.zip")
        assertTrue(fx.db.identifyDao().setFix(fixed.id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1))
        fx.db.gameDao().setFavorite(favorite.id, true)
        fx.db.playDao().addSession(favorite.id, endedAt = 5_000, seconds = 600)
        fx.db.gameDao().setHidden(hidden.id, true)
        val packVersion = fx.game("zorkqst.zip").packVersion
        assertEquals("named with the pack still shipped", 1L, packVersion)
        val before = fx.games().filter { it.systemId == "arcade" }

        scan()
        assertEquals("a rescan alone leaves them as they were", "", fx.game("zorkfghtu.zip").regions)

        val scanner = fx.scanner(naming = naming)
        val marked = scanner.nameAgainForNewRules(storedVersion = 0)
        // Rules version 3 reached every system, so every row is marked, not only the arcade ones.
        assertEquals("every row, the hidden BIOS set too", fx.games().size, marked)
        assertEquals("marking twice marks nothing new", 0, scanner.nameAgainForNewRules(storedVersion = 0))
        assertEquals("nothing for a library already marked", 0, scanner.nameAgainForNewRules(NAMING_RULES_VERSION))
        scan()

        assertEquals("arcade files are never opened", emptyList<String>(), fx.tree.opened)
        assertEquals("USA", fx.game("zorkfghtu.zip").regions)
        val afterFix = fx.game("zorkqst.zip")
        assertEquals("the fix still wins", "My Zork", afterFix.title)
        assertEquals(NameTrustNames.PERSON_FIX, afterFix.nameTrust)
        assertEquals("WLD", afterFix.regions)
        assertTrue(fx.game("zorkfghtu.zip").isFavorite)
        assertEquals(600L, fx.db.playDao().play(favorite.id)!!.totalSeconds)
        assertTrue(fx.game("zorkfght.zip").isHidden)
        assertTrue("the BIOS set stays off the lists", fx.game("testbios.zip").isNotGame)
        for (row in fx.games().filter { it.systemId == "arcade" }) {
            assertFalse(row.fileName, row.needsIdentify)
            assertEquals(row.fileName, packVersion, row.packVersion)
            assertEquals(row.fileName, before.single { it.id == row.id }.title, row.title)
        }
        // Named once: the next scan names nothing.
        val stats = fx.scanner(naming = naming).scan().toList().filterIsInstance<ScanEvent.Named>()
        assertEquals(emptyList<ScanEvent.Named>(), stats)
    }

    @Test
    fun anArcadeFileWithALineBreakInItsNameGetsNoKeyAndIsFixedUnderItsRow() = runTest {
        write("arcade/sf2\nce.zip", TestNaming.zip("x.rom", ByteArray(16)))
        scan()
        val odd = fx.games().single { it.fileName == "sf2\nce.zip" }
        assertEquals("", odd.fixKeys)
        assertTrue(fx.db.identifyDao().setFix(odd.id, NameFixKind.OWN_NAME, "Odd", "odd", null, fixedAt = 1))
        assertEquals(listOf("game:${odd.id}"), fx.db.identifyDao().fixesByKeys(listOf("game:${odd.id}")).map { it.fixKey })
        scan()
        assertEquals("Odd", fx.games().single { it.id == odd.id }.title)
    }

    @Test
    fun aPackThatFailsItsLookupsLeavesItsGamesPending() = runTest {
        fx.scanner(naming = TestNaming.naming(temp.newFolder("corrupt"), snesCorruptAtLookup = true)).scan().toList()
        for (name in listOf("zq.zip", "Moonlit Harbour (Japan).sfc", "Unknown Thing (Europe).sfc")) {
            val row = fx.game(name)
            assertTrue("$name left for a later scan", row.needsIdentify)
            assertEquals(NameTrustNames.FILE_NAME, row.nameTrust)
            assertEquals(null, row.packVersion)
        }
        assertEquals("Zork Quest II", fx.game("Zork Quest 2 (USA).gba").title)

        scan() // a pack that works
        assertEquals("Zork Quest", fx.game("zq.zip").title)
        assertFalse(fx.game("zq.zip").needsIdentify)
    }

    @Test
    fun aNewerPackNamesItsSystemAgainWithoutOpeningFiles() = runTest {
        scan()
        val gba = fx.game("Zork Quest 2 (USA).gba")
        fx.tree.opened.clear()
        fx.scanner(naming = TestNaming.naming(temp.newFolder("v2"), snesVersion = 2)).scan().toList()
        assertEquals(emptyList<String>(), fx.tree.opened)
        assertEquals(TestNaming.ZIPPED_TITLE_V2, fx.game("zq.zip").title)
        assertEquals(2L, fx.game("zq.zip").packVersion)
        assertEquals("a system whose pack did not change is left alone", gba, fx.game("Zork Quest 2 (USA).gba"))
    }

    @Test
    fun switchFilesAreNamedByTheTitleIdTheyCarry() = runTest {
        // A renamed NSP whose ticket names the game, and an empty update named only by its file name.
        write("switch/netta.nsp", TestNaming.nsp(TestNaming.SWITCH_BASE_ID))
        write("switch/zs [0100ABCD00010800][v65536][UPD].nsp", ByteArray(0))
        write("switch/Unknown Thing.nsp", ByteArray(0))
        val rules = TestRules.ALL + TestRules.SWITCH
        fx.scanner(naming = naming, rules = rules).scan().toList()
        val renamed = fx.game("netta.nsp")
        assertEquals("Zork Saga Deluxe", renamed.title)
        assertEquals("PRODUCT_CODE", renamed.idMethod)
        assertEquals("IDENTIFIER_EXACT", renamed.nameTrust)
        assertEquals(listOf("code:SWITCH:${TestNaming.SWITCH_BASE_ID}"), FixKeys.split(renamed.fixKeys))
        val update = fx.game("zs [0100ABCD00010800][v65536][UPD].nsp")
        assertEquals("the update is named after its game", "Zork Saga Deluxe", update.title)
        assertEquals("Unknown Thing", fx.game("Unknown Thing.nsp").title)
        // The ids are cached: a rescan with nothing changed reads nothing and keeps the names.
        val before = fx.games()
        fx.tree.opened.clear()
        fx.scanner(naming = naming, rules = rules).scan().toList()
        assertEquals(emptyList<String>(), fx.tree.opened)
        assertEquals(before, fx.games())
    }

    @Test
    fun aSwitchFileCachedByTheOlderReaderIsReadAgainOnce() = runTest {
        write("switch/netta.nsp", TestNaming.nsp(TestNaming.SWITCH_BASE_ID))
        val rules = TestRules.ALL + TestRules.SWITCH
        fx.scanner(naming = naming, rules = rules).scan().toList()
        val row = fx.game("netta.nsp")
        // As an older scan stored it: no title id, no reader version, named from the file.
        val old = """{"v":1,"size":${row.sizeBytes},"container":"NONE"}"""
        fx.db.openHelper.writableDatabase.execSQL(
            "UPDATE game SET fingerprint = ?, needs_identify = 1, title = 'Netta' WHERE id = ?",
            arrayOf<Any>(old, row.id),
        )
        fx.tree.opened.clear()
        fx.scanner(naming = naming, rules = rules).scan().toList()
        assertEquals("read again once", listOf("fs:switch/netta.nsp"), fx.tree.opened)
        assertEquals("Zork Saga Deluxe", fx.game("netta.nsp").title)
        fx.db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 1 WHERE id = ?", arrayOf<Any>(row.id))
        fx.tree.opened.clear()
        fx.scanner(naming = naming, rules = rules).scan().toList()
        assertEquals("then cached again", emptyList<String>(), fx.tree.opened)
        assertEquals("Zork Saga Deluxe", fx.game("netta.nsp").title)
    }

    private companion object {
        const val FILE_TIME = 1_600_000_000_000L
    }
}
