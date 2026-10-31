package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.FakeRoms
import com.plumemu.identify.fingerprint.FakeRoms.crcOf
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.source.ByteArraySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartridgeFingerprintTest {

    private val reader = FingerprintReader()

    private fun read(bytes: ByteArray, name: String, system: String?, depth: Depth = Depth.FULL): Fingerprints =
        reader.read(ByteArraySource(bytes), name, system, depth)

    @Test
    fun gbaGameCodeAndTitle() {
        val f = read(FakeRoms.gba(), "x.gba", "gba")
        assertEquals("ZZZE", f.productCode)
        assertEquals(CodeKind.GBA, f.codeKind)
        assertEquals("TEST GAME", f.internalTitle)
        assertEquals(crcOf(FakeRoms.gba()), f.crc32)
        assertEquals(CrcSource.WHOLE_FILE, f.crcSource)
    }

    @Test
    fun gbaWithABadHeaderCheckGivesNoCode() {
        val f = read(FakeRoms.gba(goodCheck = false), "x.gba", "gba")
        assertNull(f.productCode)
        assertNull(f.internalTitle)
        assertTrue(f.notes.toString(), f.notes.single().contains("no valid gba/gb header"))
    }

    @Test
    fun ndsGameCodeOnlyWithAGoodHeaderCrc() {
        val good = read(FakeRoms.nds(), "x.nds", "nds", Depth.QUICK)
        assertEquals("ZZZE", good.productCode)
        assertEquals(CodeKind.NDS, good.codeKind)
        assertEquals("TESTDS", good.internalTitle)
        assertNull(read(FakeRoms.nds(goodCrc = false), "x.nds", "ndsi", Depth.QUICK).productCode)
    }

    @Test
    fun gameBoyTitleAndColorManufacturerCode() {
        val mono = read(FakeRoms.gb(), "x.gb", "gb", Depth.QUICK)
        assertEquals("TEST GB GAME", mono.internalTitle)
        assertNull(mono.productCode)
        val color = read(FakeRoms.gb(title = "TESTCOLOR", colorCode = "BZZE"), "x.gbc", "gbc", Depth.QUICK)
        assertEquals("TESTCOLOR", color.internalTitle)
        assertEquals("BZZE", color.productCode)
        assertEquals(CodeKind.GB, color.codeKind)
        // The gba system also lists Game Boy files; its reader list falls through to Game Boy.
        assertEquals("TEST GB GAME", read(FakeRoms.gb(), "x.gb", "gba", Depth.QUICK).internalTitle)
    }

    @Test
    fun n64InAllThreeByteOrdersGivesOneCodeAndOneCrc() {
        val z64 = FakeRoms.n64()
        val expectedCrc = crcOf(z64)
        val files = mapOf(
            "x.z64" to z64,
            "x.v64" to FakeRoms.byteSwapped(z64),
            "x.n64" to FakeRoms.wordSwapped(z64),
        )
        for ((name, bytes) in files) {
            val f = read(bytes, name, "n64")
            assertEquals(name, "NZZE", f.productCode)
            assertEquals(name, CodeKind.N64, f.codeKind)
            assertEquals(name, "TEST SIXTYFOUR", f.internalTitle)
            assertEquals(name, expectedCrc, f.crc32)
            assertEquals(name, z64.size.toLong(), f.contentSize)
        }
    }

    @Test
    fun megaDriveAndItsSmdCopyGiveOneSerialAndOneCrc() {
        val plain = FakeRoms.megaDrive()
        val smd = FakeRoms.toSmd(plain)
        val a = read(plain, "x.md", "megadrive")
        val b = read(smd, "x.smd", "genesis")
        for (f in listOf(a, b)) {
            assertEquals("00001009", f.productCode)
            assertEquals(CodeKind.MEGADRIVE, f.codeKind)
            assertEquals("TEST MEGA GAME", f.internalTitle)
            assertEquals(crcOf(plain), f.crc32)
            assertEquals(plain.size.toLong(), f.contentSize)
        }
        assertEquals(0, a.headerBytesSkipped)
        assertEquals(512, b.headerBytesSkipped)
    }

    @Test
    fun megaDriveSerialIsCutToTheProductCode() {
        val f = read(FakeRoms.megaDrive(serial = " gm  T-01 -00"), "x.gen", "sega32x", Depth.QUICK)
        assertEquals("T-01", f.productCode)
        val cases = mapOf(
            "GM MK-1401 -00" to "MK-1401",
            "GM 00001009-00" to "00001009",
            "GM T-50016 -00" to "T-50016",
            "AI T-01  -01" to "T-01",
            "GM T-01" to "T-01",
            "MK-1401-00" to "MK-1401",
            "GM G-4001" to "G-4001",
            "GM 00000000-00" to "00000000",
            "GM -00" to "-00",
            // The version lost its hyphen: the 8-byte product field is cut by position.
            "GM MK-1401  00" to "MK-1401",
        )
        for ((serial, code) in cases) {
            assertEquals(serial, code, read(FakeRoms.megaDrive(serial = serial), "x.md", "megadrive", Depth.QUICK).productCode)
        }
        val dummy = read(FakeRoms.megaDrive(serial = "GM 00000000-00"), "x.md", "megadrive", Depth.QUICK)
        assertEquals(emptyList<String>(), dummy.fixKeys())
    }

    @Test
    fun snesWithAndWithoutCopierHeaderGiveOneCrc() {
        for (hiRom in listOf(false, true)) {
            val rom = FakeRoms.snes(size = 1 shl 20, hiRom = hiRom)
            val plain = read(rom, "x.sfc", "snes")
            val headered = read(FakeRoms.withHeader(FakeRoms.copierHeader(), rom), "x.smc", "sfc")
            assertEquals(crcOf(rom), plain.crc32)
            assertEquals(crcOf(rom), headered.crc32)
            assertEquals(512, headered.headerBytesSkipped)
            assertEquals(rom.size.toLong(), headered.contentSize)
            for (f in listOf(plain, headered)) {
                assertEquals("TEST SUPER GAME", f.internalTitle)
                assertEquals("ZZZE", f.productCode)
                assertEquals(CodeKind.SNES, f.codeKind)
            }
        }
    }

    @Test
    fun snesWithoutAnInternalHeaderStillSkipsTheCopierHeader() {
        val rom = FakeRoms.filler(1 shl 18, seed = 3)
        val f = read(FakeRoms.withHeader(FakeRoms.copierHeader(), rom), "x.smc", "snes")
        assertEquals(crcOf(rom), f.crc32)
        assertNull(f.internalTitle)
        assertTrue(f.notes.single().contains("no valid internal header"))
    }

    @Test
    fun snesReaderReturnsNullWithoutAHeaderButTheSkipStaysAvailable() {
        assertNull(SnesReader.read(ByteArraySource(FakeRoms.filler(1 shl 18, seed = 3))))
        assertEquals(512, SnesReader.skipWithoutHeader(512L + (1 shl 18)))
        assertEquals(0, SnesReader.skipWithoutHeader(1L shl 18))
        val bare = read(FakeRoms.filler(1 shl 18, seed = 3), "x.sfc", "snes")
        assertEquals(0, bare.headerBytesSkipped)
        assertTrue(bare.notes.single().contains("no valid snes header"))
    }

    @Test
    fun crc16ModbusMatchesTheStandardCheckValue() {
        val check = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x4B37, NdsReader.crc16Modbus(check, 0, check.size))
    }

    @Test
    fun gbaComplementCheckMatchesAHandComputedHeader() {
        // 0xA0-0xBC: "TEST GAME" + 3 NUL, "ZZZE", "01", 0x96, ten zeros. Byte sum 1220;
        // -(1220 + 0x19) mod 256 = 0x23 (GBATEK).
        val header = ByteArray(0xC0)
        "TEST GAME".forEachIndexed { i, c -> header[0xA0 + i] = c.code.toByte() }
        "ZZZE01".forEachIndexed { i, c -> header[0xAC + i] = c.code.toByte() }
        header[0xB2] = 0x96.toByte()
        header[0xBD] = 0x23
        assertEquals("ZZZE", GbaReader.read(ByteArraySource(header))?.productCode)
        header[0xBD] = 0x24
        assertNull(GbaReader.read(ByteArraySource(header)))
    }

    @Test
    fun nesWithAndWithoutInesHeaderGiveOneCrc() {
        val content = FakeRoms.filler(40_960)
        val headered = read(FakeRoms.withHeader(FakeRoms.inesHeader(), content), "x.nes", "nes")
        val bare = read(content, "x.nes", "famicom")
        assertEquals(crcOf(content), headered.crc32)
        assertEquals(crcOf(content), bare.crc32)
        assertEquals(16, headered.headerBytesSkipped)
        assertEquals(0, bare.headerBytesSkipped)
        assertTrue(headered.notes.isEmpty())
    }

    @Test
    fun nesTrainerStaysInTheCrcWithANote() {
        val content = FakeRoms.filler(512 + 40_960)
        val f = read(FakeRoms.withHeader(FakeRoms.inesHeader(trainer = true), content), "x.nes", "nes")
        assertEquals(crcOf(content), f.crc32)
        assertTrue(f.notes.single().contains("trainer"))
    }

    @Test
    fun fdsAtari7800AndLynxHeadersAreSkipped() {
        val disk = FakeRoms.filler(65_500)
        val fds = read(FakeRoms.withHeader(FakeRoms.fdsHeader(), disk), "x.fds", "nes")
        assertEquals(crcOf(disk), fds.crc32)
        assertEquals(16, fds.headerBytesSkipped)

        val cart = FakeRoms.filler(32_768)
        val a78 = read(FakeRoms.withHeader(FakeRoms.a78Header(), cart), "x.a78", "atari7800")
        assertEquals(crcOf(cart), a78.crc32)
        assertEquals(128, a78.headerBytesSkipped)
        assertEquals("TEST SEVEN", a78.internalTitle)

        val lynx = read(FakeRoms.withHeader(FakeRoms.lynxHeader(), cart), "x.lnx", "lynx")
        assertEquals(crcOf(cart), lynx.crc32)
        assertEquals(64, lynx.headerBytesSkipped)
        assertEquals("TEST LYNX", lynx.internalTitle)
    }

    @Test
    fun readersArePickedBySystemThenExtension() {
        assertEquals(listOf(GbaReader, GameBoyReader), CartridgeReaders.candidates("gba", "gba"))
        assertEquals(listOf(GameBoyReader, GbaReader), CartridgeReaders.candidates("GBA", "gb"))
        assertEquals(listOf(FdsReader, NesReader), CartridgeReaders.candidates("nes", "fds"))
        assertEquals(listOf(GameBoyReader), CartridgeReaders.candidates("sgb", "sgb"))
        assertEquals(listOf(Nintendo64Reader), CartridgeReaders.candidates(null, "v64"))
        assertTrue(CartridgeReaders.candidates(null, "bin").isEmpty())
        assertTrue(CartridgeReaders.candidates("psx", "gba").isEmpty())
    }

    @Test
    fun unknownSystemReadsNoHeaderButStillHashes() {
        val rom = FakeRoms.gba()
        val f = read(rom, "x.gba", "psx")
        assertNull(f.productCode)
        assertEquals(0, f.headerBytesSkipped)
        assertEquals(crcOf(rom), f.crc32)
        assertTrue(f.notes.isEmpty())
        assertEquals("ZZZE", read(rom, "x.gba", null).productCode)
    }

    @Test
    fun nonAsciiTitlesAreDroppedNotGarbled() {
        val rom = FakeRoms.n64()
        rom[0x20] = 0x83.toByte()
        val f = read(rom, "x.z64", "n64", Depth.QUICK)
        assertNull(f.internalTitle)
        assertEquals("NZZE", f.productCode)
    }
}
