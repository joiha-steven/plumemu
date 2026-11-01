package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases found in review: odd zip names, wrapping DS sizes, document URIs, odd-sized byte-swapped ROMs. */
class RaHasherEdgeCasesTest {
    @Test
    fun zipEntryNamedInShiftJisBytesStillHashes() {
        val rom = genericFile(32768)
        val out = ByteArrayOutputStream()
        // The name bytes 0x83 0x51 are not valid UTF-8; the entry name is written as raw bytes.
        ZipOutputStream(out, Charsets.ISO_8859_1).use { zip ->
            zip.putNextEntry(ZipEntry("\u0083Q.nes"))
            zip.write(rom)
            zip.closeEntry()
        }
        assertEquals(
            RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.nes", rom)),
            RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", out.toByteArray())),
        )
        assertTrue(RaHasher.hashBlocking(RaConsoles.NINTENDO, MemorySource("a.zip", out.toByteArray())) is RaHashResult.Hashed)
    }

    @Test
    fun nintendoDsSizesThatWrapPast32BitsAreRejectedQuickly() {
        val image = genericFile(2 * 1024 * 1024)
        fun put(at: Int, v: Long) { for (k in 0..3) image[at + k] = (v ushr (8 * k)).toByte() }
        put(0x20, 65536); put(0x2C, 0xFFFFF000L); put(0x30, 65536); put(0x3C, 0x2000L); put(0x68, 65536)
        val result = RaHasher.hashBlocking(RaConsoles.NINTENDO_DS, MemorySource("a.nds", image))
        assertEquals(NotARom, (result as RaHashResult.CannotHash).reason)
    }

    @Test
    fun arcadeDocumentUriWithoutAFileNameInItUsesTheFileName() {
        val game = RaHashResult.Hashed("c8d46d341bea4fd5bff866a65ff8aea9")
        for (key in listOf(
            "content://com.android.providers.media.documents/document/msf%3A123",
            "content://media/external/file/1234",
        )) {
            assertEquals(key, game, RaHasher.hashBlocking(RaConsoles.ARCADE, MemorySource("game.zip", ByteArray(0), key = key)))
        }
    }

    @Test
    fun n64OddSizedByteSwappedFileSwapsItsTailLikeRcheevos() {
        // 4k+2 bytes: rc_hash swaps the last two bytes too, so a v64 file hashes like its z64 original.
        val z64 = genericFile(1002).also { it[0] = 0x80.toByte(); it[1] = 0x37 }
        val v64 = z64.copyOf()
        var i = 0
        while (i + 1 < v64.size) { val t = v64[i]; v64[i] = v64[i + 1]; v64[i + 1] = t; i += 2 }
        assertEquals(
            RaHasher.hashBlocking(RaConsoles.NINTENDO_64, MemorySource("a.z64", z64)),
            RaHasher.hashBlocking(RaConsoles.NINTENDO_64, MemorySource("a.v64", v64)),
        )
    }
}
