package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * GameCube and Wii discs: the plain image as rcheevos reads it, plus the `.gcz` and `.wbfs` containers.
 * Expected hashes are built from slices of the image the test made, not from the code under test.
 */
class NintendoDiscHashesTest {
    private fun md5Of(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        parts.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(console: Int, name: String, bytes: ByteArray) = RaHasher.hashBlocking(console, MemorySource(name, bytes))

    private fun reasonOf(result: RaHashResult): RaCannotHashReason =
        (result as? RaHashResult.CannotHash)?.reason ?: throw AssertionError("expected a refusal, got $result")

    // --- GameCube

    private val gameCube = gameCubeIso(1)

    /** The header (0x2440 plus the 0x20 and the two 0xFF apploader sizes), then the nine segments of 0xFF bytes. */
    private val gameCubeMd5: String by lazy {
        val parts = mutableListOf(gameCube.copyOf(0x2440 + 0x20 + 0xFF + 0xFF))
        for (n in 0 until 18 step 2) {
            val at = (0x31 + n) shl 8
            parts += gameCube.copyOfRange(at, at + 0xFF)
        }
        md5Of(*parts.toTypedArray())
    }

    @Test
    fun gameCubePlainImagesAreHashedByNameOfEitherExtension() {
        for (name in listOf("game.iso", "game.gcm", "GAME.ISO")) {
            assertEquals(name, RaHashResult.Hashed(gameCubeMd5), hash(RaConsoles.GAMECUBE, name, gameCube))
        }
    }

    @Test
    fun gameCubeGczGivesTheSameHashAsThePlainImage() {
        assertEquals(RaHashResult.Hashed(gameCubeMd5), hash(RaConsoles.GAMECUBE, "game.gcz", gczOf(gameCube)))
        assertEquals(RaHashResult.Hashed(gameCubeMd5), hash(RaConsoles.GAMECUBE, "game.gcz", gczOf(gameCube, blockSize = 0x4000)))
    }

    @Test
    fun gameCubeImageWithoutTheMagicWordIsNotARom() {
        assertEquals(NotARom, reasonOf(hash(RaConsoles.GAMECUBE, "game.iso", gameCube.copyOf().also { it[0x1C] = 0 })))
        assertEquals(NotARom, reasonOf(hash(RaConsoles.GAMECUBE, "game.iso", ByteArray(8))))
        assertEquals(NotARom, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", gczOf(gameCube.copyOf().also { it[0x1C] = 0 }))))
    }

    @Test
    fun gameCubeImageCutBeforeItsExecutableIsNotARom() {
        assertEquals(NotARom, reasonOf(hash(RaConsoles.GAMECUBE, "game.iso", gameCube.copyOf(0x3100))))
    }

    @Test
    fun gameCubeExecutableWithAnAbsurdSegmentTableIsRefused() {
        val image = gameCube.copyOf()
        for (n in 0 until 18) image.putBe32(0x3000 + 0x90 + n * 4, 0x0FFFFFFFL)
        assertEquals(NotARom, reasonOf(hash(RaConsoles.GAMECUBE, "game.iso", image)))
    }

    // --- .gcz

    @Test
    fun aGczWithAWrongMagicOrACutHeaderIsBadImage() {
        val gcz = gczOf(gameCube)
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", gcz.copyOf().also { it[0] = 0 })))
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", gcz.copyOf(20))))
    }

    @Test
    fun aGczWithAnImplausibleBlockSizeOrCountIsBadImage() {
        val gcz = gczOf(gameCube)
        val tiny = gcz.copyOf().also { it[24] = 1; it[25] = 0 }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", tiny)))
        val wrongCount = gcz.copyOf().also { it[28] = (it[28] + 1).toByte() }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", wrongCount)))
    }

    @Test
    fun aGczCutShortIsBadImage() {
        val gcz = gczOf(gameCube)
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", gcz.copyOf(gcz.size - 100))))
    }

    @Test
    fun aCorruptCompressedGczBlockIsBadImage() {
        // A zero-heavy image so block 0 is deflated; the damage is in the compressed bytes.
        val zeros = gameCubeIso(1).also { it.fill(0, 0x4400, it.size) }
        val gcz = gczOf(zeros)
        val blocks = 1024 * 1024 / 0x8000
        val dataStart = 32 + blocks * 12
        val damaged = gcz.copyOf().also { it[dataStart + 3] = (it[dataStart + 3].toInt() xor 0x55).toByte() }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", damaged)))
    }

    @Test
    fun aGczBlockThatDoesNotMatchItsChecksumIsBadImage() {
        val gcz = gczOf(gameCube)
        val blocks = 1024 * 1024 / 0x8000
        val sumsAt = 32 + blocks * 8
        val damaged = gcz.copyOf().also { it[sumsAt] = (it[sumsAt].toInt() xor 1).toByte() }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", damaged)))
    }

    @Test
    fun aGczBlockPointingPastTheFileIsBadImage() {
        val gcz = gczOf(gameCube)
        val damaged = gcz.copyOf().also { it[32 + 8 + 5] = 0x7F }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.gcz", damaged)))
    }

    // --- Wii

    private val wiiEncrypted = wiiDisc(encrypted = true)
    private val wiiDecrypted = wiiDisc(encrypted = false)

    private fun wiiCommon(image: ByteArray) = arrayOf(
        image.copyOf(0x80),
        image.copyOfRange(0x4E000, 0x4E004),
        image.copyOfRange(WiiLayout.TMD_AT, WiiLayout.TMD_AT + WiiLayout.TMD_SIZE),
    )

    private val wiiEncryptedMd5: String by lazy {
        val clusters = (0 until WiiLayout.DATA_BYTES / 0x8000).map { n ->
            val at = WiiLayout.DATA_AT + n * 0x8000 + 0x400
            wiiEncrypted.copyOfRange(at, at + 0x7C00)
        }
        md5Of(*wiiCommon(wiiEncrypted), *clusters.toTypedArray())
    }

    private val wiiDecryptedMd5: String by lazy {
        val part = WiiLayout.DATA_AT
        val header = wiiDecrypted.copyOfRange(part, part + 0x2440 + 0x20 + WiiLayout.APPLOADER_BODY + WiiLayout.APPLOADER_TRAILER)
        val segments = WiiLayout.SEGMENTS.map { (offset, size) -> wiiDecrypted.copyOfRange(part + offset, part + offset + size) }
        md5Of(*wiiCommon(wiiDecrypted), header, *segments.toTypedArray())
    }

    @Test
    fun wiiEncryptedDiscHashesItsClustersAndTheUpdatePartitionIsSkipped() {
        assertEquals(RaHashResult.Hashed(wiiEncryptedMd5), hash(RaConsoles.WII, "game.iso", wiiEncrypted))
        // Changing the update partition changes nothing.
        val other = wiiEncrypted.copyOf().also { it.fill(7, WiiLayout.UPDATE_PARTITION + 0x300, WiiLayout.UPDATE_PARTITION + 0x400) }
        assertEquals(RaHashResult.Hashed(wiiEncryptedMd5), hash(RaConsoles.WII, "game.iso", other))
    }

    @Test
    fun wiiDecryptedDiscHashesItsBootData() {
        assertEquals(RaHashResult.Hashed(wiiDecryptedMd5), hash(RaConsoles.WII, "game.iso", wiiDecrypted))
    }

    @Test
    fun wiiGczAndWbfsGiveTheSameHashAsThePlainImage() {
        assertEquals(RaHashResult.Hashed(wiiEncryptedMd5), hash(RaConsoles.WII, "game.gcz", gczOf(wiiEncrypted)))
        assertEquals(RaHashResult.Hashed(wiiEncryptedMd5), hash(RaConsoles.WII, "game.wbfs", wbfsOf(wiiEncrypted)))
        assertEquals(RaHashResult.Hashed(wiiDecryptedMd5), hash(RaConsoles.WII, "game.wbfs", wbfsOf(wiiDecrypted)))
        assertEquals(RaHashResult.Hashed(wiiDecryptedMd5), hash(RaConsoles.WII, "game.gcz", gczOf(wiiDecrypted)))
    }

    @Test
    fun wiiWareIsHashedFromItsMetadataAndContents() {
        val image = wiiWarePackage()
        val tmd = 0xC0
        val expected = md5Of(
            image.copyOfRange(tmd, tmd + 0x240),
            image.copyOfRange(0x300, 0x300 + 112), // 100 rounded up to 16
            image.copyOfRange(0x380, 0x380 + 208), // the next content starts at the next multiple of 0x40
        )
        assertEquals(RaHashResult.Hashed(expected), hash(RaConsoles.WII, "game.wad", image))
    }

    @Test
    fun wiiFilesOfNeitherKindAreNotARom() {
        assertEquals(NotARom, reasonOf(hash(RaConsoles.WII, "game.iso", gameCube)))
        assertEquals(NotARom, reasonOf(hash(RaConsoles.WII, "game.iso", ByteArray(4))))
    }

    @Test
    fun wiiDiscWithoutPartitionsOrWithTooManyIsNotARom() {
        assertEquals(NotARom, reasonOf(hash(RaConsoles.WII, "game.iso", wiiEncrypted.copyOf().also { it.putBe32(0x40000, 0) })))
        assertEquals(NotARom, reasonOf(hash(RaConsoles.WII, "game.iso", wiiEncrypted.copyOf().also { it.putBe32(0x40000, 17) })))
    }

    @Test
    fun wiiDiscCutBeforeItsClustersIsNotARom() {
        assertEquals(NotARom, reasonOf(hash(RaConsoles.WII, "game.iso", wiiEncrypted.copyOf(WiiLayout.DATA_AT + 0x4000))))
    }

    // --- .wbfs

    @Test
    fun aWbfsWithoutItsHeaderOrWithBadSectorSizesIsBadImage() {
        val wbfs = wbfsOf(wiiEncrypted)
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfs.copyOf().also { it[0] = 'X'.code.toByte() })))
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfs.copyOf().also { it[9] = 3 })))
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfs.copyOf().also { it[8] = 40 })))
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfs.copyOf().also { it[12] = 0 })))
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfs.copyOf(100))))
    }

    @Test
    fun aWbfsSectorPointingPastTheFileIsBadImage() {
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfsOf(wiiEncrypted, pointOutsideAt = 0))))
    }

    @Test
    fun aWbfsCutInsideItsSectorTableIsBadImage() {
        assertEquals(BadImage, reasonOf(hash(RaConsoles.WII, "game.wbfs", wbfsOf(wiiEncrypted).copyOf(0x20000))))
    }

    @Test
    fun aSplitWbfsIsNotYetSupportedWithAClearDetail() {
        val folder = Folder("game.wbfs" to wbfsOf(wiiEncrypted), "game.wbf1" to ByteArray(100))
        val result = RaHasher.hashBlocking(RaConsoles.WII, folder.source("game.wbfs")) as RaHashResult.CannotHash
        assertEquals(RaCannotHashReason.NotYetSupported, result.reason)
        assertEquals(true, result.detail?.contains("split") == true)
    }

    // --- containers that need a decoder

    @Test
    fun rvzWiaChdAndCisoNeedADecoder() {
        for (console in listOf(RaConsoles.GAMECUBE, RaConsoles.WII)) {
            for (name in listOf("game.rvz", "game.wia", "game.chd", "game.ciso")) {
                assertEquals("$console $name", NeedsDecoder, reasonOf(hash(console, name, gameCube)))
            }
        }
    }

    @Test
    fun theThreeDsStaysUnhashable() {
        assertEquals(RaCannotHashReason.NotYetSupported, reasonOf(hash(RaConsoles.NINTENDO_3DS, "game.3ds", gameCube)))
    }

    // --- cancellation

    @Test
    fun cancellationPassesThroughEveryNintendoContainer() {
        val cases = listOf(
            RaConsoles.GAMECUBE to MemorySource("game.iso", gameCube),
            RaConsoles.GAMECUBE to MemorySource("game.gcz", gczOf(gameCube)),
            RaConsoles.WII to MemorySource("game.iso", wiiEncrypted),
            RaConsoles.WII to MemorySource("game.wbfs", wbfsOf(wiiDecrypted)),
            RaConsoles.WII to MemorySource("game.wad", wiiWarePackage()),
        )
        for ((console, source) in cases) {
            try {
                RaHasher.hashBlocking(console, source) { throw CancellationException("stop") }
                fail("${source.fileName}: expected the cancellation to pass through")
            } catch (expected: CancellationException) {
                assertEquals("stop", expected.message)
            }
        }
    }

    @Test
    fun aReadFailureIsReportedNotSwallowed() {
        for (name in listOf("game.iso", "game.gcz", "game.wbfs")) {
            val result = RaHasher.hashBlocking(RaConsoles.GAMECUBE, FailingSource(name))
            assertEquals(name, true, result is RaHashResult.Unreadable)
        }
    }
}
