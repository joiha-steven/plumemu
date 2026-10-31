package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.FakeRoms.crcOf
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class FingerprintReaderTest {

    private val reader = FingerprintReader()

    private fun read(bytes: ByteArray, name: String, system: String?, depth: Depth = Depth.FULL): Fingerprints =
        reader.read(ByteArraySource(bytes), name, system, depth)

    @Test
    fun zipGivesTheRomEntryCrcFromTheDirectory() {
        val rom = FakeRoms.gba()
        val zip = FakeRoms.zip("readme.txt" to ByteArray(5000), "Test (USA).gba" to rom)
        val f = read(zip, "Test.zip", "gba")
        assertEquals(Container.ZIP, f.container)
        assertEquals("Test (USA).gba", f.innerName)
        assertEquals(2, f.entryCount)
        assertEquals(crcOf(rom), f.crc32)
        assertEquals(CrcSource.ZIP_DIRECTORY, f.crcSource)
        assertEquals(rom.size.toLong(), f.contentSize)
        // The entry is never unpacked, so its header code is not read.
        assertNull(f.productCode)
        assertTrue(f.notes.isEmpty())
    }

    @Test
    fun zipNotesHeadersAndByteOrdersItCannotFixAtQuick() {
        // At FULL these entries are unpacked and fixed (ZipEntryContentTest).
        val snes = read(FakeRoms.zip("x.smc" to FakeRoms.filler(512 + 1024)), "x.zip", "snes", Depth.QUICK)
        assertTrue(snes.notes.single().contains("512-byte header"))
        val n64 = read(FakeRoms.zip("x.v64" to FakeRoms.filler(4096)), "x.zip", "n64", Depth.QUICK)
        assertTrue(n64.notes.single().contains("byte-swapped"))
        val nes = read(FakeRoms.zip("x.nes" to FakeRoms.filler(16 + 40_960)), "x.zip", "nes", Depth.QUICK)
        assertTrue(nes.notes.single().contains("16-byte header"))
    }

    @Test
    fun zipWithOnlyJunkOrNothingHasNoCrc() {
        val junk = read(FakeRoms.zip("readme.txt" to ByteArray(10), "cover.jpg" to ByteArray(10)), "x.zip", null)
        assertNull(junk.crc32)
        assertEquals(2, junk.entryCount)
        assertTrue(junk.notes.single().contains("no ROM entry"))
        val empty = read(FakeRoms.zip(), "x.zip", null)
        assertEquals(Container.ZIP, empty.container)
        assertEquals(0, empty.entryCount)
        assertNull(empty.fixKey())
    }

    @Test
    fun arcadeZipsShowManyEntries() {
        val entries = (1..30).map { "chip$it.rom" to FakeRoms.filler(100, it.toLong()) }.toTypedArray()
        assertEquals(30, read(FakeRoms.zip(*entries), "zzzgame.zip", "arcade").entryCount)
    }

    @Test
    fun corruptZipIsAZipWithANote() {
        val f = read(FakeRoms.filler(5000), "broken.zip", "gba")
        assertEquals(Container.ZIP, f.container)
        assertNull(f.crc32)
        assertTrue(f.notes.single().contains("no end of central directory"))
    }

    @Test
    fun sevenZipWithABrokenSignatureHeaderIsANote() {
        // Readable 7z archives are in SevenZipDirectoryTest; here the signature header's CRC is wrong.
        val bytes = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4) + FakeRoms.filler(10_000)
        val counting = CountingSource(ByteArraySource(bytes))
        val f = reader.read(counting, "x.7z", "snes", Depth.FULL)
        assertEquals(Container.SEVEN_ZIP, f.container)
        assertNull(f.crc32)
        assertEquals("7z: signature header CRC does not match", f.notes.single())
        assertEquals(8L + 32, counting.bytesRead)
    }

    @Test
    fun unknownJunkGetsOnlyAWholeFileCrc() {
        val junk = FakeRoms.filler(3000, seed = 11)
        val f = read(junk, "mystery.bin", null)
        assertEquals(Container.NONE, f.container)
        assertEquals(crcOf(junk), f.crc32)
        assertNull(f.productCode)
        assertTrue(f.notes.isEmpty())
        val quick = read(junk, "mystery.bin", null, Depth.QUICK)
        assertNull(quick.crc32)
        assertNull(quick.fixKey())
    }

    @Test
    fun emptyAndTinyFilesDoNotThrow() {
        for (bytes in listOf(ByteArray(0), ByteArray(3))) {
            for (name in listOf("x.gba", "x.zip", "x.sfc", "x.n64", "x.smd")) {
                val f = read(bytes, name, null)
                assertEquals(bytes.size.toLong(), f.size)
                assertTrue("$name: ${f.notes}", f.notes.none { it.startsWith("read failed") })
            }
        }
    }

    @Test
    fun quickReadsOnlyAFewKilobytes() {
        val cases = listOf(
            Triple(FakeRoms.snes(size = 4 shl 20), "big.sfc", "snes"),
            Triple(FakeRoms.gba(size = 16 shl 20), "big.gba", "gba"),
            Triple(FakeRoms.n64(size = 8 shl 20), "big.z64", "n64"),
            Triple(FakeRoms.toSmd(FakeRoms.megaDrive(size = 4 shl 20)), "big.smd", "megadrive"),
            Triple(FakeRoms.zip("big.sfc" to FakeRoms.snes(size = 4 shl 20)), "big.zip", "snes"),
            Triple(FakeRoms.zip("big.sfc" to FakeRoms.snes(size = 4 shl 20), comment = "c".repeat(5000)), "comment.zip", "snes"),
            Triple(FakeRoms.zip("big.sfc" to FakeRoms.snes(size = 4 shl 20), comment = "c".repeat(60_000)), "longcomment.zip", "snes"),
        )
        for ((bytes, name, system) in cases) {
            val counting = CountingSource(ByteArraySource(bytes))
            reader.read(counting, name, system, Depth.QUICK)
            println("QUICK $name (${bytes.size} bytes): read ${counting.bytesRead} bytes in ${counting.readCalls} calls")
            assertTrue("$name read ${counting.bytesRead}", counting.bytesRead < 64 * 1024 + 2048)
        }
        val snes = CountingSource(ByteArraySource(FakeRoms.snes(size = 4 shl 20)))
        reader.read(snes, "big.sfc", "snes", Depth.QUICK)
        assertTrue(snes.bytesRead < 1024)
        // A zip without a long comment costs its last 1 KB and its directory.
        val zip = CountingSource(ByteArraySource(FakeRoms.zip("big.gba" to FakeRoms.gba(size = 4 shl 20))))
        reader.read(zip, "big.zip", "gba", Depth.FULL)
        assertTrue("zip read ${zip.bytesRead}", zip.bytesRead < 2048)
        val smd = CountingSource(ByteArraySource(FakeRoms.toSmd(FakeRoms.megaDrive(size = 4 shl 20))))
        reader.read(smd, "big.smd", "megadrive", Depth.QUICK)
        assertTrue("smd read ${smd.bytesRead}", smd.bytesRead <= 8 + 512 + 256)
    }

    @Test
    fun fullSkipsFilesOverTheLimit() {
        val small = FingerprintReader(wholeFileCrcLimit = 1000)
        val f = small.read(ByteArraySource(FakeRoms.filler(2000)), "x.bin", null, Depth.FULL)
        assertNull(f.crc32)
        assertTrue(f.notes.single().contains("over the 1000-byte limit"))
        assertEquals(crcOf(FakeRoms.filler(1000)), small.read(ByteArraySource(FakeRoms.filler(1000)), "x.bin", null, Depth.FULL).crc32)
    }

    @Test
    fun fixKeysListEveryKeyStrongestFirst() {
        val f = Fingerprints(size = 10, container = Container.NONE, contentSize = 1234, crc32 = 0xABCL, productCode = "ZZZE", codeKind = CodeKind.GBA)
        assertEquals(listOf("crc:00000abc:1234", "code:GBA:ZZZE"), f.fixKeys())
        assertEquals("crc:00000abc:1234", f.fixKey())
        assertEquals(listOf("code:GBA:ZZZE"), f.copy(crc32 = null).fixKeys())
        assertTrue(f.copy(crc32 = null, productCode = null).fixKeys().isEmpty())
        assertNull(f.copy(crc32 = null, productCode = null).fixKey())
        assertEquals("crc:ffffffff:1", f.copy(crc32 = 0xFFFFFFFFL, contentSize = 1, productCode = null).fixKey())
    }

    @Test
    fun aQuickAndAFullReadShareTheCodeKey() {
        val quick = read(FakeRoms.n64(), "x.z64", "n64", Depth.QUICK).fixKeys()
        val full = read(FakeRoms.n64(), "x.z64", "n64", Depth.FULL).fixKeys()
        assertEquals(listOf("code:N64:NZZE"), quick)
        assertEquals(2, full.size)
        assertTrue(full.first().startsWith("crc:"))
        assertTrue(full.containsAll(quick))
    }

    @Test
    fun emptyContentAndDummyCodesGiveNoKey() {
        val base = Fingerprints(size = 0, container = Container.NONE, contentSize = 0, crc32 = 0L)
        assertTrue(base.fixKeys().isEmpty())
        val dummies = listOf("0000", "XXXX", "####", "", "   ", "00000000", "GM 00000000-00", "SLUS-00000", "--")
        for (code in dummies) {
            assertTrue(code, base.copy(productCode = code, codeKind = CodeKind.MEGADRIVE).fixKeys().isEmpty())
        }
        val real = listOf("ZZZE", "A00E", "T-01", "00001009", "GM 00001009-00", "NZ")
        for (code in real) {
            assertEquals(code, listOf("code:MEGADRIVE:$code"), base.copy(productCode = code, codeKind = CodeKind.MEGADRIVE).fixKeys())
        }
        // An empty loose file read FULL has a CRC but no key.
        assertTrue(read(ByteArray(0), "x.bin", null).fixKeys().isEmpty())
    }

    @Test
    fun sourceFailuresAreRethrownAsIOException() {
        val failing = object : RandomAccessSource {
            override val size = 1L shl 20
            override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
                if (position >= 0x100) throw IOException("card removed")
                return ByteArraySource(FakeRoms.gba()).readAt(position, dst, offset, length)
            }
            override fun close() = Unit
        }
        assertThrows(IOException::class.java) { reader.read(failing, "x.gba", "gba", Depth.FULL) }
        assertThrows(IOException::class.java) { reader.read(failing, "x.zip", null, Depth.QUICK) }
    }

    @Test
    fun oneReaderServesManyThreads() {
        val files = listOf(FakeRoms.gba() to "x.gba", FakeRoms.n64() to "x.z64", FakeRoms.megaDrive() to "x.md")
        val expected = files.map { (bytes, name) -> read(bytes, name, null) }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 48).map { i -> Callable { read(files[i % 3].first, files[i % 3].second, null) } }
            pool.invokeAll(tasks).forEachIndexed { i, result -> assertEquals(expected[i % 3], result.get()) }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun contentReadSystemsAreCartridgeSystemsAndZipsOnlyWhereAHeaderMayHide() {
        val systems = FingerprintReader.contentReadSystems()
        for (id in listOf("nes", "snes", "gb", "gbc", "gba", "n64", "megadrive", "atarilynx")) assertTrue(id, id in systems.loose)
        for (id in listOf("nes", "snes", "n64", "megadrive", "atari7800", "atarilynx")) assertTrue(id, id in systems.zipped)
        // A Game Boy or GBA zip's directory CRC is already the content's; discs and arcade gain nothing.
        for (id in listOf("gb", "gbc", "gba")) assertTrue(id, id !in systems.zipped)
        for (id in listOf("psx", "ps2", "arcade", "mame", "switch")) assertTrue(id, id !in systems.loose && id !in systems.zipped)
    }

    @Test
    fun readerLessCartridgeSystemsWithAPackAreReadLooseButNeverDiscsOrComputers() {
        val systems = FingerprintReader.contentReadSystems()
        for (id in listOf("atari2600", "pcengine", "mastersystem", "gamegear", "atarilynx", "wonderswan", "ngp", "colecovision", "videopac", "atari5200", "satellaview", "sufami")) {
            assertTrue(id, id in systems.loose)
        }
        // Their zip entries need nothing: the directory CRC already is the content's.
        for (id in listOf("atari2600", "pcengine", "mastersystem", "wonderswan")) assertTrue(id, id !in systems.zipped)
        for (id in listOf("psx", "saturn", "dreamcast", "megacd", "pcenginecd", "neogeocd", "c64", "msx", "amiga", "atarist")) {
            assertTrue(id, id !in systems.loose)
        }
    }

    @Test
    fun everyReaderLessCartridgeSystemHasABundledPack() {
        com.plumemu.identify.pack.PackAssets.assumePacks()
        val lines = java.io.File("src/main/assets/identify/packs.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        // An alias line ("alias videopac odyssey2") sends the system to another system's pack.
        val index = lines.map { if (it.startsWith("alias ")) it.split(' ')[1] else it.substringBefore(' ') }.toSet()
        for (id in FingerprintReader.CRC_ONLY_CARTRIDGE_SYSTEMS) assertTrue("no bundled pack for $id", id in index)
    }
}
