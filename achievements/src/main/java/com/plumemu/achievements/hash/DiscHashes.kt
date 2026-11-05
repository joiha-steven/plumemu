// Kotlin rewrite of rcheevos' disc hashes (src/hash/rc_hash_disc.c, `rc_hash_psx` and kin). rcheevos: MIT licence.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.Empty
import com.plumemu.achievements.hash.RaCannotHashReason.MissingFile
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import com.plumemu.achievements.hash.RaCannotHashReason.UnsupportedFormat
import java.io.IOException
import java.security.MessageDigest

/** The disc systems hashed from a disc image. */
internal enum class DiscSystem {
    PlayStation, PlayStation2, Psp, SegaCdOrSaturn, Ps3, PcEngineCd, PcFx, ThreeDo, JaguarCd, NeoGeoCd, Dreamcast, FmTowns,
    GameCube, Wii,
}

internal fun discSystemOf(consoleId: Int): DiscSystem? = when (consoleId) {
    RaConsoles.PLAYSTATION -> DiscSystem.PlayStation
    RaConsoles.PLAYSTATION_2 -> DiscSystem.PlayStation2
    RaConsoles.PSP -> DiscSystem.Psp
    RaConsoles.SEGA_CD, RaConsoles.SATURN -> DiscSystem.SegaCdOrSaturn
    RaConsoles.PLAYSTATION_3 -> DiscSystem.Ps3
    RaConsoles.PC_ENGINE_CD -> DiscSystem.PcEngineCd
    RaConsoles.PCFX -> DiscSystem.PcFx
    RaConsoles.THREE_DO -> DiscSystem.ThreeDo
    RaConsoles.ATARI_JAGUAR_CD -> DiscSystem.JaguarCd
    RaConsoles.NEO_GEO_CD -> DiscSystem.NeoGeoCd
    RaConsoles.DREAMCAST -> DiscSystem.Dreamcast
    RaConsoles.FM_TOWNS -> DiscSystem.FmTowns
    RaConsoles.GAMECUBE -> DiscSystem.GameCube
    RaConsoles.WII -> DiscSystem.Wii
    else -> null
}

/** Containers that need a decoder Android lacks (or a library the app does not carry). */
private val DISC_DECODER_EXTENSIONS = setOf("rvz", "wia", "wbfs", "gcz", "pbp", "ecm", "nrg", "mds", "mdf")

/** GameCube and Wii images read here (`.gcz`, `.wbfs`, `.rvz`, `.wia`) are not in the list; their other layouts (`.ciso`, and `.chd`, which is not read as one) are. */
private val NINTENDO_DECODER_EXTENSIONS = DISC_DECODER_EXTENSIONS - setOf("wbfs", "gcz", "rvz", "wia") + setOf("ciso", "chd", "zso")

private const val FM_TOWNS_FLOPPY_LIMIT = 2L * 1024 * 1024

/**
 * The hash of one disc image: a `.cue` with its files, a `.cso`, an `.iso`/`.bin`, or an `.m3u`
 * (which hashes the first disc it lists). [rom] is the file the person picked.
 */
internal fun hashDisc(system: DiscSystem, rom: RaRomSource, check: () -> Unit): RaHashResult {
    try {
        val file = if (rom.fileName.extensionLowercase() == "m3u") firstDiscOfPlaylist(rom) else rom
        val extension = file.fileName.extensionLowercase()
        if (extension in RaHasher.DECODER_EXTENSIONS) {
            return RaHashResult.CannotHash(NeedsDecoder, "No decoder for .$extension archives")
        }
        // A PSP .pbp is hashed whole, as rc_hash does; for any other system it is a packed disc.
        if (system == DiscSystem.Psp && extension == "pbp") return wholeFile(file, check)
        val nintendo = system == DiscSystem.GameCube || system == DiscSystem.Wii
        val decoderList = if (nintendo) NINTENDO_DECODER_EXTENSIONS else DISC_DECODER_EXTENSIONS
        if (extension in decoderList) {
            return RaHashResult.CannotHash(NeedsDecoder, "No decoder for .$extension disc images")
        }
        if (extension == "zip" || extension == "m3u") {
            return RaHashResult.CannotHash(UnsupportedFormat, "A .$extension cannot hold the disc here")
        }
        if (system == DiscSystem.FmTowns && isFmTownsFloppy(file, extension)) return wholeFile(file, check)
        // PS3: an .iso is the disc; anything else is the executable of an extracted game.
        if (system == DiscSystem.Ps3 && extension != "iso") return hashPs3Executable(file, check)
        if (file.sizeBytes <= 0L) return RaHashResult.CannotHash(Empty, "The file has no bytes")
        return when (system) {
            DiscSystem.GameCube -> NintendoImages.open(file, extension, check).use { hashGameCube(it, check) }
            DiscSystem.Wii -> NintendoImages.open(file, extension, check).use { hashWii(it, check) }
            DiscSystem.JaguarCd -> hashJaguarCd(file, check)
            DiscSystem.Dreamcast -> hashDreamcast(file, check)
            DiscSystem.PcFx -> hashPcFx(file, check)
            DiscSystem.PcEngineCd -> DiscReader.openTrack(file, DiscTracks.FIRST_DATA, check).use { hashPceCd(it, check) }
            else -> DiscReader.openTrack(file, 1, check).use { track -> hashTrackOne(system, track, check) }
        }
    } catch (e: DiscFormatException) {
        return RaHashResult.CannotHash(e.reason, e.message)
    } catch (e: IOException) {
        return RaHashResult.Unreadable(e)
    }
}

