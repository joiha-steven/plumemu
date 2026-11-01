package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadArchive
import com.plumemu.achievements.hash.RaCannotHashReason.MissingFile
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NoRomInArchive
import com.plumemu.achievements.hash.RaCannotHashReason.NotYetSupported
import com.plumemu.achievements.hash.RaCannotHashReason.UnsupportedFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arcade vectors from `test_rc_hash_rom.c`, plus dispatch, `.zip` and archive handling. */
class RaHasherTest {
    private fun reason(result: RaHashResult) = (result as RaHashResult.CannotHash).reason

    private fun arcade(path: String): RaHashResult =
        RaHasher.hashBlocking(RaConsoles.ARCADE, MemorySource(path.substringAfterLast('/').substringAfterLast('\\'), ByteArray(0), key = path))

    // --- Arcade: test_hash_arcade, test_hash_arcade_from_directory

    @Test
    fun arcadeHashesTheNameWithoutExtension() {
        val game = RaHashResult.Hashed("c8d46d341bea4fd5bff866a65ff8aea9")
        for (path in listOf(
            "game.zip", "game.7z", "/game.zip", "\\game.zip", "roms\\game.zip", "C:\\roms\\game.zip",
            "/home/user/roms/game.zip", "/home/user/roms/game.7z",
            "/home/user/snes/game.zip", "/home/user/nes2/game.zip",
        )) {
            assertEquals(path, game, arcade(path))
        }
    }

    @Test
    fun arcadeFolderNamedAfterASubsystemIsPartOfTheName() {
        val nes = RaHashResult.Hashed("9b7aad36b365712fc93728088de4c209")
        for (path in listOf(
            "/home/user/nes_game.zip", "/home/user/nes/game.zip", "C:\\roms\\nes\\game.zip",
            "C:\\roms\\NES\\game.zip", "nes\\game.zip",
        )) {
            assertEquals(path, nes, arcade(path))
        }
    }

    @Test
    fun arcadeEverySubsystemFolder() {
        val rows = mapOf(
            "chf" to "6ef57f16562ea0c7f49d93853b313e32", "channelf" to "7b6506637a0cc79bd1d24a43a34fa3b9",
            "coleco" to "c546f63ae7de98add4b9f221a4749260", "colecovision" to "47279207b94dbf2a45cb13efa56d685e",
            "msx" to "59ab85f6b56324fd81b4e324b804c29f", "msx1" to "33328d832dcb0854383cdd4a4565c459",
            "pce" to "c414a783f3983bbe2e9e01d9d5320c7e", "pcengine" to "49370c3cbe98bdcdce545c68379487db",
            "sgx" to "db545ab29694bfda1010317d4bac83b8", "supergrafx" to "5665c9ef4c2f6609d8e420c4d86ba692",
            "tg16" to "8b6c5c2e54915be2cdba63973862e143", "fds" to "c0c135a97e8c577cfdf9204823ff211f",
            "gamegear" to "f6f471e952b8103032b723f57bdbe767", "mastersystem" to "f4805afe0ff5647140a26bd0a1057373",
            "sms" to "43f35f575dead94dd2f42f9caf69fe5a", "megadriv" to "f99d0aaf12ba3eb6ced9878c76692c63",
            "megadrive" to "73eb5d7034b382093b1d36414d9e84e4", "genesis" to "b62f810c63e1cba7f5b7569643bec236",
            "sg1000" to "e8f6c711c4371f09537b4f2a7a304d6c", "spectrum" to "a5f62157b2617bd728c4b1bc885c29e9",
            "ngp" to "d4133b74c4e57274ca514e27a370dcb6",
        )
        for ((folder, md5) in rows) {
            assertEquals(folder, RaHashResult.Hashed(md5), arcade("/home/user/$folder/game.zip"))
        }
    }

    @Test
    fun arcadeFolderPathWithoutExtensionHashesTheFolderName() {
        val game = RaHashResult.Hashed("c8d46d341bea4fd5bff866a65ff8aea9")
        for (path in listOf("game", "/game", "\\game", "roms\\game", "C:\\roms\\game", "/home/user/games/game")) {
            assertEquals(path, game, arcade(path))
        }
    }

    @Test
    fun arcadeNeverOpensTheArchive() {
        // The name is the hash: a .7z that cannot be read still hashes, and needs no decoder.
        assertEquals(
            RaHashResult.Hashed("c8d46d341bea4fd5bff866a65ff8aea9"),
            RaHasher.hashBlocking(RaConsoles.ARCADE, FailingSource("game.7z")),
        )
    }

