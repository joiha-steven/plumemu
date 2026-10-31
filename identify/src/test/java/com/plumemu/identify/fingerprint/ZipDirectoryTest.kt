package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.FakeRoms.crcOf
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ZipDirectoryTest {

    private fun list(bytes: ByteArray, limit: Int = ZipDirectory.DEFAULT_DIRECTORY_LIMIT) =
        ZipDirectory(limit).read(ByteArraySource(bytes))

    @Test
    fun listsEntriesWithCrcAndSizesFromTheDirectory() {
        val rom = FakeRoms.filler(40_000)
        val zip = FakeRoms.zip("readme.txt" to "hello".toByteArray(), "games/" to ByteArray(0), "games/test.sfc" to rom)
        val listing = list(zip)
        assertEquals(listOf("readme.txt", "games/", "games/test.sfc"), listing.entries.map { it.name })
        val entry = listing.entries[2]
        assertEquals(crcOf(rom), entry.crc32)
        assertEquals(40_000L, entry.uncompressedSize)
        assertTrue(entry.compressedSize > 0)
        assertTrue(listing.entries[1].isDirectory)
        assertTrue(listing.notes.isEmpty())
    }

    @Test
    fun picksTheLargestEntryThatIsNotJunk() {
        val entries = listOf(
            ZipEntryInfo("cover.png", 1, 9_000_000, 9_000_000, false),
            ZipEntryInfo("__MACOSX/._game.gba", 2, 4_000_000, 4_000_000, false),
            ZipEntryInfo("folder/", 0, 0, 0, true),
            ZipEntryInfo("game.gba", 3, 100, 1_000_000, false),
            ZipEntryInfo("game (alt).gba", 4, 100, 1_000_000, false),
            ZipEntryInfo("info.NFO", 5, 10, 10, false),
        )
        assertEquals("game.gba", pickRomEntry(entries)?.name)
        assertEquals(5, countFiles(entries))
        assertNull(pickRomEntry(entries.filter { it.name.endsWith(".png") || it.isDirectory }))
    }

    @Test
    fun findsTheEndRecordBehindALongComment() {
        val rom = FakeRoms.filler(1000)
        val zip = FakeRoms.zip("a.nes" to rom, comment = "c".repeat(65_000))
        val listing = list(zip)
        assertEquals(listOf("a.nes"), listing.entries.map { it.name })
        assertEquals(crcOf(rom), listing.entries[0].crc32)
    }

    @Test
    fun readsZip64SizesAndRecords() {
        val zip = handBuiltZip64(name = "huge.iso", crc = 0x89ABCDEFL, size = 5_000_000_000L)
        val listing = list(zip)
        assertEquals(1, listing.entries.size)
        val entry = listing.entries[0]
        assertEquals("huge.iso", entry.name)
        assertEquals(0x89ABCDEFL, entry.crc32)
        assertEquals(5_000_000_000L, entry.uncompressedSize)
        assertEquals(4_000_000_000L, entry.compressedSize)
        assertTrue(listing.notes.toString(), listing.notes.isEmpty())
    }

    @Test
    fun decodesUtf8NamesAndTreatsOthersAsLatin1() {
        val utf8 = list(FakeRoms.zip("caf\u00e9 \u65e5.sfc" to ByteArray(10)))
        assertEquals("caf\u00e9 \u65e5.sfc", utf8.entries[0].name)
        // Without the UTF-8 flag a name is CP437 in the format; decoded as ISO-8859-1 here.
        val latin = list(FakeRoms.zip("caf\u00e9.sfc" to ByteArray(10), charset = Charsets.ISO_8859_1))
        assertEquals("caf\u00e9.sfc", latin.entries[0].name)
    }

    @Test
    fun aZipWithDataInFrontIsStillRead() {
        val rom = FakeRoms.filler(500)
        val zip = ByteArray(1000) + FakeRoms.zip("x.gba" to rom)
        val listing = list(zip)
        assertEquals(crcOf(rom), listing.entries.single().crc32)
        assertTrue(listing.notes.single().contains("1000 bytes before the archive"))
    }

    @Test
    fun anEmptyZipHasNoEntriesAndSaysSo() {
        val listing = list(FakeRoms.zip())
        assertTrue(listing.entries.isEmpty())
        assertTrue(listing.notes.single().contains("lists no entries"))
    }

    @Test
    fun anEndRecordSignatureInsideTheCommentIsNotTaken() {
        val rom = FakeRoms.filler(1000)
        val zip = FakeRoms.zip("a.gba" to rom, comment = "PK\u0005\u0006" + "\u0000".repeat(18))
        val listing = list(zip)
        assertEquals(listOf("a.gba"), listing.entries.map { it.name })
        assertEquals(crcOf(rom), listing.entries[0].crc32)
        assertTrue(listing.notes.toString(), listing.notes.isEmpty())
        // The same trap with a fake that claims one entry at the start of the file.
        val fakeWithEntry = "PK\u0005\u0006" + "\u0000".repeat(4) + "\u0001\u0000\u0001\u0000" + "\u002e\u0000\u0000\u0000" +
            "\u0000".repeat(6)
        val second = list(FakeRoms.zip("b.gba" to rom, comment = fakeWithEntry))
        assertEquals(listOf("b.gba"), second.entries.map { it.name })
    }

    @Test
    fun anEmptyEndRecordInsideTheCommentIsNotTaken() {
        val rom = FakeRoms.filler(1000)
        val zip = FakeRoms.zip("a.gba" to rom, comment = "x".repeat(2000))
        // About 30 bytes before the end: an end record listing nothing that points at itself.
        val fake = zip.size - 30
        putBytes(zip, fake, 0x50, 0x4B, 0x05, 0x06)
        putU32(zip, fake + 4, 0)
        putU32(zip, fake + 8, 0)
        putU32(zip, fake + 12, 0)
        putU32(zip, fake + 16, fake)
        putBytes(zip, fake + 20, 0, 0)
        val listing = list(zip)
        assertEquals(listOf("a.gba"), listing.entries.map { it.name })
        assertEquals(crcOf(rom), listing.entries[0].crc32)
        assertTrue(listing.notes.toString(), listing.notes.isEmpty())
    }

    @Test
    fun anEndRecordEndingExactlyAtTheFileEndWinsOverAPlausibleOneInTheComment() {
        val rom = FakeRoms.filler(1000)
        val zip = FakeRoms.zip("a.gba" to rom, comment = "x".repeat(2000))
        // Inside the comment, in the last 1 KB: a complete directory entry for "fake.gba" and an
        // end record pointing at it, whose own (empty) comment ends 30 bytes before the file end.
        // It passes every check except "ends exactly at the file end", and it is the first
        // candidate a search from the end meets.
        val entryName = "fake.gba"
        val entry = zip.size - 30 - 22 - (46 + entryName.length)
        putBytes(zip, entry, 0x50, 0x4B, 0x01, 0x02)
        for (i in 4 until 46) zip[entry + i] = 0
        putU32(zip, entry + 16, 0x1234)
        putBytes(zip, entry + 28, entryName.length, 0)
        entryName.forEachIndexed { i, c -> zip[entry + 46 + i] = c.code.toByte() }
        val fake = entry + 46 + entryName.length
        putBytes(zip, fake, 0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 1, 0, 1, 0)
        putU32(zip, fake + 12, 46 + entryName.length)
        putU32(zip, fake + 16, entry)
        putBytes(zip, fake + 20, 0, 0)
        assertEquals(listOf("a.gba"), list(zip).entries.map { it.name })
    }

    @Test
    fun aZipWithBytesAppendedIsFoundAfterTheWholeWindow() {
        val rom = FakeRoms.filler(1000)
        val zip = FakeRoms.zip("a.gba" to rom) + ByteArray(100)
        assertEquals(listOf("a.gba"), list(zip).entries.map { it.name })
    }

    @Test
    fun theEndRecordSearchWidensInSteps() {
        val zip = FakeRoms.zip("a.gba" to FakeRoms.filler(100_000), comment = "c".repeat(5000))
        val counting = CountingSource(ByteArraySource(zip))
        ZipDirectory().read(counting)
        // 1 KB, then the 7 KB in front of it, then the directory: no byte read twice, no 64 KB window.
        assertTrue("read ${counting.bytesRead}", counting.bytesRead < 8 * 1024 + 200)
        assertEquals(1, ZipDirectory().read(ByteArraySource(zip)).entries.size)
    }

    @Test
    fun brokenZipsGiveNotesNotExceptions() {
        val good = FakeRoms.zip("a.gba" to FakeRoms.filler(3000), "b.gba" to FakeRoms.filler(2000))
        val cutBeforeEnd = good.copyOf(good.size - 30)
        assertTrue(list(cutBeforeEnd).notes.single().contains("no end of central directory"))

        val directoryStart = indexOf(good, byteArrayOf(0x50, 0x4B, 0x01, 0x02))
        val badSignature = good.copyOf().also { it[directoryStart] = 0 }
        val bad = list(badSignature)
        assertTrue(bad.entries.isEmpty())
        assertTrue(bad.notes.first().contains("bad signature"))

        val secondEntry = indexOf(good, byteArrayOf(0x50, 0x4B, 0x01, 0x02), directoryStart + 4)
        val badSecond = good.copyOf().also { it[secondEntry] = 0 }
        assertEquals(listOf("a.gba"), list(badSecond).entries.map { it.name })

        val endStart = indexOf(good, byteArrayOf(0x50, 0x4B, 0x05, 0x06))
        val offsetPastEnd = good.copyOf().also { putU32(it, endStart + 16, 0x7FFFFFF0) }
        assertTrue(list(offsetPastEnd).notes.single().contains("truncated or corrupt"))

        val nameTooLong = good.copyOf().also { it[directoryStart + 29] = 0x7F }
        assertTrue(list(nameTooLong).notes.first().contains("cut off"))

        assertTrue(list(FakeRoms.filler(100_000)).notes.single().contains("no end of central directory"))
        assertTrue(list(ByteArray(0)).notes.single().contains("no end of central directory"))
    }

    @Test
    fun aHugeDirectoryIsReadOnlyUpToTheLimit() {
        val entries = (1..20).map { "rom$it.bin" to ByteArray(10) }.toTypedArray()
        val zip = FakeRoms.zip(*entries)
        val listing = list(zip, limit = 300)
        assertTrue(listing.entries.size in 1..5)
        assertTrue(listing.notes.first().contains("read only the first 300"))
        val counting = CountingSource(ByteArraySource(zip))
        ZipDirectory(300).read(counting)
        // The tail, the 20 bytes where a Zip64 locator would sit, and the capped directory.
        assertTrue(counting.bytesRead <= zip.size.coerceAtMost(ZipDirectory.MAX_END_SEARCH) + 20 + 300)
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray, from: Int = 0): Int =
        (from..bytes.size - pattern.size).first { at -> pattern.indices.all { bytes[at + it] == pattern[it] } }

    private fun putBytes(bytes: ByteArray, at: Int, vararg values: Int) {
        values.forEachIndexed { i, v -> bytes[at + i] = v.toByte() }
    }

    private fun putU32(bytes: ByteArray, at: Int, value: Int) {
        ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    /** A one-entry Zip64 archive: fake local data, a directory entry with a Zip64 extra field, Zip64 records. */
    private fun handBuiltZip64(name: String, crc: Long, size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int, value: Long) = repeat(n) { out.write(((value ushr (8 * it)) and 0xFF).toInt()) }
        out.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(60))
        val directoryOffset = out.size().toLong()
        le(4, 0x02014b50); le(2, 45); le(2, 45); le(2, 0); le(2, 0); le(4, 0)
        le(4, crc); le(4, 0xFFFFFFFFL); le(4, 0xFFFFFFFFL)
        le(2, name.length.toLong()); le(2, 20); le(2, 0); le(2, 0); le(2, 0); le(4, 0); le(4, 0)
        out.write(name.toByteArray())
        le(2, 0x0001); le(2, 16); le(8, size); le(8, 4_000_000_000L)
        val directorySize = out.size() - directoryOffset
        val recordOffset = out.size().toLong()
        le(4, 0x06064b50); le(8, 44); le(2, 45); le(2, 45); le(4, 0); le(4, 0)
        le(8, 1); le(8, 1); le(8, directorySize); le(8, directoryOffset)
        le(4, 0x07064b50); le(4, 0); le(8, recordOffset); le(4, 1)
        le(4, 0x06054b50); le(2, 0); le(2, 0); le(2, 0xFFFF); le(2, 0xFFFF)
        le(4, 0xFFFFFFFFL); le(4, 0xFFFFFFFFL); le(2, 0)
        return out.toByteArray()
    }
}
