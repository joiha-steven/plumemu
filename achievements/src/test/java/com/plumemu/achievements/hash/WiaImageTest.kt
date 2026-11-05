package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * GameCube `.rvz` and `.wia`: every supported codec gives the same bytes, and so the same hash, as the plain image.
 * No `dolphin-tool` was on the machine that wrote these, so the files are built by hand in [wiaOf] from the format
 * description. The zstd groups are real frames made by the `zstd` command line (see [zstdGroup]).
 */
class WiaImageTest {
    /** Two groups of 0x8000 bytes the hash reads, then a third of zeros (stored as size 0). */
    private val plain: ByteArray = gameCubeIso(1).copyOf(0x10000).copyOf(0x18000)

    private fun hash(console: Int, name: String, bytes: ByteArray) = RaHasher.hashBlocking(console, MemorySource(name, bytes))

    private val plainHash: RaHashResult by lazy { hash(RaConsoles.GAMECUBE, "game.iso", plain) }

    private fun reasonOf(result: RaHashResult): RaCannotHashReason =
        (result as? RaHashResult.CannotHash)?.reason ?: throw AssertionError("expected a refusal, got $result")

    /** [suffix] "n": frames made with `zstd -19 --no-content-size`, as Dolphin writes them. */
    private fun zstdGroup(group: Int, suffix: String = ""): ByteArray {
        val stream = checkNotNull(WiaImageTest::class.java.getResourceAsStream("/rvz/g$group$suffix.zst")) { "missing fixture g$group$suffix.zst" }
        return stream.use { it.readBytes() }
    }

