package com.plumemu.library.scan

import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.coredata.db.FixKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * A person's name fixes through the scan: where a fix is stored and found, Back to
 * automatic, naming at once, and the whole-file read that gives a loose cartridge its content key.
 * Same generated tree as [LibraryIdentifyTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryNameFixTest {
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
    fun aFixFollowsARenamedFileOnItsRowAndAZipByItsContentKey() = runTest {
        scan()
        val dao = fx.db.identifyDao()
        val loose = fx.game("Zork Quest 2 (USA).gba")
        val zipped = fx.game("zq.zip")
        assertTrue(dao.setFix(loose.id, NameFixKind.OWN_NAME, "My Zork", "my zork", null, fixedAt = 1))
        assertTrue(dao.setFix(zipped.id, NameFixKind.OWN_NAME, "My Zip", "my zip", null, fixedAt = 1))
        // A region mark changed: the scan matches the rename and keeps the row, so its row key.
        File(fx.roms, "gba/Zork Quest 2 (USA).gba").renameTo(File(fx.roms, "gba/Zork Quest 2 (Europe).gba"))
        // Not matched as a rename (another title): a new row, found by the zip's content key.
        File(fx.roms, "snes/zq.zip").renameTo(File(fx.roms, "snes/other.zip"))
        fx.touch("gba")
        fx.touch("snes")
        scan()
        val renamed = fx.game("Zork Quest 2 (Europe).gba")
        assertEquals(loose.id, renamed.id)
        assertEquals("My Zork", renamed.title)
        assertEquals(NameTrustNames.PERSON_FIX, renamed.nameTrust)
        val moved = fx.game("other.zip")
        assertTrue(moved.id != zipped.id)
        assertEquals("My Zip", moved.title)
        assertEquals(NameTrustNames.PERSON_FIX, moved.nameTrust)
    }

    @Test
    fun aFixAlwaysWinsOverANewExactResult() = runTest {
        scan()
        val zipped = fx.game("zq.zip")
        fx.db.identifyDao().setFix(zipped.id, NameFixKind.CHOSEN_TITLE, "Zork Quest Deluxe", "zork quest deluxe", "Test DAT", fixedAt = 1)
        File(fx.roms, "snes/zq.zip").setLastModified(FILE_TIME + 60_000)
        fx.touch("snes")
        scan()
        assertEquals(listOf("fs:snes/zq.zip"), fx.tree.opened)
        assertEquals("Zork Quest Deluxe", fx.game("zq.zip").title)
        assertEquals(NameTrustNames.PERSON_FIX, fx.game("zq.zip").nameTrust)
    }

    @Test
    fun notAGameFixTakesTheGameOffTheListsAcrossRescans() = runTest {
        scan()
        val unknown = fx.game("Unknown Thing (Europe).sfc")
        fx.db.identifyDao().setFix(unknown.id, NameFixKind.NOT_A_GAME, null, null, null, fixedAt = 1)
        File(fx.roms, "snes/Unknown Thing (Europe).sfc").setLastModified(FILE_TIME + 60_000)
        fx.touch("snes")
        scan()
        assertTrue(fx.game("Unknown Thing (Europe).sfc").isNotGame)
        assertFalse(fx.db.gameDao().observeSystemGames("snes").first().any { it.id == unknown.id })
    }

    @Test
    fun backToAutomaticNamesTheGameAgainWithoutOpeningIt() = runTest {
        scan()
        val gba = fx.game("Zork Quest 2 (USA).gba")
        fx.db.identifyDao().setFix(gba.id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        fx.db.identifyDao().backToAutomatic(gba.id)
        assertEquals("Zork Quest 2", fx.game("Zork Quest 2 (USA).gba").title)
        scan()
        assertEquals(emptyList<String>(), fx.tree.opened)
        assertEquals("Zork Quest II", fx.game("Zork Quest 2 (USA).gba").title)
        assertEquals("IDENTIFIER_EXACT", fx.game("Zork Quest 2 (USA).gba").nameTrust)
    }

    @Test
    fun nameWaitingNamesABackToAutomaticGameAtOnceWithoutAWalk() = runTest {
        scan()
        val gba = fx.game("Zork Quest 2 (USA).gba")
        fx.db.identifyDao().setFix(gba.id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        fx.db.identifyDao().backToAutomatic(gba.id)
        fx.tree.opened.clear()
        val events = fx.scanner(naming = naming).nameWaiting().toList()
        assertTrue("no walk", events.none { it is ScanEvent.FolderFinished })
        assertEquals("named from the cached fingerprints", emptyList<String>(), fx.tree.opened)
        val named = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Zork Quest II", named.title)
        assertEquals("IDENTIFIER_EXACT", named.nameTrust)
        assertFalse(named.needsIdentify)
    }

    @Test
    fun nameWaitingKeepsTheFileNameWhenTheFileCannotBeReadAndTheCacheIsStale() = runTest {
        scan()
        File(fx.roms, "gba/Zork Quest 2 (USA).gba").setLastModified(FILE_TIME + 60_000)
        fx.touch("gba")
        fx.scanner().scan().toList() // the walk alone sees the change: the cached fingerprints no longer fit
        val gba = fx.game("Zork Quest 2 (USA).gba")
        fx.db.identifyDao().setFix(gba.id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        fx.db.identifyDao().backToAutomatic(gba.id)
        fx.tree.failingReads += "fs:gba/Zork Quest 2 (USA).gba"
        fx.scanner(naming = naming).nameWaiting().toList()
        val kept = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Zork Quest 2", kept.title)
        assertEquals(NameTrustNames.FILE_NAME, kept.nameTrust)
        assertTrue("tried again by the next scan", kept.needsIdentify)
    }

    @Test
    fun nameWaitingAlsoNamesOtherCopiesSharingTheFixedKey() = runTest {
        write("snes/zq copy.zip", TestNaming.zip("zq.sfc", TestNaming.ZIPPED_ROM))
        fx.touch("snes")
        scan()
        val dao = fx.db.identifyDao()
        val first = fx.game("zq.zip")
        dao.setFix(first.id, NameFixKind.CHOSEN_TITLE, "Zork Quest Deluxe", "zork quest deluxe", "Test DAT", fixedAt = 1)
        assertEquals("Zork Quest", fx.game("zq copy.zip").title)
        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals("Zork Quest Deluxe", fx.game("zq copy.zip").title)

        dao.backToAutomatic(first.id)
        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals("Zork Quest", fx.game("zq.zip").title)
        assertEquals("Zork Quest", fx.game("zq copy.zip").title)
        assertEquals("IDENTIFIER_EXACT", fx.game("zq copy.zip").nameTrust)
    }

    @Test
    fun aFixStaysWithItsFileAndNotWithAnotherSharingItsProductCode() = runTest {
        // Two loose cartridges with the same header code but different contents (Rev 0, Rev 1),
        // as a scan leaves them: read at QUICK depth, so the code is their only key.
        write("gba/rev0.gba", TestNaming.gbaRom())
        write("gba/rev1.gba", TestNaming.gbaRom(size = 1 shl 17))
        fx.touch("gba")
        scan()
        val dao = fx.db.identifyDao()
        val a = fx.game("rev0.gba")
        val b = fx.game("rev1.gba")
        val keysB = FixKeys.split(b.fixKeys)
        assertEquals("QUICK: the shared code is the only key", FixKeys.split(a.fixKeys), keysB)
        assertTrue(keysB.single().startsWith("code:"))
        val automaticB = b.title

        dao.setFix(a.id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        assertEquals(listOf(NameFixEntity.rowKey(a.id)), dao.fixesByKeys(keysB + NameFixEntity.rowKey(a.id)).map { it.fixKey })
        assertEquals("Mine", fx.game("rev0.gba").title)

        fx.tree.opened.clear()
        assertEquals(ContentKeyOutcome.ADDED, fx.scanner(naming = naming).addContentKey(a.id))
        assertEquals("one whole-file read of that one file", listOf("fs:gba/rev0.gba"), fx.tree.opened)
        val keysA = FixKeys.split(fx.game("rev0.gba").fixKeys)
        val crcA = keysA.single { it.startsWith("crc:") }
        assertEquals("Mine", dao.fixesByKeys(listOf(crcA)).single().title)
        assertEquals("never under the shared code", emptyList<String>(), dao.fixesByKeys(keysB).map { it.fixKey })

        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals("Mine", fx.game("rev0.gba").title)
        assertEquals("B keeps its automatic name", automaticB, fx.game("rev1.gba").title)
        File(fx.roms, "gba/rev1.gba").setLastModified(FILE_TIME + 60_000)
        fx.touch("gba", step = 2)
        scan()
        assertEquals("B is read again and still keeps its automatic name", automaticB, fx.game("rev1.gba").title)
        assertEquals("Mine", fx.game("rev0.gba").title)

        dao.backToAutomatic(a.id)
        fx.scanner(naming = naming).nameWaiting().toList()
        val allKeys = keysA + FixKeys.split(fx.game("rev1.gba").fixKeys) + listOf(NameFixEntity.rowKey(a.id), NameFixEntity.rowKey(b.id))
        assertEquals("no fix row left under any key of A or B", emptyList<String>(), dao.fixesByKeys(allKeys).map { it.fixKey })
        assertEquals(automaticB, fx.game("rev1.gba").title)
        assertTrue(fx.game("rev0.gba").nameTrust != NameTrustNames.PERSON_FIX)
        assertTrue(fx.game("rev1.gba").nameTrust != NameTrustNames.PERSON_FIX)
    }

    @Test
    fun aWholeFileReadThatFailsKeepsTheFixOnItsRowAndSaysSo() = runTest {
        scan()
        val dao = fx.db.identifyDao()
        val gba = fx.game("Zork Quest 2 (USA).gba")
        dao.setFix(gba.id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 1)
        fx.tree.failingReads += "fs:gba/Zork Quest 2 (USA).gba"
        assertEquals(ContentKeyOutcome.NOT_READ, fx.scanner(naming = naming).addContentKey(gba.id))
        val kept = fx.game("Zork Quest 2 (USA).gba")
        assertEquals("Mine", kept.title)
        assertEquals(gba.fixKeys, kept.fixKeys)
        assertEquals(gba.fingerprint, kept.fingerprint)
        assertEquals(listOf(NameFixEntity.rowKey(gba.id)), dao.fixesByKeys(FixKeys.split(kept.fixKeys) + NameFixEntity.rowKey(gba.id)).map { it.fixKey })
        val lines = ShadowLog.getLogs().map { it.msg }
        assertTrue(lines.any { it.contains("game ${gba.id} whole-file read failed (ClosedChannelException)") })
        assertFalse(lines.any { it.contains("Zork") || it.contains("Mine") || it.contains(".gba") })
        assertTrue(ShadowLog.getLogs().none { it.throwable != null })

        assertEquals("a zip already has its content key: nothing is read", ContentKeyOutcome.ALREADY_KEYED, fx.scanner(naming = naming).addContentKey(fx.game("zq.zip").id))
        assertEquals(ContentKeyOutcome.STALE, fx.scanner(naming = naming).addContentKey(404))
    }

    @Test
    fun aFixMadeBeforeTheFileWasReadMovesToItsContentKeys() = runTest {
        fx.scanner().scan().toList() // the walk only: no names, no keys yet
        val dao = fx.db.identifyDao()
        dao.setFix(fx.game("zq.zip").id, NameFixKind.OWN_NAME, "Early", "early", null, fixedAt = 1)
        dao.setFix(fx.game("Zork Quest 2 (USA).gba").id, NameFixKind.OWN_NAME, "Early too", "early too", null, fixedAt = 1)
        scan()
        val zipped = fx.game("zq.zip")
        assertEquals("Early", zipped.title)
        val crc = FixKeys.split(zipped.fixKeys).single { it.startsWith("crc:") }
        assertEquals("Early", dao.fixesByKeys(listOf(crc)).single().title)
        assertEquals("Early too", fx.game("Zork Quest 2 (USA).gba").title)
        assertEquals("not copied to a product code", emptyList<String>(), dao.fixesByKeys(listOf("code:GBA:ZZZE")).map { it.fixKey })
    }

    private companion object {
        const val FILE_TIME = 1_600_000_000_000L
    }
}
