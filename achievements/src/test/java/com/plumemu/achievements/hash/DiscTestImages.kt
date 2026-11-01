package com.plumemu.achievements.hash

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Deflater

/** Files that sit in one folder, so a `.cue` can find its `.bin` and an `.m3u` its discs. */
internal class Folder(private val files: Map<String, ByteArray>) {
    constructor(vararg files: Pair<String, ByteArray>) : this(files.toMap())

    fun source(name: String): RaRomSource = FolderSource(name.substringAfterLast('/'), files.getValue(name), files)

    private class FolderSource(
        override val fileName: String,
        private val bytes: ByteArray,
        private val files: Map<String, ByteArray>,
    ) : RaRomSource {
        override val key: String = fileName
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun open(): InputStream = ByteArrayInputStream(bytes)
        override fun sibling(name: String): RaRomSource? = files[name]?.let { FolderSource(name.substringAfterLast('/'), it, files) }
    }
}

// --- rcheevos test/hash/hash_data.c: generate_iso9660_bin, generate_iso9660_file, generate_psx_bin, generate_ps2_bin

internal fun isoBin(numSectors: Int, label: String): ByteArray {
    val image = ByteArray(numSectors * 2048)
    val volume = 16 * 2048
    "\u0001CD001\u0001\u0000".forEachIndexed { i, c -> image[volume + i] = c.code.toByte() }
    label.forEachIndexed { i, c -> image[volume + 40 + i] = c.code.toByte() }
    image[volume + 128] = (2048 and 0xFF).toByte()
    image[volume + 129] = (2048 shr 8).toByte()
    image[volume + 158] = 17 // root directory record location
    image[17 * 2048 - 4] = 18 // next free sector: a helper of the generator, not part of ISO 9660
    return image
}

/** Adds [filename] (`\` between folders) to [image]; returns the offset of its contents so the caller can edit them. */
internal fun isoAddFile(image: ByteArray, filename: String, contents: ByteArray?, size: Int = contents!!.size): Int {
    fun u8(at: Int) = image[at].toInt() and 0xFF
    val nextFreeAt = 17 * 2048 - 4
    var next = u8(nextFreeAt) or (u8(nextFreeAt + 1) shl 8) or (u8(nextFreeAt + 2) shl 16)
    var entry = 17 * 2048
    var name = filename.removePrefix("\\")
    while (true) {
        val slash = name.indexOf('\\')
        if (slash < 0) break
        var found = false
        while (u8(entry) != 0) {
            val sameName = u8(entry + 33 + slash) == 0 && (0 until slash).all { u8(entry + 33 + it) == name[it].code }
            if (u8(entry + 25) != 0 && sameName) {
                entry = u8(entry + 2) * 2048
                found = true
                break
            }
            entry += u8(entry)
        }
        if (!found) {
            image[entry] = (slash + 48).toByte()
            image[entry + 2] = (next and 0xFF).toByte()
            image[entry + 3] = ((next shr 8) and 0xFF).toByte()
            image[entry + 25] = 2
            image[entry + 32] = slash.toByte()
            name.take(slash).forEachIndexed { i, c -> image[entry + 33 + i] = c.code.toByte() }
            entry = next * 2048
            next++
        }
        name = name.substring(slash + 1)
    }
    while (u8(entry) != 0) entry += u8(entry)
    val len = name.length
    image[entry] = (len + 48).toByte()
    image[entry + 2] = (next and 0xFF).toByte()
    image[entry + 3] = ((next shr 8) and 0xFF).toByte()
    image[entry + 10] = (size and 0xFF).toByte()
    image[entry + 11] = ((size shr 8) and 0xFF).toByte()
    image[entry + 12] = ((size shr 16) and 0xFF).toByte()
    image[entry + 32] = (len + 2).toByte()
    name.forEachIndexed { i, c -> image[entry + 33 + i] = c.code.toByte() }
    image[entry + 33 + len] = ';'.code.toByte()
    image[entry + 34 + len] = '1'.code.toByte()
    val start = next * 2048
    if (contents != null) contents.copyInto(image, start, 0, size) else fillImage(image, start, size)
    next += (size + 2047) / 2048
    image[nextFreeAt] = (next and 0xFF).toByte()
    image[nextFreeAt + 1] = ((next shr 8) and 0xFF).toByte()
    image[nextFreeAt + 2] = ((next shr 16) and 0xFF).toByte()
    return start
}

internal fun psxBin(binaryName: String, binarySize: Int): ByteArray {
    val cnf = "BOOT=cdrom:\\$binaryName;1\nTCB=4\nEVENT=10\nSTACK=801FFFF0\n"
    val image = isoBin((binarySize + 2047) / 2048 + 20, "TEST")
    isoAddFile(image, "SYSTEM.CNF", cnf.toByteArray())
    val exe = isoAddFile(image, binaryName, null, binarySize)
    markPsxExe(image, exe, binarySize)
    return image
}

