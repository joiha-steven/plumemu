package com.plumemu.identify.pack

import com.plumemu.identify.IdentifyLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

/** Generated packs for tests: made-up titles, made-up CRCs and codes, never real data. */
internal object TestPacks {
    const val CRC_ZORK = 0x1234ABCDL
    const val CRC_ZORK_EUROPE = 0x2345BCDEL
    const val CRC_HIGH_BIT = 0xF0000001L

    /** A small console pack for system [systemId] with three titles and a few releases. */
    fun console(systemId: String = "megadrive", dataVersion: Long = 20261021): ByteArray {
        val b = ConsolePackBuilder(systemId, dataVersion)
        val src = b.source("Test DAT", "2026-10-21")
        val zork = b.title("Zork Quest II", year = 1991, publisher = "Testsoft")
        b.release(zork, regions = listOf("USA"), languages = listOf("En"), source = src, crcs = listOf(CRC_ZORK to 524288L), codes = listOf("ZZZE" to 1))
        b.release(zork, regions = listOf("Europe"), languages = listOf("En", "Fr"), variant = "Rev 1", source = src, crcs = listOf(CRC_ZORK_EUROPE to 524288L), codes = listOf("ZZZE" to 1))
        val moon = b.title("Moonlit Harbor", year = 1994)
        b.release(moon, regions = listOf("Japan"), source = src, crcs = listOf(CRC_HIGH_BIT to null), codes = listOf("GM 00009999-00" to 5))
        val quest = b.title("Zork Quest")
        b.release(quest, regions = listOf("USA", "Europe"), source = src)
        return b.build()
    }

    /** An arcade pack: a parent, a clone, a BIOS, a device and a mechanical set. */
    fun arcade(dataVersion: Long = 20261021): ByteArray {
        val b = ArcadePackBuilder("arcade", dataVersion)
        val src = b.source("MAME software lists", "0.270")
        b.set("zorkfght", "Zork Fighters", maker = "Testsoft", year = 1996, source = src)
        b.set("zorkfghtj", "Zork Fighters (Japan)", maker = "Testsoft", year = 1996, parent = "zorkfght", source = src)
        b.set("testbios", "Test System BIOS", flags = ArcadeFlags.BIOS, source = src)
        b.set("testdev", "Test Sound Device", flags = ArcadeFlags.DEVICE, source = src)
        b.set("testslot", "Test Slot Machine", flags = ArcadeFlags.MECHANICAL, source = src)
        return b.build()
    }

    fun open(bytes: ByteArray): NamePack = (NamePack.fromBytes(bytes) as PackOpenResult.Opened).pack

    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    /** A pack of [count] generated titles, one release each, for timing and memory. */
    fun many(count: Int, seed: Long = 11): Pair<ByteArray, List<String>> {
        val random = Random(seed)
        val b = ConsolePackBuilder("timing", 1)
        val names = LinkedHashSet<String>()
        while (names.size < count) names += madeUpTitle(random)
        names.forEachIndexed { i, name ->
            val t = b.title(name, year = 1985 + i % 20)
            b.release(t, regions = listOf("USA"), crcs = listOf(random.nextLong(0, 1L shl 32) to 1024L))
        }
        return b.build() to names.toList()
    }

    private val SYLLABLES = listOf("zor", "kel", "mab", "tri", "von", "pel", "dra", "qui", "lum", "sar", "bex", "tor", "nim", "gal", "fen", "rho")
    private val TAILS = listOf("", "", "", " II", " 3", ": The Return", " Deluxe", " Adventure", " Racing", " Quest")

    fun madeUpTitle(random: Random): String {
        val words = (1..random.nextInt(2, 4)).map {
            val word = (1..random.nextInt(2, 4)).joinToString("") { SYLLABLES[random.nextInt(SYLLABLES.size)] }
            word.replaceFirstChar { c -> c.uppercaseChar() }
        }
        return words.joinToString(" ") + TAILS[random.nextInt(TAILS.size)]
    }
}

/** Bundled packs held in memory; counts how often each pack is opened. */
internal class FakeBundled(
    private val index: String?,
    private val files: Map<String, ByteArray>,
    private val failOpen: Boolean = false,
) : BundledPacks {
    val opens = AtomicInteger()
    private val opensByName = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    fun opensOf(fileName: String): Int = opensByName[fileName]?.get() ?: 0

    override fun readIndex(): String? = index

    override fun open(fileName: String): InputStream {
        opens.incrementAndGet()
        opensByName.getOrPut(fileName) { AtomicInteger() }.incrementAndGet()
        if (failOpen) throw IOException("test: cannot open")
        val bytes = files[fileName] ?: throw IOException("test: no such pack")
        return ByteArrayInputStream(bytes)
    }

    companion object {
        /** Bundles [packs] (system id to raw pack bytes) gzip-compressed, with an index line each. */
        fun of(vararg packs: Pair<String, ByteArray>, extraIndex: String = ""): FakeBundled {
            val lines = StringBuilder("# test index\n")
            val files = HashMap<String, ByteArray>()
            for ((id, bytes) in packs) {
                val pack = TestPacks.open(bytes)
                lines.append("$id $id.pack.gz ${pack.dataVersion} ${pack.checksum}\n")
                files["$id.pack.gz"] = TestPacks.gzip(bytes)
            }
            lines.append(extraIndex)
            return FakeBundled(lines.toString(), files)
        }
    }
}

/** Collects log lines so tests can check what was said. */
internal class RecordingLog : IdentifyLog {
    val lines: MutableList<String> = Collections.synchronizedList(ArrayList())

    override fun log(message: String) {
        lines += message
    }
}
