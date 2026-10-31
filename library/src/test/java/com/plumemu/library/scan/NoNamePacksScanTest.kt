package com.plumemu.library.scan

import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.identify.Identifier
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.PackStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Collections

/**
 * A build without the bundled name packs (the public source copy) has no pack index at
 * all. A scan must still finish and name every game from its cleaned file name, with the confidence
 * "unknown" and no failure; this is what a person gets from such a build. Needs no real data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoNamePacksScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private val log: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Before
    fun setUp() {
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, bytes: ByteArray) =
        File(fx.roms, path).apply { parentFile?.mkdirs(); writeBytes(bytes); setLastModified(ScanFixture.BASE_TIME) }

    @Test
    fun gamesAreNamedFromCleanedFileNamesAndNothingFails() = runTest {
        val identifyLog = IdentifyLog { log += it }
        val packs = PackStore(temp.newFolder("packs"), BundledPacks.NONE, identifyLog)
        val naming = GameNaming(packs, Identifier(packs, FingerprintReader(), identifyLog))
        write("snes/Moonlit Harbour (Japan).sfc", ByteArray(64))
        write("snes/Unknown Thing (Europe).sfc", ByteArray(64))
        write("gba/Zork Quest 2 (USA).gba", ByteArray(64))
        write("arcade/zorkfght.zip", ByteArray(16))
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }

        fx.scanner(naming = naming).scan().toList()

        val moonlit = fx.game("Moonlit Harbour (Japan).sfc")
        assertEquals("Moonlit Harbour", moonlit.title)
        assertEquals(NameTrustNames.FILE_NAME, moonlit.nameTrust)
        assertEquals("FILE_NAME", moonlit.idMethod)
        assertEquals("JPN", moonlit.regions)
        assertEquals("Zork Quest 2", fx.game("Zork Quest 2 (USA).gba").title)
        // An arcade set name is only a code without its pack: it stays as the file name gave it.
        assertEquals("zorkfght", fx.game("zorkfght.zip").title)
        assertEquals(4, fx.games().size)
        assertTrue(fx.games().all { it.nameTrust == NameTrustNames.FILE_NAME })
        assertNull(packs.packFor("snes"))
        // One line says why; it is not repeated for each system or game.
        assertEquals(log.toString(), 1, log.size)
    }
}