internal fun markPsxExe(image: ByteArray, exe: Int, binarySize: Int) {
    "PS-X EXE".forEachIndexed { i, c -> image[exe + i] = c.code.toByte() }
    val bodySize = binarySize - 2048
    for (i in 0 until 4) image[exe + 28 + i] = ((bodySize shr (8 * i)) and 0xFF).toByte()
}

internal fun ps2Bin(binaryName: String, binarySize: Int): ByteArray {
    val cnf = "BOOT2 = cdrom0:\\$binaryName;1\nVER = 1.0\nVMODE = NTSC\n"
    val image = isoBin((binarySize + 2047) / 2048 + 20, "TEST")
    isoAddFile(image, "SYSTEM.CNF", cnf.toByteArray())
    val exe = isoAddFile(image, binaryName, null, binarySize)
    byteArrayOf(0x7f, 0x45, 0x4c, 0x46).copyInto(image, exe)
    return image
}

internal fun pspBin(): ByteArray {
    val image = isoBin(160, "TEST")
    isoAddFile(image, "PSP_GAME\\PARAM.SFO", genericFile(690))
    isoAddFile(image, "PSP_GAME\\SYSDIR\\EBOOT.BIN", genericFile(273470))
    return image
}

/** `generate_generic_file(512)` with a marker, padded to one sector so it is a disc image (the C test reads 512 bytes). */
internal fun segaBin(marker: String): ByteArray {
    val image = ByteArray(2048)
    fillImage(image, 0, 512)
    marker.forEachIndexed { i, c -> image[i] = c.code.toByte() }
    return image
}

// --- containers

private fun bcd(n: Int): Int = ((n / 10) shl 4) or (n % 10)

/** Cooked 2048-byte sectors as raw CD sectors: sync, an MSF stamp, the mode, the data, filler. [mode] is 1 or 2 (Mode 2 has 8 subheader bytes). */
internal fun rawSectors(cooked: ByteArray, mode: Int, firstLba: Int = 0): ByteArray {
    val sectors = (cooked.size + 2047) / 2048
    val out = ByteArray(sectors * 2352)
    for (s in 0 until sectors) {
        val base = s * 2352
        out[base] = 0
        for (i in 1..10) out[base + i] = -1
        out[base + 11] = 0
        val frames = firstLba + s + 150
        out[base + 12] = bcd(frames / 75 / 60).toByte()
        out[base + 13] = bcd(frames / 75 % 60).toByte()
        out[base + 14] = bcd(frames % 75).toByte()
        out[base + 15] = mode.toByte()
        val dataAt = base + if (mode == 1) 16 else 24
        cooked.copyInto(out, dataAt, s * 2048, minOf(cooked.size, s * 2048 + 2048))
    }
    return out
}

/** [sectors] raw audio sectors, none of which looks like a data sector. */
internal fun audioSectors(sectors: Int): ByteArray = ByteArray(sectors * 2352) { (it * 7 + 3).toByte() }

private fun msf(sector: Int): String = "%02d:%02d:%02d".format(sector / 75 / 60, sector / 75 % 60, sector % 75)

/** The ways one cooked disc image reaches the hasher; each gives the same hash. [name] is the file the person picks. */
internal class DiscCase(val name: String, val source: RaRomSource)

