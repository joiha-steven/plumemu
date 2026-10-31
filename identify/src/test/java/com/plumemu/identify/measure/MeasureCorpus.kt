package com.plumemu.identify.measure

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.ContainerFixtures
import com.plumemu.identify.fingerprint.FakeRoms
import com.plumemu.identify.fingerprint.disc.ChdLzmaTest
import com.plumemu.identify.fingerprint.disc.DiscImages
import com.plumemu.identify.fingerprint.disc.PackedImages
import com.plumemu.identify.pack.TestPacks
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** The kinds of file in the measurement, in the order of the table. */
internal enum class Kind(val label: String) {
    ZIP_CART("zipped cartridge"),
    LOOSE_CART("loose cartridge"),
    ISO("PS1 ISO"),
    BIN_CUE("PS1 BIN + CUE"),
    CHD_CDZL("CHD cdzl (CD)"),
    CHD_ZLIB("CHD zlib (PS2 DVD)"),
    CHD_CDLZ("CHD cdlz (CD)"),
    CHD_LZMA("CHD lzma (DVD)"),
    CSO("PSP CSO"),
    GC_ISO("GameCube ISO"),
    SEVEN_ZIP("7z cartridge"),
    ARCADE("arcade zip"),
}

/** One generated file: what to pass to `IdentifyRequest`. */
internal class CorpusFile(val file: File, val kind: Kind, val systemId: String, val cleanedTitle: String)

/**
 * Generates the 10,000-file mix of the identification measurement in [folder] and the
 * name packs that go with it: 5,000 made-up titles and 8,000 releases per console system, 40,000
 * arcade sets. About 70 % of the files are listed in their pack by CRC or code; of the rest,
 * half carry a pack title as their cleaned name (a name match) and half a made-up one.
 *
 * Disk use stays small: zipped ROMs are a small random head and zeros (they deflate to a few
 * KB; a QUICK read of a zip does not depend on what it holds), loose cartridges and disc images
 * are written as their first bytes and then extended as sparse files.
 */
internal class MeasureCorpus(private val folder: File, seed: Long = 20261021) {
    private val random = Random(seed)
    private val titles = HashMap<String, List<String>>()
    private val crcs = HashMap<String, MutableList<Triple<Int, Long, Long>>>()
    private val codes = HashMap<String, MutableList<Triple<Int, String, Int>>>()
    val arcadeSets: List<String> = List(ARCADE_SETS) { String.format(Locale.ROOT, "zz%05d", it) }
    val files = ArrayList<CorpusFile>(10_000)

    fun titlesOf(system: String): List<String> = titles.getOrPut(system) {
        val names = LinkedHashSet<String>()
        val r = Random(system.hashCode().toLong())
        while (names.size < TITLES) names += TestPacks.madeUpTitle(r)
        names.toList()
    }

    /** (title index, crc, size) the pack of [system] must list. */
    fun crcsOf(system: String): List<Triple<Int, Long, Long>> = crcs[system].orEmpty()

    /** (title index, code, kind byte) the pack of [system] must list. */
    fun codesOf(system: String): List<Triple<Int, String, Int>> = codes[system].orEmpty()

    fun generate() {
        zippedCartridges()
        looseCartridges()
        discs()
        arcade()
        check(files.size == 10_000) { "generated ${files.size} files" }
    }

    /** A title for the next file; true when its fingerprint goes into the pack. */
    private fun pickTitle(system: String): Pair<String, Int?> {
        val list = titlesOf(system)
        val index = random.nextInt(list.size)
        return when {
            random.nextInt(100) < LISTED_PERCENT -> list[index] to index
            random.nextBoolean() -> list[index] to null
            else -> TestPacks.madeUpTitle(random) to null
        }
    }

    private fun listCrc(system: String, title: Int?, crc: Long, size: Long) {
        if (title != null) crcs.getOrPut(system) { ArrayList() } += Triple(title, crc, size)
    }

    private fun listCode(system: String, title: Int?, code: String, kind: CodeKind) {
        if (title != null) codes.getOrPut(system) { ArrayList() } += Triple(title, code, kind.packByte)
    }

    private fun add(file: File, kind: Kind, system: String, title: String) {
        files += CorpusFile(file, kind, system, title)
    }

