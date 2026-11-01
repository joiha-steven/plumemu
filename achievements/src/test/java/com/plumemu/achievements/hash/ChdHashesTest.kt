package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NotYetSupported
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.FilterInputStream
import java.io.InputStream

/**
 * `.chd` discs hash exactly like the same disc as `.bin` + `.cue`. The fixtures in `src/test/resources/chd/` were made by
 * `chdman createcd -i ps1.cue -c <codec>` (MAME's tool, version 0.289) from the disc [ps1Bin] builds: a PlayStation data
 * track (Mode 2 raw, 27 sectors, the executable `SLUS_007.45` of 14,336 bytes) and a 12-sector audio track, which makes
 * five 8-frame hunks. `sega-*.chd` come from `chdman createraw -hs 2048 -us 2048 -c <codec>` over the one-sector Sega CD
 * image of the rcheevos vector.
 */
class ChdHashesTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(ChdHashesTest::class.java.getResourceAsStream("/chd/$name")) { "missing fixture $name" }.use { it.readBytes() }

    /** The disc the CHDs were made from: data track as in the rcheevos vectors (smaller executable), then audio. */
    private fun ps1Bin(): ByteArray = rawSectors(psxBin("SLUS_007.45", 0x3800), 2) + audioSectors(12)

    /**
     * The same PlayStation data track as Mode 1 raw sectors with valid EDC and ECC (made with [CdEcc]); `chdman` only cuts
     * the sync and correction bytes out of sectors whose own check passes, so a CHD that reads back equal shows the
     * rebuild is the one `chdman` uses.
     */
    private fun mode1Bin(): ByteArray {
        val raw = rawSectors(psxBin("SLUS_007.45", 0x3800), 1)
        for (s in 0 until raw.size / 2352) {
            val at = s * 2352
            // EDC over sync, header and data (ECMA-130's polynomial 0xD8018001, reflected), stored after the data; 8 zero bytes; then P and Q.
            var edc = 0
            for (i in 0 until 2064) edc = (edc ushr 8) xor EDC_TABLE[(edc xor raw[at + i].toInt()) and 0xFF]
            for (i in 0 until 4) raw[at + 2064 + i] = (edc ushr (8 * i)).toByte()
            CdEcc.rebuild(raw, at)
        }
        return raw
    }

    private val EDC_TABLE = IntArray(256) { n ->
        var crc = n
        repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) -670990335 else 0) }
        crc
    }

    // PC Engine CD vector of `CdSystemHashesTest` (pceCdBin(72)), here as raw Mode 1 after 20 audio sectors and a 150-sector pregap.
    private val pceMd5 = "6565819195a49323e080e7539b54f251"

    fun pceBin(): ByteArray =
        audioSectors(20) + ByteArray(150 * 2352) + rawSectors(pceCdBin(72), 1, firstLba = 20 + 150)

    fun pceCue(): ByteArray =
        ("FILE \"pce.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "  TRACK 02 MODE1/2352\n    INDEX 00 00:00:20\n    INDEX 01 00:02:20\n").toByteArray()

    private fun ps1Cue(): ByteArray {
        val sectors = (0x3800 + 2047) / 2048 + 20
        val msf = "%02d:%02d:%02d".format(sectors / 75 / 60, sectors / 75 % 60, sectors % 75)
        return "FILE \"ps1.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n  TRACK 02 AUDIO\n    INDEX 01 $msf\n".toByteArray()
    }

    private fun hash(console: Int, name: String, bytes: ByteArray) = RaHasher.hashBlocking(console, MemorySource(name, bytes))
    private fun reasonOf(result: RaHashResult): Pair<RaCannotHashReason, String?> {
        if (result !is RaHashResult.CannotHash) fail("not a CannotHash: $result")
        val c = result as RaHashResult.CannotHash
        return c.reason to c.detail
    }

    // The MD5 of PlayStation boot file name + executable, worked out outside this code (Python, hashlib) from the bytes of
    // `ps1.bin`; the same script gives rcheevos' value db433fb038cde4fb15c144e8c7dea6e3 for the 514,048-byte executable
    // that `DiscHashesTest` pins, so it follows rcheevos' rule.
    private val ps1Md5 = "8455134c83efe322fcff68844fc0ff1b"
    private val segaMd5 = "574498e1453cb8934df60c4ab906e783"

    @Test
    fun theBinAndCueGiveTheIndependentValue() {
        val cue = Folder("ps1.cue" to ps1Cue(), "ps1.bin" to ps1Bin()).source("ps1.cue")
        assertEquals(RaHashResult.Hashed(ps1Md5), RaHasher.hashBlocking(RaConsoles.PLAYSTATION, cue))
    }

    @Test
    fun aCdlzChdHashesLikeItsBinAndCue() {
        assertEquals(RaHashResult.Hashed(ps1Md5), hash(RaConsoles.PLAYSTATION, "ps1.chd", fixture("ps1-cdlz.chd")))
    }

    @Test
    fun aCdzlChdHashesLikeItsBinAndCue() {
        assertEquals(RaHashResult.Hashed(ps1Md5), hash(RaConsoles.PLAYSTATION, "ps1.chd", fixture("ps1-cdzl.chd")))
    }

    @Test
    fun aChdInAPlaylistHashesToo() {
        val list = Folder("list.m3u" to "ps1.chd\n".toByteArray(), "ps1.chd" to fixture("ps1-cdlz.chd")).source("list.m3u")
        assertEquals(RaHashResult.Hashed(ps1Md5), RaHasher.hashBlocking(RaConsoles.PLAYSTATION, list))
    }

    @Test
    fun theTracksReadBackByteForByteLikeTheBin() {
        val bin = ps1Bin()
        val dataBytes = 27 * 2352
        for (name in listOf("ps1-cdlz.chd", "ps1-cdzl.chd")) {
            ChdImage.open(MemorySource(name, fixture(name)), {}).use { image ->
                val tracks = checkNotNull(image.cdTracks)
                assertEquals(listOf(1 to 27, 2 to 12), tracks.map { it.number to it.frames })
                val data = ByteArray(dataBytes)
                assertEquals(dataBytes, ChdTrackFile(image, tracks[0]).readAt(0, data, 0, data.size))
                assertArrayEquals("$name data track", bin.copyOfRange(0, dataBytes), data)
                // The audio track starts on the next multiple of four frames in the image, not where the cue says.
                assertEquals(28, tracks[1].firstFrame)
                val audio = ByteArray(12 * 2352)
                assertEquals(audio.size, ChdTrackFile(image, tracks[1]).readAt(0, audio, 0, audio.size))
                assertArrayEquals("$name audio track", bin.copyOfRange(dataBytes, bin.size), audio)
            }
        }
    }

    @Test
    fun aMode1ChdGetsItsErrorCorrectionBytesBack() {
        val bin = mode1Bin()
        val chd = fixture("ps1-mode1-cdlz.chd")
        assertEquals(RaHashResult.Hashed(ps1Md5), hash(RaConsoles.PLAYSTATION, "ps1.chd", chd))
        ChdImage.open(MemorySource("ps1.chd", chd), {}).use { image ->
            val data = ByteArray(bin.size)
            assertEquals(bin.size, ChdTrackFile(image, checkNotNull(image.cdTracks)[0]).readAt(0, data, 0, data.size))
            assertArrayEquals(bin, data)
        }
    }

    @Test
    fun hashingReadsOnlyTheHunksItNeeds() {
        val chd = fixture("ps1-cdlz.chd")
        val everything = CountingSource("ps1.chd", chd)
        ChdImage.open(everything, {}).use { image ->
            for (n in 0 until image.hunkCount) image.hunk(n)
            assertEquals(5, image.hunksDecoded)
        }
        val needed = CountingSource("ps1.chd", chd)
        assertEquals(RaHashResult.Hashed(ps1Md5), RaHasher.hashBlocking(RaConsoles.PLAYSTATION, needed))
        assertTrue("${needed.bytesRead} should be below ${everything.bytesRead}", needed.bytesRead < everything.bytesRead)

        // One sector: one hunk, and a second sector of the same hunk comes from the cache.
        ChdImage.open(MemorySource("ps1.chd", chd), {}).use { image ->
            val file = ChdTrackFile(image, checkNotNull(image.cdTracks)[0])
            val sector = ByteArray(2352)
            file.readAt(16L * 2352, sector, 0, sector.size)
            assertEquals(1, image.hunksDecoded)
            file.readAt(17L * 2352, sector, 0, sector.size)
            assertEquals(1, image.hunksDecoded)
            file.readAt(8L * 2352, sector, 0, sector.size)
            assertEquals(2, image.hunksDecoded)
        }
    }

    @Test
    fun rawChdsOfEveryCodecHashLikeTheIsoTheyHold() {
        for (name in listOf("sega-none.chd", "sega-zlib.chd", "sega-lzma.chd")) {
            assertEquals(name, RaHashResult.Hashed(segaMd5), hash(RaConsoles.SEGA_CD, name, fixture(name)))
        }
    }

    @Test
    fun hunksOfAnUnreadCodecNeedADecoder() {
        val (flac, flacMessage) = reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", fixture("ps1-cdfl.chd")))
        assertEquals(NeedsDecoder, flac)
        assertEquals("cdfl hunks need a decoder", flacMessage)
        val (zstd, zstdMessage) = reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", fixture("ps1-cdzs.chd")))
        assertEquals(NeedsDecoder, zstd)
        assertEquals("cdzs hunks need a decoder", zstdMessage)
        // The header names its compressors; one renamed to a codec that is not here is the same answer.
        val patched = fixture("ps1-cdlz.chd")
        "zstd".toByteArray().copyInto(patched, 16)
        assertEquals(NeedsDecoder to "zstd hunks need a decoder", reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", patched)))
    }

    @Test
    fun aChdThatNeedsItsParentNeedsADecoder() {
        // Hunk 0 of the uncompressed raw image becomes a parent reference: type 6 in the compressed-map form is not used
        // here, so use a compressed raw image whose first map symbol is rewritten is not practical; build the map instead.
        val chd = chdWithParentReference()
        val (reason, message) = reasonOf(hash(RaConsoles.SEGA_CD, "x.chd", chd))
        assertEquals(NeedsDecoder, reason)
        assertTrue(message, message!!.contains("parent"))
    }

    @Test
    fun aParentSelfEntryNeedsADecoderToo() {
        val (reason, message) = reasonOf(hash(RaConsoles.SEGA_CD, "x.chd", chdWithParentReference(11)))
        assertEquals(NeedsDecoder, reason)
        assertTrue(message, message!!.contains("parent"))
    }

    @Test
    fun aHeaderClaimingFarMoreHunksThanTheFileCanHoldIsBadImageBeforeAnythingIsAllocated() {
        val huge = fixture("ps1-cdlz.chd")
        // 1,900,000 hunks of 19,584 bytes in a 14 KB file.
        val logical = 1_900_000L * 19_584
        for (i in 0 until 8) huge[32 + i] = (logical shr (56 - 8 * i)).toByte()
        assertEquals(BadImage, reasonOf(hash(RaConsoles.PLAYSTATION, "x.chd", huge)).first)
    }

    @Test
    fun anUncompressedMapEntryOfZeroIsAHunkOfZeros() {
        val chd = fixture("sega-none.chd")
        // The map sits at 124 (one 4-byte entry per hunk, 4 hunks): point hunk 2 at offset 0.
        for (i in 0 until 4) chd[124 + 2 * 4 + i] = 0
        ChdImage.open(MemorySource("x.chd", chd), {}).use { image ->
            assertArrayEquals(ByteArray(2048), image.hunk(2))
            assertTrue(image.hunk(1).any { it != 0.toByte() })
        }
    }

    @Test
    fun anUncompressedMapEntryOfZeroInAChildChdIsInItsParent() {
        val chd = fixture("sega-none.chd")
        for (i in 0 until 4) chd[124 + 2 * 4 + i] = 0
        chd[104] = 1 // a parent SHA-1: this file is a child CHD
        ChdImage.open(MemorySource("x.chd", chd), {}).use { image ->
            val e = assertThrows(DiscFormatException::class.java) { image.hunk(2) }
            assertEquals(NeedsDecoder, e.reason)
        }
    }

    @Test
    fun aTrackWithAPregapInTheImageHashesLikeItsBinAndCue() {
        // PC Engine CD: an audio track, then the data track behind a 2-second pregap (INDEX 00 / INDEX 01 in the cue).
        val cue = Folder("pce.cue" to pceCue(), "pce.bin" to pceBin()).source("pce.cue")
        assertEquals(RaHashResult.Hashed(pceMd5), RaHasher.hashBlocking(RaConsoles.PC_ENGINE_CD, cue))
        for (name in listOf("pce-pregap-cdlz.chd", "pce-pregap-cdzl.chd")) {
            assertEquals(name, RaHashResult.Hashed(pceMd5), hash(RaConsoles.PC_ENGINE_CD, "pce.chd", fixture(name)))
        }
        ChdImage.open(MemorySource("pce.chd", fixture("pce-pregap-cdlz.chd")), {}).use { image ->
            val data = checkNotNull(image.cdTracks)[1]
            assertEquals(150 + 20, data.firstFrame) // the audio track's 20 frames, then the pregap
            assertEquals(224 - 150, data.frames) // chdman counts the pregap in FRAMES (224)
        }
    }

    @Test
    fun aDamagedHeaderIsBadImage() {
        val good = fixture("ps1-cdlz.chd")
        fun badImage(name: String, bytes: ByteArray) =
            assertEquals(name, BadImage, reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", bytes)).first)
        badImage("cut inside the header", good.copyOf(100))
        badImage("cut to a few bytes", good.copyOf(10))
        badImage("no magic", good.copyOf().also { it[0] = 'X'.code.toByte() })
        badImage("wrong header length", good.copyOf().also { it[11] = 99 })
        badImage("hunk size zero", good.copyOf().also { for (i in 56 until 60) it[i] = 0 })
        badImage("unit size that does not divide the hunk", good.copyOf().also { it[63] = 7 })
        badImage("map offset past the end", good.copyOf().also { it[44] = 0x7F })
        badImage("logical size zero", good.copyOf().also { for (i in 32 until 40) it[i] = 0 })
        badImage("cut inside the map", good.copyOf(good.size - 5))
        badImage("cut in the middle of the hunks", good.copyOf(300))
    }

    @Test
    fun aChangedMapFailsItsCheck() {
        val good = fixture("ps1-cdlz.chd")
        var mapAt = 0
        for (i in 0 until 8) mapAt = (mapAt shl 8) or (good[40 + i].toInt() and 0xFF)
        val damaged = good.copyOf().also { it[mapAt + 18] = (it[mapAt + 18].toInt() xor 0x55).toByte() }
        assertEquals(BadImage, reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", damaged)).first)
    }

    @Test
    fun aDamagedHunkIsBadImageNotAWrongHash() {
        val good = fixture("ps1-cdlz.chd")
        var mapAt = 0
        for (i in 0 until 8) mapAt = (mapAt shl 8) or (good[40 + i].toInt() and 0xFF)
        // The hunks run from the end of the metadata (offset 330) to the map; noise every 40 bytes lands in each of them.
        // Noise in a hunk the hash does not read (hunk 0 here is empty sectors) is not noticed: hunks are read lazily.
        val damaged = good.copyOf()
        for (at in 330 until mapAt step 40) damaged[at] = (damaged[at].toInt() xor 0x5A).toByte()
        assertEquals(BadImage, reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", damaged)).first)
    }

    @Test
    fun anOlderChdVersionIsNotRead() {
        val old = fixture("ps1-cdlz.chd").also { it[15] = 4 }
        assertEquals(NotYetSupported, reasonOf(hash(RaConsoles.PLAYSTATION, "ps1.chd", old)).first)
    }

    @Test
    fun aChdIsNotAGameCubeOrWiiDisc() {
        assertEquals(NeedsDecoder, reasonOf(hash(RaConsoles.GAMECUBE, "game.chd", fixture("ps1-cdlz.chd"))).first)
    }

    /**
     * A one-hunk raw CHD whose compressed map says "this hunk is in the parent". The map's Huffman table gives symbols 6
     * (parent) and 7 one bit each; the hunk's symbol is 6 (bit 0) followed by its 8-bit parent offset.
     */
    private fun chdWithParentReference(symbolOfHunk: Int = 6): ByteArray {
        val header = fixture("sega-none.chd").copyOf(124)
        for (i in 32 until 40) header[i] = 0
        header[39] = 0
        header[38] = 8 // logical size 2048: one hunk
        "zlib".toByteArray().copyInto(header, 16)
        for (i in 48 until 56) header[i] = 0 // no metadata
        header[47] = 124 // the map follows the header
        for (i in 40 until 47) header[i] = 0
        val bits = StringBuilder()
        // Two symbols of one bit each: the hunk's and 7; the lower symbol gets code 0.
        for (symbol in 0 until 16) bits.append(if (symbol == symbolOfHunk || symbol == 7) "00010001" else "0000")
        if (symbolOfHunk == 6) bits.append("0").append("00000000") else bits.append("1") // 6 (parent) carries its offset, 11 (parent-self) does not
        while (bits.length % 8 != 0) bits.append('0')
        val data = ByteArray(bits.length / 8) { bits.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        val entries = ByteArray(12).also { it[0] = 6 }
        val crc = ChdImage.crc16(entries)
        val mapHeader = ByteArray(16)
        mapHeader[3] = data.size.toByte()
        mapHeader[10] = (crc shr 8).toByte()
        mapHeader[11] = crc.toByte()
        mapHeader[14] = 8 // parent offset bits
        return header + mapHeader + data
    }

    private class CountingSource(override val fileName: String, private val bytes: ByteArray) : RaRomSource {
        override val key: String = fileName
        override val sizeBytes: Long get() = bytes.size.toLong()
        var bytesRead = 0L
            private set

        override fun open(): InputStream = object : FilterInputStream(java.io.ByteArrayInputStream(bytes)) {
            override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bytesRead += it }
        }
    }
}