internal fun discCases(cooked: ByteArray, label: String = "game", isoLayout: Boolean = true): List<DiscCase> {
    val cases = ArrayList<DiscCase>()
    fun add(name: String, source: RaRomSource) = cases.add(DiscCase(name, source))
    add("iso", MemorySource("$label.iso", cooked))
    add("bin, cooked", MemorySource("$label.bin", cooked))
    val mode1 = rawSectors(cooked, 1)
    // Mode 2 raw sectors keep their data 24 bytes in, which only "CD001" tells from Mode 1; images without it use Mode 1.
    val dataMode = if (isoLayout) 2 else 1
    val mode2 = rawSectors(cooked, dataMode)
    val modeName = "MODE$dataMode/2352"
    val cue = { mode: String -> "FILE \"$label.bin\" BINARY\n  TRACK 01 $mode\n    INDEX 01 00:00:00\n".toByteArray() }
    add("cue MODE1/2352", Folder("$label.cue" to cue("MODE1/2352"), "$label.bin" to mode1).source("$label.cue"))
    add("cue $modeName", Folder("$label.cue" to cue(modeName), "$label.bin" to mode2).source("$label.cue"))
    add("cue MODE1/2048", Folder("$label.cue" to cue("MODE1/2048"), "$label.bin" to cooked).source("$label.cue"))
    // The sheet lies about the mode; the sector headers say what the file really is (needs a volume descriptor to look at).
    if (cooked.size > 17 * 2048) {
        add("cue lying about the mode", Folder("$label.cue" to cue("MODE1/2048"), "$label.bin" to mode2).source("$label.cue"))
    }
    // Data track then an audio track, in one file and in two.
    val audio = audioSectors(40)
    val dataSectors = mode2.size / 2352
    val oneBin = "FILE \"$label.bin\" BINARY\n  TRACK 01 $modeName\n    INDEX 01 00:00:00\n" +
        "  TRACK 02 AUDIO\n    INDEX 00 ${msf(dataSectors)}\n    INDEX 01 ${msf(dataSectors + 10)}\n"
    add("cue, data + audio in one bin", Folder("$label.cue" to oneBin.toByteArray(), "$label.bin" to mode2 + audio).source("$label.cue"))
    val twoBins = "FILE \"$label (Track 1).bin\" BINARY\n  TRACK 01 $modeName\n    INDEX 01 00:00:00\n" +
        "FILE \"$label (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n"
    add(
        "cue, two bins (spaces in names)",
        Folder("$label.cue" to twoBins.toByteArray(), "$label (Track 1).bin" to mode2, "$label (Track 2).bin" to audio).source("$label.cue"),
    )
    // Track 1 behind a 2-second pregap: INDEX 00 at the start, INDEX 01 150 sectors in.
    val pregap = "FILE \"$label.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n"
    // The track start comes from the volume descriptor, so a one-sector image cannot have a pregap. With INDEX 00 before
    // INDEX 01 rc_hash would read the pregap's zeros as sector 0; this reader is deliberately more lenient.
    if (cooked.size > 17 * 2048) {
        add("cue, pregap", Folder("$label.cue" to pregap.toByteArray(), "$label.bin" to ByteArray(150 * 2352) + mode1).source("$label.cue"))
    }
    // A cue in a folder next to nothing else but its bin, with a sub-folder path.
    val sub = "FILE \"tracks/$label.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n"
    add("cue, bin in a sub-folder", Folder("$label.cue" to sub.toByteArray(), "tracks/$label.bin" to mode1).source("$label.cue"))
    // Compressed images: blocks alternate deflate and stored, with and without an index shift.
    add("cso", MemorySource("$label.cso", csoOf(cooked)))
    add("cso, aligned index", MemorySource("$label.cso", csoOf(cooked, align = 2)))
    add("cso, 8 KiB blocks", MemorySource("$label.cso", csoOf(cooked, blockSize = 8192)))
    return cases
}

/** A `.cso`: [blockSize]-byte blocks, even ones deflated, odd ones stored raw, offsets shifted left by [align]. */
internal fun csoOf(cooked: ByteArray, blockSize: Int = 2048, align: Int = 0, version: Int = 1): ByteArray {
    val blocks = (cooked.size + blockSize - 1) / blockSize
    val step = 1 shl align
    fun roundUp(n: Int) = (n + step - 1) / step * step
    val bodies = ArrayList<ByteArray>()
    val stored = ArrayList<Boolean>()
    for (n in 0 until blocks) {
        val plain = cooked.copyOfRange(n * blockSize, minOf(cooked.size, (n + 1) * blockSize))
        val packed = if (n % 2 == 0) deflateRaw(plain) else plain
        val useRaw = n % 2 == 1 || packed.size >= plain.size
        bodies += if (useRaw) plain else packed
        stored += useRaw
    }
    val header = ByteArrayOutputStream()
    header.write("CISO".toByteArray())
    header.write(le(24, 4)); header.write(le(cooked.size.toLong(), 8)); header.write(le(blockSize.toLong(), 4))
    header.write(version); header.write(align); header.write(0); header.write(0)
    var position = roundUp(24 + 4 * (blocks + 1))
    val index = ByteArrayOutputStream()
    val data = ByteArrayOutputStream()
    for (n in 0 until blocks) {
        index.write(le(((position / step).toLong() or (if (stored[n]) 0x80000000L else 0L)), 4))
        val body = bodies[n]
        data.write(body)
        val padded = roundUp(body.size)
        data.write(ByteArray(padded - body.size))
        position += padded
    }
    index.write(le((position / step).toLong(), 4))
    val out = ByteArrayOutputStream()
    out.write(header.toByteArray()); out.write(index.toByteArray())
    out.write(ByteArray(roundUp(24 + 4 * (blocks + 1)) - (24 + 4 * (blocks + 1))))
    out.write(data.toByteArray())
    return out.toByteArray()
}

private fun le(value: Long, bytes: Int): ByteArray = ByteArray(bytes) { ((value shr (8 * it)) and 0xFF).toByte() }
private fun le(value: Int, bytes: Int): ByteArray = le(value.toLong(), bytes)

internal fun deflateRaw(data: ByteArray): ByteArray {
    val deflater = Deflater(9, true)
    deflater.setInput(data)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    return out.toByteArray()
}