    private fun dir(name: String): File = File(folder, name).also { it.mkdirs() }

    // --- cartridges ------------------------------------------------------------------------

    /** A cartridge head of [system] with a header carrying [code] (and the size it stands for). */
    private class Head(val bytes: ByteArray, val ext: String, val code: String?, val codeKind: CodeKind?)

    private fun head(system: String, n: Int): Head {
        val letters = code3(n)
        return when (system) {
            "nes" -> Head(FakeRoms.inesHeader() + FakeRoms.filler(8 * 1024, n.toLong()), "nes", null, null)
            "snes" -> Head(FakeRoms.snes(size = 32 * 1024, code = "A$letters"), "sfc", "A$letters", CodeKind.SNES)
            "megadrive" -> {
                val number = String.format(Locale.ROOT, "%05d", n)
                Head(FakeRoms.megaDrive(serial = "GM T-$number -00", size = 8 * 1024), "md", "T-$number", CodeKind.MEGADRIVE)
            }
            "gba" -> Head(FakeRoms.gba(code = "B$letters", size = 8 * 1024), "gba", "B$letters", CodeKind.GBA)
            "gb" -> Head(FakeRoms.gb(title = "TEST $letters", size = 8 * 1024), "gb", null, null)
            "n64" -> Head(FakeRoms.n64(code = "N${letters.take(2)}E", size = 8 * 1024), "z64", "N${letters.take(2)}E", CodeKind.N64)
            else -> error(system)
        }
    }

    private fun code3(n: Int): String {
        val a = 'A' + n % 26
        val b = 'A' + (n / 26) % 26
        val c = 'A' + (n / 676) % 26
        return "$a$b$c"
    }

    /** A size in [min, max] bytes, log-uniform, rounded down to [unit]. */
    private fun size(min: Long, max: Long, unit: Long): Long {
        val value = Math.exp(Math.log(min.toDouble()) + random.nextDouble() * (Math.log(max.toDouble()) - Math.log(min.toDouble())))
        return maxOf(min, (value.toLong() / unit) * unit)
    }

    private fun zippedCartridges() {
        val mix = listOf("nes" to 1500, "snes" to 1200, "megadrive" to 1000, "gba" to 1200, "gb" to 600, "n64" to 500)
        for ((system, count) in mix) {
            val dir = dir("zip-$system")
            repeat(count) { n ->
                val head = head(system, n)
                val content = when (system) {
                    "nes" -> 16 + size(40 * 1024, 512 * 1024, 8 * 1024)
                    "snes", "megadrive" -> size(512 * 1024, 4 shl 20, 128 * 1024)
                    "gba" -> size(1 shl 20, 8 shl 20, 1 shl 20)
                    "gb" -> size(64 * 1024, 1 shl 20, 32 * 1024)
                    else -> size(4 shl 20, 12 shl 20, 1 shl 20)
                }
                val (title, listed) = pickTitle(system)
                val file = File(dir, "${title.replace(':', ' ')} ($n).zip")
                val comment = if (random.nextInt(100) < 30) "TORRENTZIPPED-" + String.format("%08X", random.nextInt()) else null
                val crc = writeZip(file, "${title.replace(':', ' ')}.${head.ext}", head.bytes, content, comment)
                listCrc(system, listed, crc, content)
                add(file, Kind.ZIP_CART, system, title)
            }
        }
    }

