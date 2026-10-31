package com.plumemu.library.scan

import com.plumemu.identify.Identifier
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.pack.ArcadeFlags
import com.plumemu.identify.pack.ArcadePackBuilder
import com.plumemu.identify.pack.BundledPacks
import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.NamePack
import com.plumemu.identify.pack.PackOpenResult
import com.plumemu.identify.pack.PackStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Name packs and ROM files made up for the scan tests: invented titles, codes and bytes, never
 * real data. The packs are bundled in memory the way the APK's assets are.
 */
internal object TestNaming {
    /** Bytes of the ROM inside `zq.zip`; the SNES pack lists their CRC. */
    val ZIPPED_ROM: ByteArray = ByteArray(4096) { (it * 7 + 3).toByte() }
    val ZIPPED_CRC: Long = CRC32().apply { update(ZIPPED_ROM) }.value

    /**
     * @param snesVersion data version of the SNES pack; 2 also renames the zipped ROM's title to
     *   [ZIPPED_TITLE_V2] (new DATs).
     * @param snesBroken the SNES pack ships but its bytes are damaged, so it cannot be installed.
     * @param arcadeBroken the same for the arcade pack.
     * @param arcadeVersion data version of the arcade pack; 2 no longer lists `zorkfghtj` (a newer
     *   MAME dropped or renamed the set).
     */
    fun naming(
        directory: File,
        log: IdentifyLog = IdentifyLog { },
        snesVersion: Long = 1,
        snesBroken: Boolean = false,
        snesCorruptAtLookup: Boolean = false,
        arcadeBroken: Boolean = false,
        arcadeVersion: Long = 1,
        crcReleases: Map<String, List<Pair<String, ByteArray>>> = emptyMap(),
    ): GameNaming {
        val snes = snesPack(snesVersion).let { if (snesCorruptAtLookup) breakFirstTitleRef(it) else it }
        val damaged = if (snesBroken) "snes" else if (arcadeBroken) "arcade" else null
        val extra = crcReleases.filterKeys { it != "gba" && it != "snes" }.map { (system, releases) -> system to crcPack(system, releases) }
        val packs = PackStore(directory, bundle("gba" to gbaPack(crcReleases["gba"].orEmpty()), "snes" to snes, *extra.toTypedArray(), "arcade" to arcadePack(arcadeVersion), "switch" to switchPack(), damaged = damaged), log)
        return GameNaming(packs, Identifier(packs, FingerprintReader(), log))
    }

    const val ZIPPED_TITLE_V2 = "Zork Quest Remastered"

    private fun gbaPack(crcReleases: List<Pair<String, ByteArray>>): ByteArray = ConsolePackBuilder("gba", 1).run {
        val src = source("Test DAT", "1")
        val zork = title("Zork Quest II", year = 1991, publisher = "Testsoft")
        release(zork, regions = listOf("USA"), source = src, codes = listOf("ZZZE" to GBA_CODE_KIND))
        addCrcReleases(crcReleases, src)
        build()
    }

    /** A pack of [system] listing each title by the CRC of its bytes (a No-Intro style DAT). */
    private fun crcPack(system: String, releases: List<Pair<String, ByteArray>>): ByteArray = ConsolePackBuilder(system, 1).run {
        addCrcReleases(releases, source("Test DAT", "1"))
        build()
    }

    private fun ConsolePackBuilder.addCrcReleases(releases: List<Pair<String, ByteArray>>, src: Int) {
        for ((name, bytes) in releases) {
            release(title(name), regions = listOf("USA"), source = src, crcs = listOf(crcOf(bytes) to bytes.size.toLong()))
        }
    }