    /** A zstd frame of one raw block, for the small tables (up to 255 bytes: single segment, one-byte content size). */
    private fun zstdRawFrame(data: ByteArray): ByteArray {
        check(data.size <= 255)
        val header = (data.size shl 3) or 1 // last block, raw
        return byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x20, data.size.toByte(), header.toByte(), (header shr 8).toByte(), (header shr 16).toByte()) + data
    }

    private fun zstdEncode(data: ByteArray, group: Int): ByteArray = if (group < 0) zstdRawFrame(data) else zstdGroup(group)

    private fun assertSameAsPlain(name: String, file: ByteArray, expected: ByteArray = plain) {
        assertEquals(name, plainHash, hash(RaConsoles.GAMECUBE, name, file))
        NintendoImages.open(MemorySource(name, file), name.substringAfterLast('.')) {}.use { disc ->
            assertEquals(expected.size.toLong(), disc.size)
            val back = ByteArray(expected.size)
            assertEquals(expected.size, disc.readAt(0, back, 0, back.size))
            assertArrayEquals(name, expected, back)
        }
    }

    // --- every supported codec

    @Test
    fun rvzWithoutCompression() {
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_NONE).bytes)
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_NONE, chunk = 0x20000).bytes)
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_NONE, chunk = 2 shl 20).bytes)
    }

    @Test
    fun rvzWithZstd() {
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_ZSTD, encode = ::zstdEncode).bytes)
    }

    @Test
    fun rvzWithZstdFramesThatCarryNoContentSize() {
        val frame = zstdGroup(0, "n")
        assertEquals(0, frame[4].toInt() and 0xC0) // frame header: no content size field
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_ZSTD, encode = { d, g -> if (g < 0) zstdRawFrame(d) else zstdGroup(g, "n") }).bytes)
    }

    @Test
    fun rvzWithLzmaWithAndWithoutAnEndMarker() {
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_LZMA, encode = { d, _ -> lzmaRaw(d, endMarker = true) }).bytes)
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_LZMA, encode = { d, _ -> lzmaRaw(d, endMarker = false) }).bytes)
    }

    @Test
    fun rvzWithLzma2() {
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_LZMA2, encode = { d, _ -> lzma2Raw(d) }).bytes)
    }

    @Test
    fun wiaWithNoneLzmaAndLzma2() {
        val chunk = 2 shl 20
        assertSameAsPlain("game.wia", wiaOf(plain, WIA_NONE, rvz = false, chunk = chunk).bytes)
        assertSameAsPlain("game.wia", wiaOf(plain, WIA_LZMA, rvz = false, chunk = chunk, encode = { d, _ -> lzmaRaw(d, endMarker = true) }).bytes)
        assertSameAsPlain("game.wia", wiaOf(plain, WIA_LZMA2, rvz = false, chunk = chunk, encode = { d, _ -> lzma2Raw(d) }).bytes)
    }

    @Test
    fun theMagicWordDecidesNotTheExtension() {
        assertSameAsPlain("game.wia", wiaOf(plain, WIA_NONE).bytes)
        assertSameAsPlain("game.rvz", wiaOf(plain, WIA_NONE, rvz = false, chunk = 2 shl 20).bytes)
    }

    @Test
    fun anImageOfSeveralGroupsReadsAcrossTheirBoundaries() {
        val big = gameCubeIso(5)
        val file = wiaOf(big, WIA_LZMA2, chunk = 2 shl 20, encode = { d, _ -> lzma2Raw(d) }).bytes
        NintendoImages.open(MemorySource("big.rvz", file), "rvz") {}.use { disc ->
            for (at in listOf(0L, 0x1FFFF0L, 0x1FFFFFL, (4L shl 20) + 100)) {
                val got = ByteArray(64)
                assertEquals(64, disc.readAt(at, got, 0, 64))
                assertArrayEquals("at $at", big.copyOfRange(at.toInt(), at.toInt() + 64), got)
            }
            assertEquals(10, disc.readAt(big.size - 10L, ByteArray(64), 0, 64))
        }
        assertEquals(hash(RaConsoles.GAMECUBE, "big.iso", big), hash(RaConsoles.GAMECUBE, "big.rvz", file))
    }

    @Test
    fun aGroupStoredWithoutCompressionInsideACompressedRvzIsRead() {
        val file = wiaOf(plain, WIA_LZMA2, encode = { d, _ -> lzma2Raw(d) }, storedGroups = setOf(0)).bytes
        assertSameAsPlain("game.rvz", file)
    }

    // --- RVZ packing (junk)

    @Test
    fun junkRunsAreRegeneratedFromTheirSeed() {
        // Junk from 0x3100 to the end of the first group: it holds the main executable's segments, so the hash reads it.
        val seed = ByteArray(68) { (it * 7 + 3).toByte() }
        val junk = junkBytes(seed, 0x3100, 0x8000 - 0x3100)
        val image = plain.copyOf()
        junk.copyInto(image, 0x3100)
        val packed = packedWithJunk(image, 0x3100, junk.size, seed)
        val expected = hash(RaConsoles.GAMECUBE, "game.iso", image)
        assertEquals(false, expected == plainHash)
        for ((codec, encode) in listOf<Pair<Int, (ByteArray, Int) -> ByteArray>>(
            WIA_NONE to { d, _ -> d },
            WIA_LZMA to { d, _ -> lzmaRaw(d, endMarker = true) },
            WIA_LZMA2 to { d, _ -> lzma2Raw(d) },
        )) {
            val file = wiaOf(image, codec, encode = encode, payloads = mapOf(0 to packed)).bytes
            assertEquals("codec $codec", expected, hash(RaConsoles.GAMECUBE, "game.rvz", file))
            NintendoImages.open(MemorySource("game.rvz", file), "rvz") {}.use { disc ->
                val back = ByteArray(image.size)
                disc.readAt(0, back, 0, back.size)
                assertArrayEquals("codec $codec", image, back)
            }
        }
    }

    @Test
    fun junkStartingAtASectorStartAndInTheMiddleOfAWordAreBothAligned() {
        // Junk from 0x8000 (a sector start) and, in another group, from 0x13 bytes in: the offset inside the sector is skipped.
        val seed = ByteArray(68) { (255 - it).toByte() }
        val image = plain.copyOf()
        junkBytes(seed, 0x8000, 0x8000).copyInto(image, 0x8000)
        val p1 = packedWithJunk(image.copyOfRange(0x8000, 0x10000), 0, 0x8000, seed)
        val early = junkBytes(seed, 0x13, 0x7FED)
        early.copyInto(image, 0x13)
        val p0 = packedWithJunk(image, 0x13, early.size, seed)
        val file = wiaOf(image, WIA_NONE, payloads = mapOf(0 to p0, 1 to p1)).bytes
        NintendoImages.open(MemorySource("game.rvz", file), "rvz") {}.use { disc ->
            val back = ByteArray(image.size)
            disc.readAt(0, back, 0, back.size)
            assertArrayEquals(image, back)
        }
    }

    @Test
    fun malformedPackingIsDamage() {
        val seed = ByteArray(68)
        val tooLong = packedWithJunk(plain, 0x10, 0x9000, seed) // junk runs past a 0x8000 group
        val seedCut = packedWithJunk(plain, 0x10, 0x100, seed).copyOf(4 + 0x10 + 4 + 30)
        val early = byteArrayOf(0, 0, 0x7F, 0xFF.toByte(), 1, 2, 3) // 0x7FFF bytes announced, 3 there
        for ((name, payload) in listOf("long" to tooLong, "seed" to seedCut, "early" to early)) {
            val file = wiaOf(plain, WIA_NONE, payloads = mapOf(0 to payload)).bytes
            assertEquals(name, BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.rvz", file)))
        }
    }

    // --- what is not read

    @Test
    fun aWiiDiscNeedsTheKeyItCannotCarry() {
        val wii = wiiDisc(encrypted = true)
        val file = wiaOf(wii, WIA_NONE).bytes
        assertEquals(NeedsDecoder, reasonOf(hash(RaConsoles.WII, "game.rvz", file)))
        val typed = resealWia(wiaOf(plain, WIA_NONE).bytes.also { it[0x48 + 3] = 2 })
        assertEquals(NeedsDecoder, reasonOf(hash(RaConsoles.GAMECUBE, "game.rvz", typed)))
    }

    @Test
    fun bzip2AndPurgeNeedADecoder() {
        for (codec in listOf(WIA_BZIP2, WIA_PURGE)) {
            val file = wiaOf(plain, codec, rvz = false, chunk = 2 shl 20).bytes
            assertEquals(NeedsDecoder, reasonOf(hash(RaConsoles.GAMECUBE, "game.wia", file)))
        }
    }

    @Test
    fun aNewerVersionNeedsADecoder() {
        val file = wiaOf(plain, WIA_NONE).bytes.also { it[8] = 2 }
        assertEquals(NeedsDecoder, reasonOf(hash(RaConsoles.GAMECUBE, "game.rvz", resealWia(file))))
    }

    // --- damage

    /** Damage must come out as [BadImage] when the whole image is read, and also from the hash when [byHash] (damage in group 0 or the headers). */
    private fun bad(name: String, file: ByteArray, byHash: Boolean = true) {
        try {
            NintendoImages.open(MemorySource("game.rvz", file), "rvz") {}.use { it.readAt(0, ByteArray(plain.size), 0, plain.size) }
            throw AssertionError("$name: expected damage")
        } catch (e: DiscFormatException) {
            assertEquals(name, BadImage, e.reason)
        }
        if (byHash) assertEquals(name, BadImage, reasonOf(hash(RaConsoles.GAMECUBE, "game.rvz", file)))
    }

    private fun patched(file: ByteArray, patch: (ByteArray) -> Unit) = resealWia(file.copyOf().also(patch))

    @Test
    fun aCutShortOrAlteredFileIsDamage() {
        val file = wiaOf(plain, WIA_NONE).bytes
        bad("empty", ByteArray(0), byHash = false) // the hasher refuses an empty file before it opens it
        bad("cut in the head", file.copyOf(0x30))
        bad("cut in the disc struct", file.copyOf(0x100))
        bad("cut at the end", file.copyOf(file.size - 10))
        bad("no magic", file.copyOf().also { it[0] = 'X'.code.toByte() })
        bad("file head checksum", file.copyOf().also { it[0x40] = (it[0x40].toInt() xor 1).toByte() })
        bad("disc struct checksum", file.copyOf().also { it[0x48 + 0x20] = (it[0x48 + 0x20].toInt() xor 1).toByte() })
        bad("size in the head", patched(file) { it[0x2F] = (it[0x2F].toInt() + 1).toByte() })
        bad("disc smaller than its head", patched(file) { it.fill(0, 0x24, 0x2C); it[0x2B] = 0x40 })
    }

    @Test
    fun badSizesInTheHeadersAreDamage() {
        val rvz = wiaOf(plain, WIA_NONE)
        val wia = wiaOf(plain, WIA_NONE, rvz = false, chunk = 2 shl 20)
        fun disc(file: WiaFile, at: Int, value: Long) = patched(file.bytes) { it.putBe32(0x48 + at, value) }
        bad("chunk not a sector multiple", disc(rvz, 0x0C, 0x8001))
        bad("chunk too small", disc(rvz, 0x0C, 0x4000))
        bad("chunk not a power of two", disc(rvz, 0x0C, 0x18000))
        bad("chunk zero", disc(rvz, 0x0C, 0))
        bad("wia chunk under 2 MiB", disc(wia, 0x0C, 0x100000))
        bad("unknown compression", disc(rvz, 0x04, 9))
        bad("zstd in a wia", disc(wia, 0x04, 5))
        bad("partitions on a GameCube", disc(rvz, 0x90, 1))
        bad("no raw data", disc(rvz, 0xB4, 0))
        bad("too many raw data", disc(rvz, 0xB4, 5000))
        bad("huge raw data count", disc(rvz, 0xB4, 0x7FFFFFFF))
        bad("no groups", disc(rvz, 0xC4, 0))
        bad("huge group count", disc(rvz, 0xC4, 0x7FFFFFFF))
        bad("group table past the end", disc(rvz, 0xC8 + 4, rvz.bytes.size.toLong()))
        bad("group table size", disc(rvz, 0xD0, 0x7FFFFFFF))
        bad("raw table size", disc(rvz, 0xC0, 0xFFFFFFFFL))
        val lzma = wiaOf(plain, WIA_LZMA, encode = { d, _ -> lzmaRaw(d, true) }).bytes
        bad("lzma properties missing", patched(lzma) { it[0x48 + 0xD4] = 0 })
        bad("lzma properties invalid", patched(lzma) { it[0x48 + 0xD5] = 0xE1.toByte() })
        bad("lzma2 properties invalid", patched(wiaOf(plain, WIA_LZMA2, encode = { d, _ -> lzma2Raw(d) }).bytes) { it[0x48 + 0xD5] = 41 })
    }

    @Test
    fun aValidButLargeChunkIsNotReadAndTheCapCoversWhatDolphinOffers() {
        // Dolphin's RVZ chunks run from 32 KiB to 2 MiB (default 128 KiB); wit's WIA default is 2 MiB.
        for (chunk in listOf(0x8000, 0x20000, 1 shl 20, 2 shl 20, 4 shl 20)) {
            assertEquals(chunk.toString(), plainHash, hash(RaConsoles.GAMECUBE, "game.rvz", wiaOf(plain, WIA_NONE, chunk = chunk).bytes))
        }
        for (chunk in listOf(6 shl 20, 32 shl 20, 64 shl 20)) {
            val file = patched(wiaOf(plain, WIA_NONE, rvz = false, chunk = 2 shl 20).bytes) { it.putBe32(0x48 + 0x0C, chunk.toLong()) }
            assertEquals(chunk.toString(), NeedsDecoder, reasonOf(hash(RaConsoles.GAMECUBE, "game.wia", file)))
        }
    }

    @Test
    fun codecPropertiesMustHaveTheirExactLengthAndFitTheDiscHeader() {
        val lzma = { d: ByteArray, _: Int -> lzmaRaw(d, true) }
        bad("lzma props 6 bytes", wiaOf(plain, WIA_LZMA, encode = lzma, comprData = byteArrayOf(LZMA_PROPS_BYTE.toByte(), 0, 0, 1, 0, 0)).bytes)
        bad("lzma props 4 bytes", wiaOf(plain, WIA_LZMA, encode = lzma, comprData = byteArrayOf(LZMA_PROPS_BYTE.toByte(), 0, 0, 1)).bytes)
        bad("lzma2 props 2 bytes", wiaOf(plain, WIA_LZMA2, encode = { d, _ -> lzma2Raw(d) }, comprData = byteArrayOf(16, 0)).bytes)
        bad("compressor data longer than 7", patched(wiaOf(plain, WIA_NONE).bytes) { it[0x48 + 0xD4] = 8 })
        // The disc struct is sized to end before its compressor data.
        val lzmaFile = wiaOf(plain, WIA_LZMA, encode = lzma).bytes
        bad("disc struct too short", patched(lzmaFile) { it.putBe32(0x0C, 0xD8) })
    }

    @Test
    fun rawDataThatOverlapsOrOverflowsIsDamage() {
        val file = wiaOf(plain, WIA_NONE)
        bad("overflowing length", patched(file.bytes) { it.putBe32(file.rawTableAt + 8, 0x7FFFFFFF); it.putBe32(file.rawTableAt + 12, 0xFFFFFFFFL) })
        bad("offset past the disc", patched(file.bytes) { it.putBe32(file.rawTableAt, 0x7FFFFFFF) })
        bad("length past the disc", patched(file.bytes) { it.putBe32(file.rawTableAt + 12, plain.size.toLong()) })
        val overlapping = wiaOf(plain, WIA_NONE, extraRaw = listOf(longArrayOf(0x4000, 0x4000, 0, 1))).bytes
        bad("overlapping entries", overlapping)
        // An empty entry at the end of the disc touches nothing and is fine.
        val apart = wiaOf(plain.copyOf(0x10000), WIA_NONE, extraRaw = listOf(longArrayOf(0x10000, 0, 0, 0))).bytes
        NintendoImages.open(MemorySource("game.rvz", apart), "rvz") {}.use { assertEquals(0x10000L, it.size) }
    }

    @Test
    fun badTablesAreDamage() {
        val file = wiaOf(plain, WIA_NONE)
        fun group(n: Int, at: Int, value: Long) = patched(file.bytes) { it.putBe32(file.groupTableAt + n * file.groupEntryBytes + at, value) }
        fun raw(at: Int, value: Long) = patched(file.bytes) { it.putBe32(file.rawTableAt + at, value) }
        bad("a group past the end", group(0, 0, file.bytes.size / 4L))
        bad("a group starting inside the head", group(0, 0, 1))
        bad("a group longer than the file", group(0, 4, 0x80000000L or 0x7FFFFFF0L))
        bad("a group larger than a chunk", group(1, 4, 0x80000000L or 0x20000L), byHash = false)
        bad("a group past the end, later", group(1, 0, file.bytes.size / 4L), byHash = false)
        bad("a packed size larger than a chunk", group(0, 8, 0x40000))
        bad("raw data past the disc", raw(8 + 4, 0x7FFFFFFF))
        bad("raw data groups missing", raw(20, 1))
        bad("raw data groups past the table", raw(16, 5))
    }

    @Test
    fun aDamagedStreamIsDamageNotACrash() {
        val garbage = ByteArray(100) { 0x42 }
        val codecs = listOf<Triple<Int, (ByteArray, Int) -> ByteArray, ByteArray>>(
            Triple(WIA_ZSTD, ::zstdEncode, zstdGroup(0)),
            Triple(WIA_LZMA, { d, _ -> lzmaRaw(d, true) }, lzmaRaw(plain.copyOf(0x8000), true)),
            Triple(WIA_LZMA2, { d, _ -> lzma2Raw(d) }, lzma2Raw(plain.copyOf(0x8000))),
        )
        for ((codec, encode, good) in codecs) {
            val cutShort = good.copyOf(good.size / 3)
            bad("codec $codec cut short", wiaOf(plain, codec, encode = { d, g -> if (g == 0) cutShort else encode(d, g) }).bytes)
            bad("codec $codec garbage", wiaOf(plain, codec, encode = { d, g -> if (g == 0) garbage else encode(d, g) }).bytes)
            bad("codec $codec garbage later", wiaOf(plain.copyOf(0x10000), codec, encode = { d, g -> if (g == 1) garbage else encode(d, g) }).bytes, byHash = false)
        }
    }

    @Test
    fun aGroupTooShortForItsChunkIsDamage() {
        // Group 0 announces 0x100 stored bytes for a 0x8000-byte chunk.
        val file = wiaOf(plain, WIA_NONE)
        bad("short", patched(file.bytes) { it.putBe32(file.groupTableAt + 4, 0x100) })
    }

    // --- bounds and plumbing

    @Test
    fun anOpenRvzReadsOnlyWhatTheHashNeeds() {
        // The hash touches the first group only, so damage in a later group does not matter to it.
        val garbage = ByteArray(100) { 0x42 }
        val file = wiaOf(plain, WIA_ZSTD, encode = { d, g -> if (g == 1) garbage else zstdEncode(d, g) })
        assertEquals(plainHash, hash(RaConsoles.GAMECUBE, "game.rvz", file.bytes))
    }

    @Test
    fun cancellationPassesThrough() {
        try {
            RaHasher.hashBlocking(RaConsoles.GAMECUBE, MemorySource("game.rvz", wiaOf(plain, WIA_NONE).bytes)) { throw CancellationException("stop") }
            throw AssertionError("expected the cancellation to pass through")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }

    @Test
    fun aReadFailureIsReportedNotSwallowed() {
        assertEquals(true, RaHasher.hashBlocking(RaConsoles.GAMECUBE, FailingSource("game.rvz")) is RaHashResult.Unreadable)
    }
}
