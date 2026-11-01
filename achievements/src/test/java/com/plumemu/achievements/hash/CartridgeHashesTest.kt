package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.Empty
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors from rcheevos `test/hash/test_rc_hash_rom.c` (the expected MD5s are rcheevos' own), run
 * through [RaHasher]. Each ROM is built the way that file builds it.
 */
class CartridgeHashesTest {
    private fun hash(console: Int, name: String, bytes: ByteArray) =
        RaHasher.hashBlocking(console, MemorySource(name, bytes))

    private fun assertHashed(expected: String, actual: RaHashResult) =
        assertEquals(RaHashResult.Hashed(expected), actual)

    // --- whole file: test_hash_full_file rows of test_rc_hash_rom.c (all with generate_generic_file)

    @Test
    fun wholeFileConsolesMatchRcheevosVectors() {
        val rows = listOf(
            Triple(RaConsoles.ARCADIA_2001, 4096, "572686c3a073162e4ec6eff86e6f6e3a"),
            Triple(RaConsoles.ATARI_2600, 2048, "02c3f2fa186388ba8eede9147fb431c4"),
            Triple(RaConsoles.ATARI_JAGUAR, 0x400000, "a247ec8a8c42e18fcb80702dfadac14b"),
            Triple(RaConsoles.COLECOVISION, 16384, "455f07d8500f3fabc54906737866167f"),
            Triple(RaConsoles.ELEKTOR_TV_GAMES_COMPUTER, 4096, "572686c3a073162e4ec6eff86e6f6e3a"),
            Triple(RaConsoles.ELEKTOR_TV_GAMES_COMPUTER, 1861, "37097124a29aff663432d049654a17dc"),
            Triple(RaConsoles.FAIRCHILD_CHANNEL_F, 2048, "02c3f2fa186388ba8eede9147fb431c4"),
            Triple(RaConsoles.GAMEBOY, 131072, "a0f425b23200568132ba76b2405e3933"),
            Triple(RaConsoles.GAMEBOY_COLOR, 2097152, "cf86acf519625a25a17b1246975e90ae"),
            Triple(RaConsoles.GAMEBOY_ADVANCE, 4194304, "a247ec8a8c42e18fcb80702dfadac14b"),
            Triple(RaConsoles.GAME_GEAR, 524288, "68f0f13b598e0b66461bc578375c3888"),
            Triple(RaConsoles.INTELLIVISION, 8192, "ce1127f881b40ce6a67ecefba50e2835"),
            Triple(RaConsoles.INTERTON_VC_4000, 2048, "02c3f2fa186388ba8eede9147fb431c4"),
            Triple(RaConsoles.MAGNAVOX_ODYSSEY2, 4096, "572686c3a073162e4ec6eff86e6f6e3a"),
            Triple(RaConsoles.MASTER_SYSTEM, 131072, "a0f425b23200568132ba76b2405e3933"),
            Triple(RaConsoles.MEGA_DRIVE, 1048576, "da9461b3b0f74becc3ccf6c2a094c516"),
            Triple(RaConsoles.MEGADUCK, 65536, "8e6576cd5c21e44e0bbfc4480577b040"),
            Triple(RaConsoles.NEOGEO_POCKET, 2097152, "cf86acf519625a25a17b1246975e90ae"),
            Triple(RaConsoles.ORIC, 18119, "953a2baa3232c63286aeae36b2172cef"),
            Triple(RaConsoles.POKEMON_MINI, 524288, "68f0f13b598e0b66461bc578375c3888"),
            Triple(RaConsoles.SEGA_32X, 3145728, "07d733f252896ec41b4fd521fe610e2c"),
            Triple(RaConsoles.SG1000, 32768, "6a2305a2b6675a97ff792709be1ca857"),
            Triple(RaConsoles.SUPERVISION, 32768, "6a2305a2b6675a97ff792709be1ca857"),
            Triple(RaConsoles.TI83, 1695, "bfb6048395a425c69743900785987c42"),
            Triple(RaConsoles.TI83, 2500, "6e81d530ee9a79d4f4f505729ad74bb5"),
            Triple(RaConsoles.TIC80, 67682, "79b96f4ffcedb3ce8210a83b22cd2c69"),
            Triple(RaConsoles.UZEBOX, 53654, "a9aab505e92edc034d3c732869159789"),
            Triple(RaConsoles.VECTREX, 4096, "572686c3a073162e4ec6eff86e6f6e3a"),
            Triple(RaConsoles.VIRTUAL_BOY, 524288, "68f0f13b598e0b66461bc578375c3888"),
            Triple(RaConsoles.WASM4, 33454, "bce38bb5f05622fc7e0e56757059d180"),
            Triple(RaConsoles.WONDERSWAN, 524288, "68f0f13b598e0b66461bc578375c3888"),
            Triple(RaConsoles.WONDERSWAN, 4194304, "a247ec8a8c42e18fcb80702dfadac14b"),
        )
        for ((console, size, md5) in rows) {
            assertHashed(md5, hash(console, "test.bin", genericFile(size)))
        }
    }

    @Test
    fun wholeFileStopsAtSixtyFourMebibytes() {
        val limit = 64L * 1024 * 1024
        val expected = newMd5().also { md ->
            val zeros = ByteArray(1 shl 20)
            repeat((limit shr 20).toInt()) { md.update(zeros) }
        }.hex()
        val over = RaHasher.hashBlocking(RaConsoles.GAMEBOY, ZeroStreamSource("big.gb", limit + 12345))
        assertHashed(expected, over)
        val exact = RaHasher.hashBlocking(RaConsoles.GAMEBOY, ZeroStreamSource("big.gb", limit))
        assertHashed(expected, exact)
    }

    // --- NES, FDS: test_hash_nes_*, test_hash_fds_*

    private fun nesFile(kb: Int, header: Boolean): ByteArray {
        val body = kb * 1024
        val image = ByteArray(body + if (header) 16 else 0)
        if (header) {
            "NES".forEachIndexed { i, c -> image[i] = c.code.toByte() }
            image[3] = 0x1A
            image[4] = (kb / 16).toByte()
            fillImage(image, 16)
        } else {
            fillImage(image)
        }
        return image
    }

    private fun fdsFile(sides: Int, header: Boolean): ByteArray {
        val body = sides * 65500
        val image = ByteArray(body + if (header) 16 else 0)
        if (header) {
            "FDS".forEachIndexed { i, c -> image[i] = c.code.toByte() }
            image[3] = 0x1A
            image[4] = sides.toByte()
            fillImage(image, 16)
        } else {
            fillImage(image)
        }
        return image
    }

    @Test
    fun nesWithoutHeader32k() =
        assertHashed("6a2305a2b6675a97ff792709be1ca857", hash(RaConsoles.NINTENDO, "a.nes", nesFile(32, false)))

    @Test
    fun nesHeaderIsSkipped() =
        assertHashed("6a2305a2b6675a97ff792709be1ca857", hash(RaConsoles.NINTENDO, "a.nes", nesFile(32, true)))

    @Test
    fun nes256k() =
        assertHashed("545d527301b8ae148153988d6c4fcb84", hash(RaConsoles.NINTENDO, "a.nes", nesFile(256, false)))

    @Test
    fun fdsTwoSidesWithAndWithoutHeader() {
        assertHashed("fd770d4d34c00760fabda6ad294a8f0b", hash(RaConsoles.NINTENDO, "a.fds", fdsFile(2, false)))
        assertHashed("fd770d4d34c00760fabda6ad294a8f0b", hash(RaConsoles.FAMICOM_DISK_SYSTEM, "a.fds", fdsFile(2, true)))
    }

    @Test
    fun nesHeaderOnlyFileIsHashedAsIs() {
        // rc_hash skips the header only when the file is longer than 16 bytes.
        val sixteen = ByteArray(16).also { "NES".forEachIndexed { i, c -> it[i] = c.code.toByte() }; it[3] = 0x1A }
        val expected = newMd5().also { it.update(sixteen) }.hex()
        assertHashed(expected, hash(RaConsoles.NINTENDO, "a.nes", sixteen))
    }

    // --- Atari 7800: test_hash_atari_7800*

    private fun atari7800File(kb: Int, header: Boolean): ByteArray {
        val image = ByteArray(kb * 1024 + if (header) 128 else 0)
        if (header) {
            image[0] = 3
            "ATARI7800".forEachIndexed { i, c -> image[1 + i] = c.code.toByte() }
            image[50] = (kb / 4).toByte()
            fillImage(image, 128)
        } else {
            fillImage(image)
        }
        return image
    }

    @Test
    fun atari7800WithAndWithoutHeader() {
        assertHashed("455f07d8500f3fabc54906737866167f", hash(RaConsoles.ATARI_7800, "a.a78", atari7800File(16, false)))
        assertHashed("455f07d8500f3fabc54906737866167f", hash(RaConsoles.ATARI_7800, "a.a78", atari7800File(16, true)))
    }

    // --- Lynx: rcheevos has no Lynx vector; the headered file must hash like the headerless one.

    @Test
    fun lynxHeaderIsSkipped() {
        val body = genericFile(32768)
        val withHeader = ByteArray(64 + body.size)
        "LYNX".forEachIndexed { i, c -> withHeader[i] = c.code.toByte() }
        body.copyInto(withHeader, 64)
        val plain = hash(RaConsoles.ATARI_LYNX, "a.lnx", body)
        assertTrue(plain is RaHashResult.Hashed)
        assertEquals(plain, hash(RaConsoles.ATARI_LYNX, "a.lnx", withHeader))
        // "LYNX" without the zero byte is not the header.
        withHeader[4] = 1
        assertTrue(plain != hash(RaConsoles.ATARI_LYNX, "a.lnx", withHeader))
    }

    // --- SNES and PC Engine: the test_hash_full_file rows with a 512-byte header

    @Test
    fun snesCopierHeader() {
        assertHashed("68f0f13b598e0b66461bc578375c3888", hash(RaConsoles.SUPER_NINTENDO, "a.smc", genericFile(524288)))
        // 512 bytes over a multiple of 8 KiB: the first 512 bytes are left out.
        assertHashed("258c93ebaca1c3f488ab48218e5e8d38", hash(RaConsoles.SUPER_NINTENDO, "a.smc", genericFile(524288 + 512)))
    }

    @Test
    fun pcEngineHeader() {
        assertHashed("68f0f13b598e0b66461bc578375c3888", hash(RaConsoles.PC_ENGINE, "a.pce", genericFile(524288)))
        assertHashed("258c93ebaca1c3f488ab48218e5e8d38", hash(RaConsoles.PC_ENGINE, "a.pce", genericFile(524288 + 512)))
        assertHashed("ebb565a7f964ccdfaecdce0d6ed540af", hash(RaConsoles.PC_ENGINE, "a.pce", genericFile(491520 + 512)))
    }

    // --- Super Cassette Vision: test_hash_scv_cart

    @Test
    fun scvHeaderIsSkipped() {
        val image = genericFile(32768 + 32)
        "EmuSCV....CART..................".forEachIndexed { i, c -> image[i] = c.code.toByte() }
        assertHashed("4309c9844b44f9ff8256dfc04687b8fd", hash(RaConsoles.SUPER_CASSETTEVISION, "game.cart", image))
        assertHashed("6a2305a2b6675a97ff792709be1ca857", hash(RaConsoles.SUPER_CASSETTEVISION, "a.bin", genericFile(32768)))
    }

    // --- Nintendo 64: test_hash_n64, test_hash_n64_file (first 64 bytes of Super Mario 64)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private val z64 = bytes(
        0x80, 0x37, 0x12, 0x40, 0x00, 0x00, 0x00, 0x0F, 0x80, 0x24, 0x60, 0x00, 0x00, 0x00, 0x14, 0x44,
        0x63, 0x5A, 0x2B, 0xFF, 0x8B, 0x02, 0x23, 0x26, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x53, 0x55, 0x50, 0x45, 0x52, 0x20, 0x4D, 0x41, 0x52, 0x49, 0x4F, 0x20, 0x36, 0x34, 0x20, 0x20,
        0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4E, 0x53, 0x4D, 0x45, 0x00,
    )
    private val v64 = bytes(
        0x37, 0x80, 0x40, 0x12, 0x00, 0x00, 0x0F, 0x00, 0x24, 0x80, 0x00, 0x60, 0x00, 0x00, 0x44, 0x14,
        0x5A, 0x63, 0xFF, 0x2B, 0x02, 0x8B, 0x26, 0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x55, 0x53, 0x45, 0x50, 0x20, 0x52, 0x41, 0x4D, 0x49, 0x52, 0x20, 0x4F, 0x34, 0x36, 0x20, 0x20,
        0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x4D, 0x53, 0x00, 0x45,
    )
    private val n64 = bytes(
        0x40, 0x12, 0x37, 0x80, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x60, 0x24, 0x80, 0x44, 0x14, 0x00, 0x00,
        0xFF, 0x2B, 0x5A, 0x63, 0x26, 0x23, 0x02, 0x8B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x45, 0x50, 0x55, 0x53, 0x41, 0x4D, 0x20, 0x52, 0x20, 0x4F, 0x49, 0x52, 0x20, 0x20, 0x34, 0x36,
        0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x00, 0x00, 0x00, 0x45, 0x4D, 0x53,
    )
    private val ndd = bytes(
        0xE8, 0x48, 0xD3, 0x16, 0x10, 0x13, 0x00, 0x45, 0x0C, 0x18, 0x24, 0x30, 0x3C, 0x48, 0x54, 0x60,
        0x6C, 0x78, 0x84, 0x90, 0x9C, 0xA8, 0xB4, 0xC0, 0xFF, 0xFF, 0xFF, 0xFF, 0x80, 0x02, 0x5C, 0x00,
        0x10, 0x16, 0x1C, 0x22, 0x28, 0x2A, 0x31, 0x32, 0x3A, 0x40, 0x46, 0x4C, 0x04, 0x0C, 0x14, 0x1C,
        0x24, 0x2C, 0x34, 0x3C, 0x44, 0x4C, 0x54, 0x5C, 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C,
    )

    @Test
    fun n64AllThreeByteOrdersHashTheSame() {
        val expected = "06096d7ce21cb6bcde38391534c4eb91"
        assertHashed(expected, hash(RaConsoles.NINTENDO_64, "game.z64", z64))
        assertHashed(expected, hash(RaConsoles.NINTENDO_64, "game.v64", v64))
        assertHashed(expected, hash(RaConsoles.NINTENDO_64, "game.n64", n64))
        // misnamed files: the first byte decides, not the extension
        assertHashed(expected, hash(RaConsoles.NINTENDO_64, "game.n64", z64))
        assertHashed(expected, hash(RaConsoles.NINTENDO_64, "game.z64", n64))
    }

    @Test
    fun n64DiskDriveImageIsNotSwapped() =
        assertHashed("a698b32a52970d8a52a5a52c83acc2a9", hash(RaConsoles.NINTENDO_64, "game.ndd", ndd))

    @Test
    fun n64SwapsAcrossChunkBoundaries() {
        // A byte-swapped ROM longer than the 64 KiB chunk hashes like its big-endian original.
        val big = genericFile(200_000)
        big[0] = 0x80.toByte(); big[1] = 0x37
        val swapped = big.copyOf()
        var i = 0
        while (i + 1 < swapped.size) {
            val t = swapped[i]; swapped[i] = swapped[i + 1]; swapped[i + 1] = t; i += 2
        }
        assertEquals(hash(RaConsoles.NINTENDO_64, "a.z64", big), hash(RaConsoles.NINTENDO_64, "a.v64", swapped))
    }

    @Test
    fun n64RejectsABadFirstByte() {
        val result = hash(RaConsoles.NINTENDO_64, "game.z64", genericFile(64).also { it[0] = 0x12 })
        assertEquals(NotARom, (result as RaHashResult.CannotHash).reason)
    }

    // --- Nintendo DS and DSi: test_hash_nds, test_hash_nds_supercard, test_hash_dsi

    private fun ndsFile(arm9Size: Int, arm7Size: Int): ByteArray {
        val image = genericFile(2 * 1024 * 1024)
        val arm9Addr = 65536
        val arm7Addr = arm9Addr + arm9Size
        val iconAddr = arm7Addr + arm7Size
        fun put(at: Int, v: Int) { for (k in 0..3) image[at + k] = (v ushr (8 * k)).toByte() }
        put(0x20, arm9Addr); put(0x2C, arm9Size); put(0x30, arm7Addr); put(0x3C, arm7Size); put(0x68, iconAddr)
        return image
    }

    private val ndsHash = "56b30c276cba4affa886bd38e8e34d7e"

    @Test
    fun nintendoDsAndDsi() {
        val image = ndsFile(1234567, 654321)
        assertHashed(ndsHash, hash(RaConsoles.NINTENDO_DS, "game.nds", image))
        assertHashed(ndsHash, hash(RaConsoles.NINTENDO_DSI, "game.nds", image))
    }

    @Test
    fun nintendoDsSuperCardHeaderIsSkipped() {
        val image = ndsFile(1234567, 654321)
        val padded = ByteArray(512 + image.size)
        image.copyInto(padded, 512)
        padded[0] = 0x2E; padded[3] = 0xEA.toByte()
        padded[0xB0] = 0x44; padded[0xB1] = 0x46; padded[0xB2] = 0x96.toByte(); padded[0xB3] = 0
        assertHashed(ndsHash, hash(RaConsoles.NINTENDO_DS, "game.nds", padded))
    }

    @Test
    fun nintendoDsRejectsShortAndHugeHeaders() {
        val short = hash(RaConsoles.NINTENDO_DS, "game.nds", genericFile(300))
        assertEquals(NotARom, (short as RaHashResult.CannotHash).reason)
        // ARM9 + ARM7 code over 16 MiB: not a DS ROM.
        val huge = ndsFile(1234567, 654321)
        for (k in 0..3) huge[0x2C + k] = if (k == 3) 0x02 else 0
        val result = hash(RaConsoles.NINTENDO_DS, "game.nds", huge)
        assertEquals(NotARom, (result as RaHashResult.CannotHash).reason)
    }

    @Test
    fun nintendoDsPadsAnIconBlockCutShortWithZeros() {
        // Homebrew with no full icon block: the missing bytes count as zeros.
        val image = ndsFile(1000, 1000)
        val iconAt = 65536 + 2000
        val cut = image.copyOf(iconAt + 100)
        val padded = cut.copyOf(iconAt + 0xA00) // zero filled
        assertEquals(hash(RaConsoles.NINTENDO_DS, "a.nds", padded), hash(RaConsoles.NINTENDO_DS, "a.nds", cut))
    }

    // --- Arduboy: test_hash_arduboy, _crlf, _no_final_lf

    private val hex =
        ":100000000C94690D0C94910D0C94910D0C94910D20\n" +
            ":100010000C94910D0C94910D0C94910D0C94910DE8\n" +
            ":100020000C94910D0C94910D0C94C32A0C94352BC7\n" +
            ":00000001FF\n"

    @Test
    fun arduboyHexNormalisesLineEndings() {
        val expected = "67b64633285a7f965064ba29dab45148"
        assertHashed(expected, hash(RaConsoles.ARDUBOY, "game.hex", hex.toByteArray()))
        assertHashed(expected, hash(RaConsoles.ARDUBOY, "game.hex", hex.replace("\n", "\r\n").toByteArray()))
        assertHashed(expected, hash(RaConsoles.ARDUBOY, "game.hex", hex.trimEnd('\n').toByteArray()))
    }

    @Test
    fun arduboyBlankLinesAreKept() {
        // "a\n\nb" hashes as "a\n\nb\n"; a lone CR is a line end too.
        val expected = newMd5().also { it.update("a\n\nb\n".toByteArray()) }.hex()
        assertHashed(expected, hash(RaConsoles.ARDUBOY, "g.hex", "a\n\nb".toByteArray()))
        assertHashed(expected, hash(RaConsoles.ARDUBOY, "g.hex", "a\r\rb".toByteArray()))
    }

    // --- Neo Geo cartridge (.neo, Arcade): test_hash_neo*

    private fun neoFile(payload: Int, name: String): ByteArray {
        val image = ByteArray(4096 + payload)
        image[0] = 'N'.code.toByte(); image[1] = 'E'.code.toByte(); image[2] = 'O'.code.toByte(); image[3] = 1
        for (k in 0..3) image[4 + k] = (payload ushr (8 * k)).toByte()
        name.forEachIndexed { i, c -> image[44 + i] = c.code.toByte() }
        fillImage(image, 4096)
        return image
    }

    @Test
    fun neoHashIsOfTheRomDataOnly() {
        val payloadHash = hash(RaConsoles.MEGA_DRIVE, "p.md", genericFile(131072))
        assertTrue(payloadHash is RaHashResult.Hashed)
        assertEquals(payloadHash, hash(RaConsoles.ARCADE, "game.neo", neoFile(131072, "Test Game")))
        // another tool's header text, same ROM data
        assertEquals(payloadHash, hash(RaConsoles.ARCADE, "game2.neo", neoFile(131072, "test game (alt name)")))
    }

    @Test
    fun neoWithBadMagicOrNoDataIsNotARom() {
        val bad = neoFile(131072, "Test").also { it[3] = 2 }
        assertEquals(NotARom, (hash(RaConsoles.ARCADE, "g.neo", bad) as RaHashResult.CannotHash).reason)
        val noData = neoFile(0, "Test")
        assertEquals(NotARom, (hash(RaConsoles.ARCADE, "g.neo", noData) as RaHashResult.CannotHash).reason)
    }

    // --- wrong paths

    @Test
    fun emptyFileCannotBeHashed() {
        for (console in listOf(RaConsoles.GAMEBOY, RaConsoles.NINTENDO, RaConsoles.SUPER_NINTENDO, RaConsoles.NINTENDO_64, RaConsoles.NINTENDO_DS)) {
            val result = hash(console, "empty.bin", ByteArray(0))
            assertEquals("console $console", Empty, (result as RaHashResult.CannotHash).reason)
        }
    }

    @Test
    fun truncatedHeadersAreHashedAsPlainBytesOrRejected() {
        // 10 bytes starting like an iNES header: too short to hold one, so hashed whole.
        val tiny = ByteArray(10).also { "NES".forEachIndexed { i, c -> it[i] = c.code.toByte() }; it[3] = 0x1A }
        assertHashed(newMd5().also { it.update(tiny) }.hex(), hash(RaConsoles.NINTENDO, "a.nes", tiny))
        // A 100-byte DS file cannot hold the 512-byte header.
        assertTrue(hash(RaConsoles.NINTENDO_DS, "a.nds", ByteArray(100) { 1 }) is RaHashResult.CannotHash)
    }

    @Test
    fun unreadableFileIsReported() {
        for (console in listOf(RaConsoles.GAMEBOY, RaConsoles.NINTENDO, RaConsoles.NINTENDO_64, RaConsoles.NINTENDO_DS, RaConsoles.ARDUBOY, RaConsoles.ARCADE)) {
            val name = if (console == RaConsoles.ARCADE) "a.neo" else "a.bin"
            val result = RaHasher.hashBlocking(console, FailingSource(name))
            assertTrue("console $console: $result", result is RaHashResult.Unreadable)
        }
    }

    @Test
    fun readErrorHalfwayThroughIsReported() {
        val result = RaHasher.hashBlocking(RaConsoles.GAMEBOY, BreaksHalfwaySource("a.gb"))
        assertTrue(result is RaHashResult.Unreadable)
    }

    @Test
    fun cancellationStopsALongRead() {
        var chunks = 0
        try {
            RaHasher.hashBlocking(RaConsoles.GAMEBOY, ZeroStreamSource("big.gb", 64L * 1024 * 1024)) {
                if (++chunks == 3) throw java.util.concurrent.CancellationException("stop")
            }
            org.junit.Assert.fail("the check should have stopped the read")
        } catch (e: java.util.concurrent.CancellationException) {
            assertEquals(3, chunks)
        }
    }
}
