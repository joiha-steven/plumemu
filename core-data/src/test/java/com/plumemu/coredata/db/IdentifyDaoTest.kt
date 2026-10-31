package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
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

/** Names and fixes in the database: what the pass writes, what the Fix name screens call, and scans on top. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentifyDaoTest {
    private lateinit var db: PlumDatabase
    private val scan get() = db.scanDao()
    private val dao get() = db.identifyDao()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = scan.insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(vararg documentIds: String): List<Long> {
        scan.applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), documentIds.map { game(folderId, it) }))
        scan.finishScan(ScanFinish(folderId))
        return documentIds.map { id -> scan.gamesInFolder(folderId).single { it.documentId == id }.id }
    }

    private suspend fun identify(id: Long, keys: List<String>, title: String, trust: String = "IDENTIFIER_EXACT") {
        val row = db.gameDao().game(id)!!
        assertEquals(1, dao.recordIdentification(id, row.sizeBytes, row.modifiedAt, row.fileName, "fp", row.sizeBytes, row.modifiedAt, FixKeys.join(keys), "CRC", "EXACT", packVersion = 1))
        dao.setShownName(id, title, title.lowercase(), "USA", trust, isNotGame = false)
    }

    private suspend fun row(id: Long) = db.gameDao().game(id)!!

    @Test
    fun newRowsWaitForThePassAndANamedRowDoesNot() = runTest {
        val (a, b) = insert("a.sfc", "b.sfc")
        assertEquals(listOf(a, b), dao.gamesToIdentify(folderId, 0, 10).map { it.id })
        identify(a, listOf("crc:0000abcd:1024"), "Zork Quest")
        assertEquals(listOf(b), dao.gamesToIdentify(folderId, 0, 10).map { it.id })
        assertEquals(1, dao.countToIdentify(folderId))
        assertEquals(listOf("snes"), dao.systemsToIdentify(folderId))
    }

    @Test
    fun aResultForAFileThatChangedMeanwhileIsNotStored() = runTest {
        val (a) = insert("a.sfc")
        val written = dao.recordIdentification(a, sizeBytes = 999, modifiedAt = 1, fileName = "a.sfc", fingerprint = "fp", fingerprintSize = 999, fingerprintMtime = 1, fixKeys = "", method = "CRC", confidence = "EXACT", packVersion = 1)
        assertEquals(0, written)
        assertTrue(row(a).needsIdentify)
        assertNull(row(a).fingerprint)
    }

    @Test
    fun rescanKeepsAnIdentifiedNameAndUpdatesTheFileName() = runTest {
        val (a) = insert("a.sfc")
        identify(a, listOf("crc:0000abcd:1024"), "Zork Quest")
        val stored = scan.gamesInDirectory(folderId, "root").single()
        scan.applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(title = "zork renamed", sortName = "zork renamed", fileName = "zork renamed.sfc")))))
        val after = row(a)
        assertEquals("Zork Quest", after.title)
        assertEquals("zork renamed", after.fileTitle)
        assertTrue("a renamed file is named again", after.needsIdentify)
        assertEquals("fp", after.fingerprint)
    }

    @Test
    fun rescanOfAnUnchangedFileLeavesItNamed() = runTest {
        val (a) = insert("a.sfc")
        identify(a, emptyList(), "a.sfc", trust = NameTrustNames.FILE_NAME)
        val stored = scan.gamesInDirectory(folderId, "root").single()
        scan.applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(isPrimaryCopy = false)))))
        assertFalse(row(a).needsIdentify)
    }

    @Test
    fun rescanUpdatesTheShownNameWhileOnlyTheFileNameIsKnown() = runTest {
        val (a) = insert("a.sfc")
        val stored = scan.gamesInDirectory(folderId, "root").single()
        scan.applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(title = "Better", sortName = "better")))))
        assertEquals("Better", row(a).title)
        assertEquals("better", row(a).sortName)
    }

    @Test
    fun aFixIsStoredUnderTheFilesContentKeysAndItsRowNotASharedCode() = runTest {
        // A fix belongs to the file's content; a product code is shared by revisions.
        val (a, b, c) = insert("a.sfc", "b.sfc", "c.sfc")
        identify(a, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "Zork Quest")
        assertTrue(dao.setFix(a, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 7))
        assertEquals(listOf("crc:0000abcd:1024", NameFixEntity.rowKey(a)), dao.fixesByKeys(listOf("crc:0000abcd:1024", "code:SNES:ZZZE", NameFixEntity.rowKey(a))).map { it.fixKey }.sorted())
        assertNull("content keys are not tied to the row", dao.fixesByKeys(listOf("crc:0000abcd:1024")).single().gameId)
        assertEquals(a, dao.fixesByKeys(listOf(NameFixEntity.rowKey(a))).single().gameId)
        assertEquals("My Zork", row(a).title)
        assertEquals(NameTrustNames.PERSON_FIX, row(a).nameTrust)
        // Another copy of the same file finds the fix by its content key; another revision sharing only the code does not.
        identify(b, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "Zork Quest")
        assertEquals("My Zork", dao.fixOf(b)?.title)
        identify(c, listOf("crc:00001111:2048", "code:SNES:ZZZE"), "Zork Quest")
        assertNull(dao.fixOf(c))
    }

    @Test
    fun aFileWithOnlyAProductCodeIsFixedUnderItsRowAlone() = runTest {
        // A fix is never stored under a product code, even when the file has no
        // other key; another revision sharing the code keeps its own name.
        val (a, b) = insert("a.sfc", "b.sfc")
        identify(a, listOf("code:SNES:ZZZE"), "Zork Quest")
        identify(b, listOf("code:SNES:ZZZE"), "Zork Quest")
        dao.setFix(a, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertEquals(listOf(NameFixEntity.rowKey(a)), dao.fixesByKeys(listOf("code:SNES:ZZZE", NameFixEntity.rowKey(a))).map { it.fixKey })
        assertEquals("Mine", dao.fixOf(a)?.title)
        assertNull(dao.fixOf(b))
        assertFalse("B is not sent back to the pass", row(b).needsIdentify)
    }

    @Test
    fun aWholeFileReadAddsTheRowsFixUnderItsContentKeyOnlyWhileTheFixAndTheFileStand() = runTest {
        val (a) = insert("a.sfc")
        identify(a, listOf("code:SNES:ZZZE"), "Zork Quest")
        val size = row(a).sizeBytes
        val time = row(a).modifiedAt
        val full = listOf("crc:0000abcd:1024", "code:SNES:ZZZE")
        assertFalse("no fix yet: nothing to add", dao.addContentKeys(a, size, time, "full", full))
        assertEquals("fp", row(a).fingerprint)

        dao.setFix(a, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertFalse("the file changed since it was read", dao.addContentKeys(a, size + 1, time, "full", full))
        assertTrue(dao.addContentKeys(a, size, time, "full", full))
        assertEquals("full", row(a).fingerprint)
        assertEquals(full, FixKeys.split(row(a).fixKeys))
        assertEquals("Mine", dao.fixesByKeys(listOf("crc:0000abcd:1024")).single().title)
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(listOf("code:SNES:ZZZE")))
        assertEquals(listOf("crc:0000abcd:1024"), dao.contentKeysOf(a))

        assertTrue(dao.backToAutomatic(a))
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(full + NameFixEntity.rowKey(a)))
        assertFalse("undone meanwhile: the read adds nothing", dao.addContentKeys(a, size, time, "full", full))
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(full + NameFixEntity.rowKey(a)))
    }

    @Test
    fun aFileWithoutKeysIsFixedUnderItsRowKey() = runTest {
        val (a) = insert("a.sfc")
        dao.setFix(a, NameFixKind.CHOSEN_TITLE, "Zork Quest", "zork quest", "MAME", fixedAt = 1)
        val fix = dao.fixOf(a)!!
        assertEquals(NameFixEntity.rowKey(a), fix.fixKey)
        assertEquals(a, fix.gameId)
        assertEquals("MAME", fix.titleSource)
    }

    @Test
    fun notAGameTakesTheGameOffEveryListAndKeepsItsName() = runTest {
        val (a, b) = insert("a.sfc", "b.sfc")
        db.gameDao().setFavorite(a, true)
        dao.setFix(a, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 1)
        assertEquals("a.sfc", row(a).title)
        assertEquals(listOf(b), db.gameDao().observeSystemGames("snes").first().map { it.id })
        assertEquals(emptyList<Long>(), db.gameDao().observeFavorites().first().map { it.id })
        assertEquals(listOf(1), db.gameDao().observeSystemsWithGames().first().map { it.gameCount })
    }

    @Test
    fun backToAutomaticDeletesTheFixUnderEveryKeyAndShowsTheFileName() = runTest {
        val (a) = insert("a.sfc")
        identify(a, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "Zork Quest")
        dao.setFix(a, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 1)
        assertTrue(dao.backToAutomatic(a))
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(listOf("crc:0000abcd:1024", "code:SNES:ZZZE", NameFixEntity.rowKey(a))))
        val after = row(a)
        assertEquals("a.sfc", after.title)
        assertEquals(NameTrustNames.FILE_NAME, after.nameTrust)
        assertFalse(after.isNotGame)
        assertTrue(after.needsIdentify)
        assertEquals("fingerprints stay cached", "fp", after.fingerprint)
    }

    @Test
    fun fixCallsOnAGameThatIsGoneChangeNothing() = runTest {
        assertFalse(dao.setFix(404, NameFixKind.OWN_NAME, "x", "x", null, 1))
        assertFalse(dao.backToAutomatic(404))
        assertNull(dao.fixOf(404))
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(listOf(NameFixEntity.rowKey(404))))
    }

    @Test
    fun unidentifiedListsOnlyGamesNamedFromTheirFileAfterAPass() = runTest {
        val (a, b, c) = insert("a.sfc", "b.sfc", "c.sfc")
        identify(a, emptyList(), "a.sfc", trust = NameTrustNames.FILE_NAME)
        identify(b, listOf("crc:0000abcd:1024"), "Zork Quest")
        // c was not looked at yet: not listed.
        assertEquals(listOf(a), dao.observeUnidentified(emptySet()).first().map { it.id })
        assertEquals(listOf(a, b, c).size, db.gameDao().observeSystemGames("snes").first().size)
    }

    @Test
    fun gamesOfSystemsNamedByDesignAreNeverUnidentified() = runTest {
        val (a, b) = insert("a.sfc", "b.sfc")
        identify(a, emptyList(), "a", trust = NameTrustNames.FILE_NAME)
        identify(b, emptyList(), "b", trust = NameTrustNames.FILE_NAME)
        assertEquals(listOf(a, b), dao.observeUnidentified(emptySet()).first().map { it.id })
        // A system whose files are never identified (steam: a .steam file holds only an app id).
        assertEquals(emptyList<Long>(), dao.observeUnidentified(setOf("snes")).first().map { it.id })
        assertEquals(listOf(a, b), dao.observeUnidentified(setOf("steam")).first().map { it.id })
    }

    @Test
    fun notAGameListsOnlyThePersonsMarksAndBackToAutomaticTakesThemOut() = runTest {
        val (a, b, c) = insert("a.sfc", "b.sfc", "c.sfc")
        identify(a, listOf("crc:0000abcd:1024"), "Zork Quest")
        dao.setFix(a, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 1)
        // An identifier-hidden set (arcade BIOS) is not the person's mark.
        dao.setShownName(b, null, null, "", "IDENTIFIER_EXACT", isNotGame = true)
        assertEquals(listOf(a), dao.observeNotAGame().first().map { it.id })
        assertEquals(emptyList<Long>(), db.gameDao().observeHiddenGames().first().map { it.id })
        dao.backToAutomatic(a)
        assertEquals(emptyList<Long>(), dao.observeNotAGame().first().map { it.id })
        assertEquals(setOf(a, c), db.gameDao().observeSystemGames("snes").first().map { it.id }.toSet())
    }

    @Test
    fun identificationForInfo() = runTest {
        val (a) = insert("a.sfc")
        identify(a, listOf("crc:0000abcd:1024"), "Zork Quest")
        val info = dao.identificationOf(a)!!
        assertEquals("CRC", info.idMethod)
        assertEquals("EXACT", info.idConfidence)
        assertEquals("IDENTIFIER_EXACT", info.nameTrust)
        assertEquals(listOf("crc:0000abcd:1024"), FixKeys.split(info.fixKeys))
        assertEquals("a.sfc", info.fileTitle)
    }

    @Test
    fun aMoveKeepsTheNewRowsRowKeyFixAndDeletingAGameDropsIt() = runTest {
        val (old, new) = insert("old.sfc", "new.sfc")
        dao.setFix(new, NameFixKind.OWN_NAME, "Mine", "mine", null, 1)
        val newRow = scan.gamesInDirectory(folderId, "root").single { it.id == new }
        scan.finishScan(ScanFinish(folderId, moves = listOf(GameMove(old, newRow))))
        assertEquals(NameFixEntity.rowKey(old), dao.fixOf(old)?.fixKey)
        scan.finishScan(ScanFinish(folderId, removedGameIds = listOf(old)))
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(listOf(NameFixEntity.rowKey(old), NameFixEntity.rowKey(new))))
    }

    @Test
    fun resetLibraryForgetsFixes() = runTest {
        val (a) = insert("a.sfc")
        identify(a, listOf("crc:0000abcd:1024"), "Zork Quest")
        dao.setFix(a, NameFixKind.OWN_NAME, "Mine", "mine", null, 1)
        scan.forgetLibrary()
        assertEquals(emptyList<NameFixEntity>(), dao.fixesByKeys(listOf("crc:0000abcd:1024")))
    }

    @Test
    fun keysRoundTripAndBlankKeysAreDropped() {
        assertEquals(listOf("crc:1:2", "code:SNES:A B"), FixKeys.split(FixKeys.join(listOf("crc:1:2", " ", "code:SNES:A B"))))
        assertEquals(listOf("game:5"), FixKeys.forGame(5, ""))
        // A key holding a line break would split into two keys when read back: it is dropped.
        assertEquals(listOf("crc:1:2"), FixKeys.split(FixKeys.join(listOf("arcade:sf2\nce", "crc:1:2", "code:SNES:A\u0000B"))))
        assertEquals(listOf("game:7"), FixKeys.forGame(7, FixKeys.join(listOf("arcade:sf2\nce"))))
    }

    @Test
    fun rowsSharingAKeyAreSentToThePassNotOverwritten() = runTest {
        // The same ROM in two places (two picked folders, or a copy): one key, two rows. Another
        // revision sharing only the product code is left alone (last review).
        val (a, b, other) = insert("a.sfc", "b.sfc", "other.sfc")
        val (revision) = insert("rev.sfc")
        identify(a, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "Zork Quest")
        identify(b, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "Zork Quest")
        identify(other, listOf("crc:00001111:1024"), "Other")
        identify(revision, listOf("crc:00002222:1024", "code:SNES:ZZZE"), "Zork Quest")
        dao.setFix(a, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1)
        assertEquals("the row acted on shows the fix at once", "My Zork", row(a).title)
        assertTrue("the copy is named again by the pass (strongest key first)", row(b).needsIdentify)
        assertEquals("Zork Quest", row(b).title)
        assertFalse(row(a).needsIdentify)
        assertFalse(row(other).needsIdentify)
        assertFalse("a shared code sends nothing back", row(revision).needsIdentify)

        identify(b, listOf("crc:0000abcd:1024", "code:SNES:ZZZE"), "My Zork", trust = NameTrustNames.PERSON_FIX)
        dao.backToAutomatic(a)
        assertEquals("a.sfc", row(a).title)
        assertTrue(row(b).needsIdentify)
        assertFalse(row(other).needsIdentify)
        assertFalse(row(revision).needsIdentify)
    }

    @Test
    fun aFixOnAWeakSharedKeyNeverOverwritesAnotherRowsOwnFix() = runTest {
        // Rev 0 and Rev 1 share a product code but not a CRC; Rev 1 has its own fix on its CRC.
        val (rev0, rev1) = insert("rev0.sfc", "rev1.sfc")
        identify(rev0, listOf("crc:00000a00:1024", "code:SNES:ZZZE"), "Zork Quest")
        identify(rev1, listOf("crc:00000a01:1024", "code:SNES:ZZZE"), "Zork Quest")
        dao.setFix(rev1, NameFixKind.OWN_NAME, "Zork Quest Rev 1", "zork quest rev 1", null, fixedAt = 1)
        identify(rev1, listOf("crc:00000a01:1024", "code:SNES:ZZZE"), "Zork Quest Rev 1", trust = NameTrustNames.PERSON_FIX)

        dao.setFix(rev0, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 2)
        assertEquals("Zork Quest Rev 1", row(rev1).title)
        assertFalse("Not a game on Rev 0 does not hide Rev 1", row(rev1).isNotGame)
        assertEquals(NameTrustNames.PERSON_FIX, row(rev1).nameTrust)
        assertEquals("Rev 1 still finds its own fix by its strongest key", "Zork Quest Rev 1", dao.fixesByKeys(listOf("crc:00000a01:1024")).single().title)

        dao.backToAutomatic(rev0)
        assertEquals("Zork Quest Rev 1", row(rev1).title)
        assertEquals(NameTrustNames.PERSON_FIX, row(rev1).nameTrust)
    }

    @Test
    fun aKeyIsMatchedWholeNeverAsPartOfAnother() = runTest {
        val (a, b) = insert("a.sfc", "b.sfc")
        identify(a, listOf("code:SNES:ZZZE"), "Zork Quest")
        identify(b, listOf("code:SNES:ZZZE2"), "Zork Quest 2")
        dao.setFix(a, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertEquals("Zork Quest 2", row(b).title)
        assertFalse("a longer key is another key", row(b).needsIdentify)
    }
}
