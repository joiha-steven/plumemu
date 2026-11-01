// Kotlin rewrite of rcheevos' rc_hash dispatch (src/hash/rc_hash.c). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadArchive
import com.plumemu.achievements.hash.RaCannotHashReason.Empty
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NoRomInArchive
import com.plumemu.achievements.hash.RaCannotHashReason.NotYetSupported
import com.plumemu.achievements.hash.RaCannotHashReason.UnsupportedFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * The RetroAchievements hash of one ROM: the MD5 RetroAchievements lists a game under.
 *
 * One file per call, streamed; nothing past the first 64 MiB of a file is read (rc_hash's limit),
 * except Nintendo DS, which reads what its header points at. Cartridge systems and Arcade are done
 * here; the disc systems in `DiscHashes.kt` and the files beside it; 3DS and MS-DOS are left to a
 * later step ([RaCannotHashReason.NotYetSupported]).
 */
public object RaHasher {
    /** Hashes [rom] as a game of console [consoleId] (an id from [RaConsoles]); the caller picks the dispatcher. */
    public suspend fun hash(consoleId: Int, rom: RaRomSource): RaHashResult {
        val job: Job? = currentCoroutineContext()[Job]
        return hashBlocking(consoleId, rom) { job?.ensureActive() }
    }

    /** As [hash], blocking; [check] runs between chunks and may throw to stop (cancellation). */
    public fun hashBlocking(consoleId: Int, rom: RaRomSource, check: () -> Unit = {}): RaHashResult {
        val method = methodOf(consoleId)
        val first = if (method == Method.WholeFileOrPlaylist && rom.fileName.extensionLowercase() == "m3u") {
            try {
                firstDiscOfPlaylist(rom)
            } catch (e: DiscFormatException) {
                return RaHashResult.CannotHash(e.reason, e.message)
            } catch (e: IOException) {
                return RaHashResult.Unreadable(e)
            }
        } else {
            rom
        }
        return hashFile(consoleId, method, first, check)
    }

    private fun hashFile(consoleId: Int, method: Method, rom: RaRomSource, check: () -> Unit): RaHashResult {
        val extension = rom.fileName.extensionLowercase()
        when (method) {
            Method.None -> return RaHashResult.CannotHash(UnsupportedFormat, "No hash rule for console $consoleId")
            Method.Deferred -> return RaHashResult.CannotHash(NotYetSupported, "Console $consoleId is not hashed yet")
            Method.Disc -> return hashDisc(discSystemOf(consoleId) ?: error("not a disc console"), rom, check)
            // The arcade hash is of the name only: a .zip, .7z or folder is never opened.
            Method.Arcade -> if (extension != "neo") return hashArcade(arcadePath(rom))
            else -> Unit
        }
        if (extension == "arduboy" && method == Method.Arduboy) {
            return RaHashResult.CannotHash(NotYetSupported, ".arduboy archives are not hashed yet")
        }
        if (extension in DECODER_EXTENSIONS) {
            return RaHashResult.CannotHash(NeedsDecoder, "No decoder for .$extension")
        }
        return try {
            val file = if (extension == "zip") firstRomInZip(rom, check) ?: return noRomInZip else rom
            if (file.sizeBytes <= 0L) return RaHashResult.CannotHash(Empty, "The file has no bytes")
            run(method, file, check)
        } catch (e: ZipException) {
            RaHashResult.CannotHash(BadArchive, "Cannot read the .zip: ${e.message}")
        } catch (e: IllegalArgumentException) {
            RaHashResult.CannotHash(BadArchive, "Cannot read the .zip: ${e.message}")
        } catch (e: EOFException) {
            // A zip read through to its end early is a damaged zip; any other file's end is a disk error.
            if (extension == "zip") RaHashResult.CannotHash(BadArchive, "The .zip is cut short") else RaHashResult.Unreadable(e)
        } catch (e: IOException) {
            RaHashResult.Unreadable(e)
        }
    }

    private val noRomInZip = RaHashResult.CannotHash(NoRomInArchive, "No ROM entry in the .zip")

    private fun run(method: Method, file: RaRomSource, check: () -> Unit): RaHashResult {
        val size = file.sizeBytes
        return when (method) {
            Method.WholeFile, Method.WholeFileOrPlaylist -> hashWholeFile(file, size, check)
            Method.Nes -> hashNes(file, size, check)
            Method.Lynx -> hashLynx(file, size, check)
            Method.Atari7800 -> hash7800(file, size, check)
            Method.Scv -> hashScv(file, size, check)
            Method.Snes -> hashSnes(file, size, check)
            Method.Pce -> hashPce(file, size, check)
            Method.N64 -> hashN64(file, size, check)
            Method.NintendoDs -> hashNintendoDs(file, check)
            Method.Arduboy -> hashArduboyText(file, size, check)
            Method.Arcade -> hashNeoGeoCart(file, size, check) // only a .neo file gets here
            Method.None, Method.Deferred, Method.Disc -> error("handled before run")
        }
    }

    /** Arcade hashing reads the parent folder name, so it wants the full path, decoded if it is a document URI. */
    private fun arcadePath(rom: RaRomSource): String {
        val key = rom.key
        return when {
            key.startsWith("content://") -> {
                // The decoded URI is a path only when it ends with the file name; ids like `.../document/msf%3A123` do not.
                val decoded = runCatching { URLDecoder.decode(key.replace("+", "%2B"), "UTF-8") }.getOrNull()
                if (decoded != null && decoded.endsWith("/" + rom.fileName)) decoded else rom.fileName
            }
            key.contains('/') || key.contains('\\') -> key
            else -> rom.fileName
        }
    }

    private enum class Method {
        None, Deferred, WholeFile, WholeFileOrPlaylist, Nes, Lynx, Atari7800, Scv, Snes, Pce, N64,
        NintendoDs, Arduboy, Arcade, Disc,
    }

    /** The rc_hash switch. 3DS (encrypted) and MS-DOS (zip contents) are [Method.Deferred]. */
    private fun methodOf(consoleId: Int): Method = when (consoleId) {
        RaConsoles.ARCADIA_2001, RaConsoles.ATARI_2600, RaConsoles.ATARI_JAGUAR, RaConsoles.COLECOVISION,
        RaConsoles.ELEKTOR_TV_GAMES_COMPUTER, RaConsoles.FAIRCHILD_CHANNEL_F, RaConsoles.GAMEBOY,
        RaConsoles.GAMEBOY_ADVANCE, RaConsoles.GAMEBOY_COLOR, RaConsoles.GAME_GEAR, RaConsoles.INTELLIVISION,
        RaConsoles.INTERTON_VC_4000, RaConsoles.MAGNAVOX_ODYSSEY2, RaConsoles.MASTER_SYSTEM,
        RaConsoles.MEGADUCK, RaConsoles.NEOGEO_POCKET, RaConsoles.ORIC, RaConsoles.POKEMON_MINI,
        RaConsoles.SEGA_32X, RaConsoles.SG1000, RaConsoles.SUPERVISION, RaConsoles.TI83, RaConsoles.TIC80,
        RaConsoles.UZEBOX, RaConsoles.VECTREX, RaConsoles.VIRTUAL_BOY, RaConsoles.WASM4,
        RaConsoles.WONDERSWAN, RaConsoles.ZX_SPECTRUM -> Method.WholeFile

        RaConsoles.MEGA_DRIVE, RaConsoles.AMSTRAD_PC, RaConsoles.APPLE_II, RaConsoles.COMMODORE_64,
        RaConsoles.MSX, RaConsoles.PC8800 -> Method.WholeFileOrPlaylist

        RaConsoles.ATARI_7800 -> Method.Atari7800
        RaConsoles.ATARI_LYNX -> Method.Lynx
        RaConsoles.FAMICOM_DISK_SYSTEM, RaConsoles.NINTENDO -> Method.Nes
        RaConsoles.PC_ENGINE -> Method.Pce
        RaConsoles.SUPER_CASSETTEVISION -> Method.Scv
        RaConsoles.SUPER_NINTENDO -> Method.Snes
        RaConsoles.NINTENDO_64 -> Method.N64
        RaConsoles.NINTENDO_DS, RaConsoles.NINTENDO_DSI -> Method.NintendoDs
        RaConsoles.ARCADE -> Method.Arcade
        RaConsoles.ARDUBOY -> Method.Arduboy

        RaConsoles.PLAYSTATION, RaConsoles.PLAYSTATION_2, RaConsoles.PSP, RaConsoles.SEGA_CD,
        RaConsoles.SATURN, RaConsoles.THREE_DO, RaConsoles.ATARI_JAGUAR_CD, RaConsoles.DREAMCAST,
        RaConsoles.FM_TOWNS, RaConsoles.GAMECUBE, RaConsoles.NEO_GEO_CD, RaConsoles.PC_ENGINE_CD,
        RaConsoles.PCFX, RaConsoles.PLAYSTATION_3, RaConsoles.WII -> Method.Disc

        RaConsoles.NINTENDO_3DS, RaConsoles.MS_DOS -> Method.Deferred

        else -> Method.None
    }

    /** Containers Android cannot open without a library. */
    internal val DECODER_EXTENSIONS = setOf("7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "lzma", "zst")

    /** Entries that sit next to a ROM in a zip but are not one. */
    private val NOT_A_ROM_EXTENSIONS = setOf(
        "txt", "nfo", "diz", "md", "xml", "ini", "cfg", "url", "htm", "html", "pdf", "db",
        "png", "jpg", "jpeg", "gif", "bmp", "sav", "srm", "state", "ips", "bps", "ups",
    )

    /**
     * `.zip`: RetroArch hands the core the first entry that is a ROM, and rc_hash hashes that. This
     * picks the first entry that is not a folder, a macOS `__MACOSX`/dot file or a known non-ROM
     * extension, and returns it as a source of its own. Null when there is none.
     */
    @Throws(IOException::class)
    private fun firstRomInZip(zip: RaRomSource, check: () -> Unit): RaRomSource? {
        var index = -1
        var entryName: String? = null
        var entrySize = -1L
        openZip(zip.open()).use { input ->
            while (true) {
                check()
                val entry = input.nextEntry ?: break
                index++
                val name = entry.name
                val base = name.substringAfterLast('/').substringAfterLast('\\')
                if (entry.isDirectory || base.isEmpty() || base.startsWith(".") ||
                    name.startsWith("__MACOSX/") || base.extensionLowercase() in NOT_A_ROM_EXTENSIONS
                ) {
                    continue
                }
                entryName = name
                // A streamed entry reports no size until it is read through.
                entrySize = if (entry.size >= 0) entry.size else countBytes(input, check)
                break
            }
        }
        val name = entryName ?: return null
        return ZipEntrySource(zip, index, name, entrySize)
    }

    /**
     * Entry names are read as Latin-1 so a Shift-JIS or CP437 name cannot make the read fail. The
     * charset constructor exists from Android 7; before that (minSdk is 23) it is absent, and a name
     * that is not UTF-8 then ends as BadArchive. Found by reflection so lint's API check stays honest.
     */
    private fun openZip(stream: InputStream): ZipInputStream = withCharset?.newInstance(stream, Charsets.ISO_8859_1)
        ?: ZipInputStream(stream)

    private val withCharset by lazy {
        runCatching { ZipInputStream::class.java.getConstructor(InputStream::class.java, java.nio.charset.Charset::class.java) }.getOrNull()
    }

    private fun countBytes(input: InputStream, check: () -> Unit): Long {
        val buffer = ByteArray(CHUNK_BYTES)
        var total = 0L
        while (true) {
            check()
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
        }
    }

    /** One entry of a zip, opened by reading the zip again up to that entry. */
    private class ZipEntrySource(
        private val zip: RaRomSource,
        private val index: Int,
        entryName: String,
        override val sizeBytes: Long,
    ) : RaRomSource {
        override val key: String = entryName
        override val fileName: String = entryName.substringAfterLast('/').substringAfterLast('\\')

        override fun open(): InputStream {
            val input = openZip(zip.open())
            try {
                repeat(index + 1) { input.nextEntry ?: throw ZipException("The .zip changed while it was read") }
            } catch (e: Throwable) {
                input.close()
                throw e
            }
            return input
        }
    }
}