    @Test
    fun arcadeDocumentUriIsDecodedToFindTheFolder() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Ffbneo%2Fnes%2Fgame.zip"
        assertEquals(
            RaHashResult.Hashed("9b7aad36b365712fc93728088de4c209"),
            RaHasher.hashBlocking(RaConsoles.ARCADE, MemorySource("game.zip", ByteArray(0), key = uri)),
        )
    }

    // --- dispatch

    @Test
    fun consolesWithoutAnImplementedRuleAreNotYetSupported() {
        for (console in listOf(RaConsoles.NINTENDO_3DS, RaConsoles.MS_DOS)) {
            assertEquals("console $console", NotYetSupported, reason(RaHasher.hashBlocking(console, MemorySource("a.iso", genericFile(100)))))
        }
    }

    @Test
    fun consolesWithoutAHashRuleAreUnsupported() {
        for (console in listOf(0, 50, 31, 35, 999)) {
            assertEquals("console $console", UnsupportedFormat, reason(RaHasher.hashBlocking(console, MemorySource("a.bin", genericFile(100)))))
        }
    }

    @Test
    fun arduboyArchivesAreNotYetSupportedAndAPlaylistNeedsItsDisc() {
        assertEquals(MissingFile, reason(RaHasher.hashBlocking(RaConsoles.MEGA_DRIVE, MemorySource("a.m3u", "x.md\n".toByteArray()))))
        assertEquals(NotYetSupported, reason(RaHasher.hashBlocking(RaConsoles.ARDUBOY, MemorySource("a.arduboy", genericFile(100)))))
    }

    @Test
    fun suspendEntryPointGivesTheSameHash() = runBlocking {
        val result = RaHasher.hash(RaConsoles.NINTENDO, MemorySource("a.nes", genericFile(32768)))
        assertEquals(RaHashResult.Hashed("6a2305a2b6675a97ff792709be1ca857"), result)
    }

    // --- archives

    @Test
    fun sevenZipAndOtherArchivesNeedADecoder() {
        for (name in listOf("game.7z", "game.rar", "game.tar.gz", "game.gz")) {
            assertEquals(name, NeedsDecoder, reason(RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource(name, genericFile(100)))))
        }
    }

    @Test
    fun zipHoldingARomHashesTheRom() {
        val rom = genericFile(32768)
        val zip = zipOf("readme.txt" to "hello".toByteArray(), "Game (USA).nes" to rom)
        assertEquals(
            RaHashResult.Hashed("6a2305a2b6675a97ff792709be1ca857"),
            RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("game.zip", zip)),
        )
    }

    @Test
    fun zipEntryIsHashedWithItsConsoleRule() {
        // A headered NES file inside a zip: the header is skipped like a loose file's.
        val image = ByteArray(16 + 32768).also {
            "NES".forEachIndexed { i, c -> it[i] = c.code.toByte() }; it[3] = 0x1A
            fillImage(it, 16)
        }
        val zip = zipOf("a.nes" to image)
        assertEquals(
            RaHashResult.Hashed("6a2305a2b6675a97ff792709be1ca857"),
            RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", zip)),
        )
        // A Nintendo DS entry is read by seeking, through the zip.
        val nds = ByteArray(2 * 1024 * 1024).also { fillImage(it) }
        for (k in 0..3) { nds[0x20 + k] = if (k == 2) 1 else 0; nds[0x2C + k] = if (k == 1) 0x10 else 0; nds[0x3C + k] = 0 }
        nds[0x68] = 0; nds[0x69] = 0; nds[0x6A] = 0x02; nds[0x6B] = 0
        val loose = RaHasher.hashBlocking(RaConsoles.NINTENDO_DS, MemorySource("a.nds", nds))
        assertTrue(loose is RaHashResult.Hashed)
        assertEquals(loose, RaHasher.hashBlocking(RaConsoles.NINTENDO_DS, MemorySource("a.zip", zipOf("a.nds" to nds))))
    }

    @Test
    fun zipWithoutARomEntryIsReported() {
        val zip = zipOf("readme.txt" to "hello".toByteArray(), "cover.png" to ByteArray(10), "folder/" to ByteArray(0))
        assertEquals(NoRomInArchive, reason(RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", zip))))
        // not a zip at all
        assertEquals(NoRomInArchive, reason(RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", genericFile(500)))))
    }

    @Test
    fun zipCutShortIsReported() {
        val zip = zipOf("a.nes" to genericFile(200_000))
        val cut = zip.copyOf(zip.size / 2)
        assertEquals(BadArchive, reason(RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", cut))))
    }

    @Test
    fun unreadableZipIsReported() {
        assertTrue(RaHasher.hashBlocking(RaConsoles.NINTENDO, FailingSource("a.zip")) is RaHashResult.Unreadable)
    }
}