    /** One entry: [head] then zeros up to [length]; returns the entry's CRC. */
    private fun writeZip(file: File, entryName: String, head: ByteArray, length: Long, comment: String?): Long {
        val crc = CRC32()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            comment?.let { zip.setComment(it) }
            zip.putNextEntry(ZipEntry(entryName))
            val first = head.copyOf(minOf(head.size.toLong(), length).toInt())
            zip.write(first)
            crc.update(first)
            val zeros = ByteArray(64 * 1024)
            var left = length - first.size
            while (left > 0) {
                val n = minOf(left, zeros.size.toLong()).toInt()
                zip.write(zeros, 0, n)
                crc.update(zeros, 0, n)
                left -= n
            }
            zip.closeEntry()
        }
        return crc.value
    }

    private fun looseCartridges() {
        val mix = listOf("nes" to 300, "snes" to 300, "megadrive" to 250, "gba" to 400, "n64" to 250)
        for ((system, count) in mix) {
            val dir = dir("loose-$system")
            repeat(count) { i ->
                val n = 10_000 + i
                val head = head(system, n)
                val length = when (system) {
                    "nes" -> 16 + size(128 * 1024, 1 shl 20, 8 * 1024)
                    "snes" -> size(512 * 1024, 6 shl 20, 128 * 1024)
                    "megadrive" -> size(512 * 1024, 4 shl 20, 128 * 1024)
                    "gba" -> size(4 shl 20, 32 shl 20, 1 shl 20)
                    else -> size(8 shl 20, 32 shl 20, 1 shl 20)
                }
                val (title, listed) = pickTitle(system)
                val file = File(dir, "${title.replace(':', ' ')} ($n).${head.ext}")
                writeSparse(file, head.bytes, length)
                // QUICK gives no CRC for a loose file: the pack lists its code, where it has one.
                if (head.code != null && head.codeKind != null) listCode(system, listed, head.code, head.codeKind)
                add(file, Kind.LOOSE_CART, system, title)
            }
        }
    }

    /** [head], then a hole up to [length] bytes: the file reads as zeros but takes no disk. */
    private fun writeSparse(file: File, head: ByteArray, length: Long) {
        RandomAccessFile(file, "rw").use { out ->
            out.write(head, 0, minOf(head.size.toLong(), length).toInt())
            out.setLength(length)
        }
    }

    // --- discs -----------------------------------------------------------------------------

    private fun discs() {
        repeat(300) { n -> ps1Iso(n) }
        repeat(250) { n -> binCue(1000 + n) }
        repeat(200) { n -> cdzlChd(2000 + n) }
        repeat(100) { n -> fixedDisc(Kind.CHD_ZLIB, "ps2", "chd", ps2Chd, "SLES-99998", CodeKind.PS2, n) }
        repeat(150) { n -> fixedDisc(Kind.CHD_CDLZ, "psx", "chd", cdlzChd, "SLUS-99999", CodeKind.PS1, n) }
        repeat(50) { n -> fixedDisc(Kind.CHD_LZMA, "psx", "chd", lzmaChd, "SLUS-99999", CodeKind.PS1, 200 + n) }
        repeat(150) { n -> pspCso(n) }
        repeat(150) { n -> gameCube(n) }
        repeat(150) { n -> sevenZip(n) }
    }

    private val ps2Chd by lazy { PackedImages.chd(DiscImages.ps2Iso(padTo = 256 * 1024), 8192, 2048, listOf("zlib")) }
    private val cdlzChd by lazy { ChdLzmaTest.chdOf(ContainerFixtures.named("ps1-cd.cdlz.bin"), "cdlz") }
    private val lzmaChd by lazy { ChdLzmaTest.chdOf(ContainerFixtures.named("ps1-dvd.lzma.bin"), "lzma") }

    private fun ps1Boot(n: Int): Pair<String, String> {
        val digits = String.format(Locale.ROOT, "%05d", n)
        return "BOOT = cdrom:\\SLUS_${digits.take(3)}.${digits.drop(3)};1" to "SLUS-$digits"
    }

    private fun discTitle(system: String, code: String, kind: CodeKind): String {
        val (title, listed) = pickTitle(system)
        listCode(system, listed, code, kind)
        return title
    }

    private fun ps1Iso(n: Int) {
        val (boot, serial) = ps1Boot(n)
        val title = discTitle("psx", serial, CodeKind.PS1)
        val file = File(dir("psx"), "${title.replace(':', ' ')} ($n).iso")
        writeSparse(file, DiscImages.ps1Iso(boot, padTo = 64 * 1024), size(300L shl 20, 700L shl 20, 2048))
        add(file, Kind.ISO, "psx", title)
    }

    private fun binCue(n: Int) {
        val (boot, serial) = ps1Boot(n)
        val title = discTitle("psx", serial, CodeKind.PS1)
        val base = "${title.replace(':', ' ')} ($n)"
        val dir = dir("psx")
        writeSparse(File(dir, "$base.bin"), DiscImages.toRaw(DiscImages.ps1Iso(boot, padTo = 64 * 1024), mode = 2), size(300L shl 20, 700L shl 20, 2352))
        val cue = File(dir, "$base.cue")
        cue.writeText("FILE \"$base.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n")
        add(cue, Kind.BIN_CUE, "psx", title)
    }

    private fun cdzlChd(n: Int) {
        val (boot, serial) = ps1Boot(n)
        val title = discTitle("psx", serial, CodeKind.PS1)
        val raw = DiscImages.toRaw(DiscImages.ps1Iso(boot, padTo = 128 * 1024), mode = 2)
        val chd = PackedImages.chd(PackedImages.toFrames(raw), 2448 * 8, 2448, listOf("cdzl"))
        val file = File(dir("psx"), "${title.replace(':', ' ')} ($n).chd")
        file.writeBytes(chd)
        add(file, Kind.CHD_CDZL, "psx", title)
    }

    private fun fixedDisc(kind: Kind, system: String, ext: String, bytes: ByteArray, code: String, codeKind: CodeKind, n: Int) {
        val title = discTitle(system, code, codeKind)
        val file = File(dir(system), "${title.replace(':', ' ')} (${kind.name.lowercase(Locale.ROOT)} $n).$ext")
        file.writeBytes(bytes)
        add(file, kind, system, title)
    }

    private fun pspCso(n: Int) {
        val serial = String.format(Locale.ROOT, "ULUS-1%04d", n)
        val title = discTitle("psp", serial, CodeKind.PSP)
        val file = File(dir("psp"), "${title.replace(':', ' ')} ($n).cso")
        file.writeBytes(PackedImages.cso(DiscImages.pspIso(serial, "TEST GAME $n", padTo = 512 * 1024)))
        add(file, Kind.CSO, "psp", title)
    }

    private fun gameCube(n: Int) {
        val id = "G" + code3(n).take(2) + "E01"
        val title = discTitle("gc", id, CodeKind.GAMECUBE)
        val file = File(dir("gc"), "${title.replace(':', ' ')} ($n).iso")
        writeSparse(file, DiscImages.nintendoHeader(id, "Test Game $n").copyOf(64 * 1024), 1_459_978_240L)
        add(file, Kind.GC_ISO, "gc", title)
    }

    private fun sevenZip(n: Int) {
        val (fixture, system) = listOf(
            "plain.7z" to "gba", "lzma-header.7z" to "gba", "lzma2-header.7z" to "nes", "solid.7z" to "gb",
        )[n % 4]
        val archive = ContainerFixtures.named(fixture)
        val (title, listed) = pickTitle(system)
        val rom = archive.entries.filter { !it.isDirectory }.maxBy { it.size }
        listCrc(system, listed, rom.crc, rom.size)
        val file = File(dir("7z-$system"), "${title.replace(':', ' ')} ($n).7z")
        file.writeBytes(archive.bytes)
        add(file, Kind.SEVEN_ZIP, system, title)
    }

    // --- arcade ----------------------------------------------------------------------------

    private fun arcade() {
        val dir = dir("arcade")
        repeat(1000) { n ->
            val shortName = if (n % 10 == 9) String.format(Locale.ROOT, "qq%04d", n) else arcadeSets[random.nextInt(arcadeSets.size)]
            val file = File(dir, "$shortName.zip").takeIf { !it.exists() } ?: File(dir, "${shortName}x$n.zip")
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                repeat(random.nextInt(10, 121)) { e ->
                    zip.putNextEntry(ZipEntry(String.format(Locale.ROOT, "%s.%d%c", shortName.take(4), e, 'a' + e % 26)))
                    zip.write(FakeRoms.filler(64, e.toLong()))
                    zip.closeEntry()
                }
            }
            add(file, Kind.ARCADE, "arcade", shortName)
        }
    }

    companion object {
        const val TITLES = 5_000
        const val RELEASES = 8_000
        const val ARCADE_SETS = 40_000
        const val LISTED_PERCENT = 70
        val CONSOLE_SYSTEMS = listOf("nes", "snes", "megadrive", "gba", "gb", "n64", "psx", "ps2", "psp", "gc")
    }
}