/** Hashes what track 1 holds for the systems that read only that track. */
private fun hashTrackOne(system: DiscSystem, track: DiscTrack, check: () -> Unit): RaHashResult = when (system) {
    DiscSystem.PlayStation -> hashPlayStation(track, check)
    DiscSystem.PlayStation2 -> hashPlayStation2(track, check)
    DiscSystem.Psp -> hashParamAndBoot(track, "PSP_GAME\\PARAM.SFO", "PSP_GAME\\SYSDIR\\EBOOT.BIN", "PSP", check)
    DiscSystem.Ps3 -> hashParamAndBoot(track, "PS3_GAME\\PARAM.SFO", "PS3_GAME\\USRDIR\\EBOOT.BIN", "PS3", check)
    DiscSystem.SegaCdOrSaturn -> hashSegaCd(track)
    DiscSystem.ThreeDo -> hash3do(track, check)
    DiscSystem.NeoGeoCd -> hashNeoGeoCd(track, check)
    DiscSystem.FmTowns -> hashFmTownsDisc(track, check)
    else -> error("${system.name} does not read track 1")
}

private fun isFmTownsFloppy(file: RaRomSource, extension: String): Boolean =
    extension == "hdm" || extension == "d88" || (extension == "bin" && file.sizeBytes < FM_TOWNS_FLOPPY_LIMIT)

private fun wholeFile(file: RaRomSource, check: () -> Unit): RaHashResult =
    if (file.sizeBytes <= 0L) RaHashResult.CannotHash(Empty, "The file has no bytes") else hashWholeFile(file, file.sizeBytes, check)

/**
 * The first disc an `.m3u` lists: the first line that is not empty or a `#` comment, read from
 * the first 1,023 bytes as rc_hash does. A path is taken relative to the playlist's folder.
 */
@Throws(IOException::class, DiscFormatException::class)
internal fun firstDiscOfPlaylist(playlist: RaRomSource): RaRomSource {
    val buffer = ByteArray(1023)
    val got = playlist.open().use { it.readUpTo(buffer, 0, buffer.size) }
    val line = String(buffer, 0, got, Charsets.ISO_8859_1).split('\n')
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: throw DiscFormatException(MissingFile, "The playlist lists no disc")
    val disc = playlist.safeSibling(line)
        ?: throw DiscFormatException(MissingFile, "The playlist names \"$line\", which is not next to it")
    if (disc.fileName.extensionLowercase() == "m3u") throw DiscFormatException(UnsupportedFormat, "A playlist cannot list a playlist")
    return disc
}

// --- shared

/** `rc_hash_cd_file`: hashes a file given by its first [sector] and [size] (at most 64 MiB), sector by sector. */
@Throws(IOException::class)
internal fun hashDiscFile(md: MessageDigest, track: DiscTrack, sector: Long, size: Long, what: String, check: () -> Unit) {
    val buffer = ByteArray(2048)
    var numRead = track.readSector(sector, buffer, buffer.size)
    if (numRead < buffer.size) throw DiscFormatException(NotARom, "Could not read $what")
    var left = minOf(size, MAX_HASH_BYTES)
    if (left < numRead) numRead = left.toInt() // a whole sector was read; only the part holding file data counts
    var next = sector
    while (true) {
        check()
        md.update(buffer, 0, numRead)
        if (left <= numRead) break
        left -= numRead
        next++
        numRead = track.readSector(next, buffer, if (left >= buffer.size) buffer.size else left.toInt())
        // A disc cut short ends here with what it has; rc_hash hashes the same part.
        if (numRead <= 0) break
    }
}

// --- PlayStation, PlayStation 2

private class BootFile(val name: String, val sector: Long, val size: Long)

private fun isSpace(c: Int): Boolean = c == ' '.code || c in 9..13