    fun crcOf(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private fun snesPack(version: Long): ByteArray = ConsolePackBuilder("snes", version).run {
        val src = source("Test DAT", version.toString())
        val zork = title(if (version >= 2) ZIPPED_TITLE_V2 else "Zork Quest")
        release(zork, regions = listOf("USA", "Europe"), source = src, crcs = listOf(ZIPPED_CRC to ZIPPED_ROM.size.toLong()))
        title("Moonlit Harbor", year = 1994).also { release(it, regions = listOf("Japan"), source = src) }
        build()
    }

    /** A made-up Switch game listed under its base id, as the pack tool writes titledb's pack. */
    private fun switchPack(): ByteArray = ConsolePackBuilder("switch", 1).run {
        val src = source("titledb", "test")
        release(title("Zork Saga Deluxe", year = 2019), source = src, codes = listOf(SWITCH_BASE_ID to SWITCH_CODE_KIND))
        build()
    }

    const val SWITCH_BASE_ID = "0100ABCD00010000"
    private const val SWITCH_CODE_KIND = 15

    /**
     * An NSP (PFS0) listing one NCA and the ticket of [titleId]: the rights id is the title id, 7
     * zero bytes and a key generation. Nothing else of a real NSP is needed to read the id.
     */
    fun nsp(titleId: String): ByteArray {
        val names = listOf("0123456789abcdef0123456789abcdef.nca", titleId.lowercase() + "0".repeat(14) + "0b.tik")
        val table = names.joinToString("") { it + "\u0000" }.toByteArray(Charsets.US_ASCII)
        val header = java.nio.ByteBuffer.allocate(0x10 + 0x18 * names.size + table.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("PFS0".toByteArray()).putInt(names.size).putInt(table.size).putInt(0)
        var nameAt = 0
        for (name in names) {
            header.putLong(0).putLong(0).putInt(nameAt).putInt(0)
            nameAt += name.length + 1
        }
        return header.put(table).array()
    }

    private fun arcadePack(version: Long): ByteArray = ArcadePackBuilder("arcade", version).run {
        val src = source("MAME", "0.270")
        set("zorkfght", "Zork Fighters", maker = "Testsoft", year = 1996, source = src)
        set("testbios", "Test System BIOS", flags = ArcadeFlags.BIOS, source = src)
        // Short titles and clashes (ArcadeTitleClashTest).
        set("zorkfghtu", "Zork Fighters (US 961201)", maker = "Testsoft", year = 1996, parent = "zorkfght", source = src)
        if (version < 2) set("zorkfghtj", "Zork Fighters [Japan (rev 1)]", maker = "Testsoft", year = 1996, parent = "zorkfght", source = src)
        set("zorkqst", "Zork Quest (World 920101) / Zork Saga", maker = "Testsoft", year = 1992, source = src)
        // Sets that share zorkfght's short title but are not its copies (ArcadeTitleClashTest):
        // another game of the same name, and a bootleg clone.
        set("zorkfgt2", "Zork Fighters (Japan, other hardware)", maker = "Othersoft", year = 1997, source = src)
        set("zorkfghtb", "Zork Fighters (bootleg)", maker = "bootleg", year = 1996, parent = "zorkfght", source = src)
        build()
    }

    private fun bundle(vararg packs: Pair<String, ByteArray>, damaged: String? = null): BundledPacks {
        val index = StringBuilder()
        val files = HashMap<String, ByteArray>()
        for ((id, bytes) in packs) {
            val pack = (NamePack.fromBytes(bytes) as PackOpenResult.Opened).pack
            index.append("$id $id.pack.gz ${pack.dataVersion} ${pack.checksum}\n")
            val shipped = if (id == damaged) bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() } else bytes
            files["$id.pack.gz"] = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(shipped) } }.toByteArray()
        }
        return object : BundledPacks {
            override fun readIndex(): String = index.toString()

            override fun open(fileName: String): InputStream = ByteArrayInputStream(files[fileName] ?: throw IOException("no pack"))
        }
    }

    /**
     * A pack that installs (structure and SHA-256 fine) but whose first title points far outside
     * the string pool, so a lookup throws `CorruptPackException`. Offsets from identify/PACKS.md:
     * header 36 bytes, section entries of 12 (TITL is the third), trailer 32.
     */
    private fun breakFirstTitleRef(pack: ByteArray): ByteArray {
        val buffer = java.nio.ByteBuffer.wrap(pack).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val titles = buffer.getInt(36 + 2 * 12 + 4)
        buffer.putInt(titles, 1_000_000)
        val body = pack.copyOf(pack.size - 32)
        return body + java.security.MessageDigest.getInstance("SHA-256").digest(body)
    }

    /** A GBA cartridge header with game code [code] and a valid header checksum (GBATEK). */
    fun gbaRom(code: String = "ZZZE", size: Int = 1 shl 16): ByteArray {
        val rom = ByteArray(size)
        "TEST GAME".toByteArray().copyInto(rom, 0xA0)
        code.toByteArray().copyInto(rom, 0xAC)
        rom[0xB2] = 0x96.toByte()
        var check = 0
        for (i in 0xA0..0xBC) check -= rom[i].toInt() and 0xFF
        rom[0xBD] = ((check - 0x19) and 0xFF).toByte()
        return rom
    }

    fun zip(entryName: String, content: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content)
            zip.closeEntry()
        }
    }.toByteArray()

    /** The code kind byte of a GBA game code in a pack (PACKS.md, "Code kinds"). */
    private const val GBA_CODE_KIND = 1
}
