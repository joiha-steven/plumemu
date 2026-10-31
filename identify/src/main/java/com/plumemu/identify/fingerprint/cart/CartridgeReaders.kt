package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind

import com.plumemu.identify.source.RandomAccessSource
import java.io.IOException
import java.util.Locale

/**
 * Which header readers to try for a file, by Plumemu system id (the `id` of a system in
 * `platforms/src/main/assets/platforms.json`, or one of its folder names) and extension.
 *
 * | System ids and folder names | Readers, in order |
 * |---|---|
 * | gba, gameboyadvance, "game boy advance" | GBA, then Game Boy (the system also lists .gb/.gbc) |
 * | gb, gameboy, "game boy", gbc, gameboycolor, "game boy color", sgb | Game Boy |
 * | nds, ds, nintendods, ndsi | Nintendo DS |
 * | n64, nintendo64 | Nintendo 64 |
 * | megadrive, genesis, megadrivejp, md, "sega genesis", genesismsu, sega32x, sega32xjp, sega32xna | Mega Drive |
 * | snes, sfc, snesna, superfamicom, "super nintendo", snesmsu1 | SNES |
 * | nes, famicom, fc | NES, then FDS (the system also lists .fds) |
 * | fds | FDS, then NES |
 * | atari7800 | Atari 7800 |
 * | atarilynx, lynx | Lynx |
 * | n3ds, 3ds | Nintendo 3DS (NCSD, NCCH, CIA) |
 *
 * Within a system, readers that claim the file's extension go first. With no system, only
 * readers that claim the extension are tried (`.bin` is claimed by none: it means too many
 * things). A system not in the table gets no header read. Not covered yet: Satellaview and
 * Sufami Turbo (other header layouts), Master System and Game Gear ("TMR SEGA" at 0x7FF0).
 */
internal object CartridgeReaders {
    private val all: List<CartridgeReader> = listOf(
        GbaReader, NdsReader, GameBoyReader, Nintendo64Reader, MegaDriveReader, SnesReader,
        NesReader, FdsReader, Atari7800Reader, LynxReader, N3dsReader,
    )

    private val bySystem: Map<String, List<CartridgeReader>> = buildMap {
        fun put(readers: List<CartridgeReader>, vararg ids: String) = ids.forEach { put(it, readers) }
        put(listOf(GbaReader, GameBoyReader), "gba", "gameboyadvance", "game boy advance")
        put(listOf(GameBoyReader), "gb", "gameboy", "game boy", "gbc", "gameboycolor", "game boy color", "sgb")
        put(listOf(NdsReader), "nds", "ds", "nintendods", "ndsi")
        put(listOf(Nintendo64Reader), "n64", "nintendo64")
        put(
            listOf(MegaDriveReader),
            "megadrive", "genesis", "megadrivejp", "md", "sega genesis", "genesismsu",
            "sega32x", "sega32xjp", "sega32xna",
        )
        put(listOf(SnesReader), "snes", "sfc", "snesna", "superfamicom", "super nintendo", "snesmsu1")
        put(listOf(NesReader, FdsReader), "nes", "famicom", "fc")
        put(listOf(FdsReader, NesReader), "fds")
        put(listOf(Atari7800Reader), "atari7800")
        put(listOf(LynxReader), "atarilynx", "lynx")
        put(listOf(N3dsReader), "n3ds", "3ds")
    }

    /** Every system id (and folder name) that has a header reader: its loose files are cartridges. */
    val systemsWithReaders: Set<String> get() = bySystem.keys

    /** The system ids whose readers may find a header or a byte order in a zip entry (`storedFormMayDiffer`). */
    val systemsWhoseStoredFormMayDiffer: Set<String>
        get() = bySystem.filterValues { readers -> readers.any { it.storedFormMayDiffer } }.keys

    /**
     * The code numbering a cartridge of [systemId] with [extension] is read with: the first reader
     * [candidates] gives (one claiming the extension when there is one); null when none or it reads no code.
     */
    fun codeKindFor(systemId: String, extension: String): CodeKind? = candidates(systemId, extension).firstOrNull()?.codeKind

    /**
     * Like [codeKindFor], but only from a reader of [systemId] that claims [extension] itself; null
     * for an archive (`.zip`, `.7z`) or any other extension, whose content may be another format.
     */
    fun codeKindClaiming(systemId: String, extension: String): CodeKind? {
        val ext = extension.lowercase(Locale.ROOT)
        return candidates(systemId, ext).firstOrNull { ext in it.extensions }?.codeKind
    }

    /** Readers to try in order; empty when there is nothing to read. */
    fun candidates(systemId: String?, extension: String): List<CartridgeReader> {
        val ext = extension.lowercase(Locale.ROOT)
        if (systemId == null) return all.filter { ext in it.extensions }
        val forSystem = bySystem[systemId.trim().lowercase(Locale.ROOT)] ?: return emptyList()
        return forSystem.sortedByDescending { ext in it.extensions }
    }

    /**
     * The first header one of [readers] finds in [source]; failing that, a copier header told by
     * the length alone ([CartridgeReader.skipWithoutHeader], SNES) with a note; else null. The
     * same detection for a loose file and an unpacked zip entry.
     */
    @Throws(IOException::class)
    fun detect(readers: List<CartridgeReader>, source: RandomAccessSource): CartridgeHeader? =
        readers.firstNotNullOfOrNull { it.read(source) }
            ?: readers.firstNotNullOfOrNull { reader ->
                val skip = reader.skipWithoutHeader(source.size)
                if (skip <= 0) return@firstNotNullOfOrNull null
                val note = "${reader.name}: no valid internal header; $skip-byte copier header skipped by file length"
                CartridgeHeader(headerBytesSkipped = skip, notes = listOf(note))
            }
}