/** `rc_hash_find_playstation_executable`: the file SYSTEM.CNF names with [bootKey]; sector 0 when there is none. */
@Throws(IOException::class)
private fun findBootFile(track: DiscTrack, bootKey: String, cdromPrefix: String): BootFile? {
    val cnf = Iso9660.find(track, "SYSTEM.CNF") ?: return null
    val text = ByteArray(2048)
    val length = track.readSector(cnf.sector, text, text.size - 1)
    fun at(i: Int): Int = if (i < length) text[i].toInt() and 0xFF else 0
    fun startsWith(i: Int, word: String): Boolean = word.indices.all { at(i + it) == word[it].code }

    var p = 0
    while (at(p) != 0) {
        if (startsWith(p, bootKey)) {
            p += bootKey.length
            while (isSpace(at(p))) p++
            if (at(p) == '='.code) {
                p++
                while (isSpace(at(p))) p++
                if (startsWith(p, cdromPrefix)) p += cdromPrefix.length
                while (at(p) == '\\'.code) p++
                val start = p
                while (at(p) != 0 && !isSpace(at(p)) && at(p) != ';'.code) p++
                val name = String(text, start, minOf(p - start, 63), Charsets.ISO_8859_1)
                val found = Iso9660.find(track, name) ?: return null
                return BootFile(name, found.sector, found.size)
            }
        }
        while (at(p) != 0 && at(p) != '\n'.code) p++
        if (at(p) != 0) p++
    }
    return null
}

private fun noExecutable(): DiscFormatException = DiscFormatException(NotARom, "Could not locate primary executable")

@Throws(IOException::class)
private fun hashBootFile(track: DiscTrack, boot: BootFile, size: Long, check: () -> Unit): RaHashResult {
    // Games sharing one engine differ only in their data files; the serial in the boot name tells them apart.
    val md = newMd5()
    md.update(boot.name.toByteArray(Charsets.ISO_8859_1))
    hashDiscFile(md, track, boot.sector, size, "primary executable", check)
    return RaHashResult.Hashed(md.hex())
}

@Throws(IOException::class)
internal fun hashPlayStation(track: DiscTrack, check: () -> Unit): RaHashResult {
    val boot = findBootFile(track, "BOOT", "cdrom:")
        ?: Iso9660.find(track, "PSX.EXE")?.let { BootFile("PSX.EXE", it.sector, it.size) }
        ?: throw noExecutable()
    val head = ByteArray(32)
    if (track.readSector(boot.sector, head, head.size) < head.size) throw DiscFormatException(NotARom, "Could not read primary executable")
    var size = boot.size
    // rc_hash compares seven bytes of "PS-X EXE".
    if (String(head, 0, 7, Charsets.ISO_8859_1) == "PS-X EX") {
        // The header gives the executable's size without itself; the hash covers the header too.
        size = le32(head, 28) + 2048
    }
    return hashBootFile(track, boot, size, check)
}

@Throws(IOException::class)
internal fun hashPlayStation2(track: DiscTrack, check: () -> Unit): RaHashResult {
    val boot = findBootFile(track, "BOOT2", "cdrom0:") ?: throw noExecutable()
    if (track.readSector(boot.sector, ByteArray(4), 4) < 4) throw DiscFormatException(NotARom, "Could not read primary executable")
    return hashBootFile(track, boot, boot.size, check)
}

private fun le32(b: ByteArray, at: Int): Long =
    (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
        ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

// --- PSP

/**
 * PSP and PS3 discs: PARAM.SFO (the game's serial and name) then EBOOT.BIN (the encrypted executable),
 * both found by path.
 */
@Throws(IOException::class)
internal fun hashParamAndBoot(track: DiscTrack, paramPath: String, bootPath: String, name: String, check: () -> Unit): RaHashResult {
    val param = Iso9660.find(track, paramPath) ?: throw DiscFormatException(NotARom, "Not a $name game disc")
    val md = newMd5()
    hashDiscFile(md, track, param.sector, param.size, paramPath, check)
    val boot = Iso9660.find(track, bootPath) ?: throw DiscFormatException(NotARom, "Could not find primary executable")
    hashDiscFile(md, track, boot.sector, boot.size, bootPath, check)
    return RaHashResult.Hashed(md.hex())
}

// --- Sega CD, Saturn

/** The first 512 bytes of sector 0 (volume header and ROM header) identify the game; rc_hash hashes only those. */
@Throws(IOException::class)
internal fun hashSegaCd(track: DiscTrack): RaHashResult {
    val header = ByteArray(512)
    track.readSector(0, header, header.size)
    val id = String(header, 0, 16, Charsets.ISO_8859_1)
    if (id != "SEGADISCSYSTEM  " && id != "SEGA SEGASATURN ") throw DiscFormatException(NotARom, "Not a Sega CD or Saturn disc")
    val md = newMd5()
    md.update(header)
    return RaHashResult.Hashed(md.hex())
}
