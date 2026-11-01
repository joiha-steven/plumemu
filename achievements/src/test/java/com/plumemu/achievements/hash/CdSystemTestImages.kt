package com.plumemu.achievements.hash

// Image builders ported from rcheevos test/hash/hash_data.c (the systems after PlayStation).

private fun ByteArray.put(at: Int, text: String) = text.forEachIndexed { i, c -> this[at + i] = c.code.toByte() }

private fun ByteArray.putBe24(at: Int, value: Int) {
    this[at] = (value shr 16).toByte()
    this[at + 1] = (value shr 8).toByte()
    this[at + 2] = value.toByte()
}

/** `generate_3do_bin`: a volume header, [rootSectors] directory sectors (the last one holds LaunchMe), then the program. */
internal fun threeDoBin(rootSectors: Int, binarySize: Int): ByteArray {
    val volumeHeader = byteArrayOf(0x01, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x01, 0x00) + ByteArray(32) +
        "CD-ROM".toByteArray() + ByteArray(26) +
        intArrayOf(
            0x2D, 0x79, 0x6E, 0x96, 0, 0, 8, 0, 0, 0, 5, 0, 0x31, 0x5A, 0xF2, 0xE6, 0, 0, 0, 1, 0, 0, 8, 0, 0, 0, 0, 6,
            0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1,
        ).map { it.toByte() }.toByteArray()
    fun field(vararg values: Int) = values.map { it.toByte() }.toByteArray()
    val header = field(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0, 0, 0, 0, 0xA4, 0, 0, 0, 0x14)
    fun entry(flags: Int, name: String, directoryBlock: Int): ByteArray {
        val bytes = ByteArray(0x48)
        bytes[3] = flags.toByte()
        bytes[0x0C + 2] = 8 // block size 0x0800
        name.forEachIndexed { i, c -> bytes[0x20 + i] = c.code.toByte() }
        bytes[0x47] = directoryBlock.toByte()
        return bytes
    }
    val directory = header + entry(7, "folder", 0).also { it[0x0C] = 0; it[0x0D] = 0; it[0x0E] = 8; it[0x0F] = 0 } +
        entry(2, "LaunchMe", 2)
    val sectors = (binarySize + 2047) / 2048
    val image = ByteArray((rootSectors + 1 + sectors) * 2048)
    volumeHeader.copyInto(image)
    image[0x5B] = rootSectors.toByte()
    var offset = 2048
    for (i in 0 until rootSectors) {
        directory.copyInto(image, offset)
        if (i < rootSectors - 1) {
            image[offset + 0] = 0; image[offset + 1] = 0; image[offset + 2] = 0; image[offset + 3] = (i + 1).toByte()
            image.put(offset + 0x14 + 0x48 + 0x20, "filename")
        } else {
            image[offset + 0x14 + 0x48 + 0x11] = (binarySize shr 16).toByte()
            image[offset + 0x14 + 0x48 + 0x12] = (binarySize shr 8).toByte()
            image[offset + 0x14 + 0x48 + 0x13] = binarySize.toByte()
            image[offset + 0x14 + 0x48 + 0x16] = (sectors shr 8).toByte()
            image[offset + 0x14 + 0x48 + 0x17] = sectors.toByte()
            image[offset + 0x14 + 0x48 + 0x47] = (i + 2).toByte()
        }
        if (i > 0) {
            image[offset + 4] = 0; image[offset + 5] = 0; image[offset + 6] = 0; image[offset + 7] = (i - 1).toByte()
        }
        offset += 2048
    }
    fillImage(image, offset, binarySize)
    return image
}

private const val DREAMCAST_HEADER = "SEGA SEGAKATANA SEGA ENTERPRISES5966 GD-ROM1/1   U      918FA01 X-1234N   V1.001" +
    "20200910        1ST_READ.BIN    RETROACHIEVEMENTUNIT TEST       "

/** `generate_dreamcast_bin`: IP.BIN in sector 0, a volume in 16-17, the boot file from sector 18 (listed at [trackFirstSector] + 18). */
internal fun dreamcastBin(trackFirstSector: Int, binarySize: Int, bootName: String = "1ST_READ.BIN"): ByteArray {
    val sectors = (binarySize + 2047) / 2048
    val image = ByteArray((sectors + 18) * 2048)
    image.put(0, DREAMCAST_HEADER.padEnd(256, ' '))
    image.put(96, bootName.padEnd(16, ' '))
    image.put(2048 * 16, "\u0001CD001")
    image[2048 * 16 + 156 + 2] = (45017 and 0xFF).toByte()
    image[2048 * 16 + 156 + 3] = ((45017 shr 8) and 0xFF).toByte()
    image[2048 * 16 + 156 + 4] = ((45017 shr 16) and 0xFF).toByte()
    val record = 2048 * 17
    image[record] = 0x30
    val sector = trackFirstSector + 18
    image[record + 2] = sector.toByte(); image[record + 3] = (sector shr 8).toByte(); image[record + 4] = (sector shr 16).toByte()
    for (i in 0 until 4) image[record + 10 + i] = (binarySize shr (8 * i)).toByte()
    image[record + 32] = 0x0E
    image.put(record + 33, "1ST_READ.BIN;1")
    fillImage(image, 2048 * 18, sectors * 2048)
    return image
}

/** `generate_pce_cd_bin`. */
internal fun pceCdBin(binarySectors: Int): ByteArray {
    val header = ByteArray(128)
    byteArrayOf(0, 0, 2, 0x14, 0, 0x40, 0, 0x40, 0, 1, 2, 3, 4).copyInto(header)
    header.put(32, "PC Engine CD-ROM SYSTEM")
    header.put(56, "Copyright HUDSON SOFT / NEC Home Electronics,Ltd.")
    header.put(106, "GAMENAME".padEnd(22, ' '))
    val image = ByteArray((binarySectors + 2) * 2048)
    header.copyInto(image, 2048)
    image[2048 + 3] = binarySectors.toByte()
    fillImage(image, 4096, binarySectors * 2048)
    return image
}

/** `generate_pcfx_bin`. */
internal fun pcFxBin(binarySectors: Int): ByteArray {
    val header = ByteArray(128)
    header.put(0, "GAMENAME")
    byteArrayOf(2, 0, 0, 0, 0x14, 0, 0, 0, 0, 0x80.toByte(), 0, 0, 0, 0x80.toByte(), 0, 0).copyInto(header, 32)
    header.put(48, "N/A")
    header.put(52, "rcheevostest")
    byteArrayOf(0, 1, 1, 0).copyInto(header, 116) // version and country
    header.put(120, "2020XXXX")
    val image = ByteArray((binarySectors + 2) * 2048)
    image.put(0, "PC-FX:Hu_CD-ROM")
    header.copyInto(image, 2048)
    image[2048 + 0x24] = binarySectors.toByte()
    fillImage(image, 4096, binarySectors * 2048)
    return image
}

/** `generate_jaguarcd_bin`: [headerOffset] bytes, the approved-data header, the program size, then the program; sector-aligned. */
internal fun jaguarCdBin(headerOffset: Int, binarySize: Int, byteSwapped: Boolean): ByteArray {
    val image = ByteArray((binarySize + 64 + 32 + 8 + 2351) / 2352 * 2352)
    for (i in 0 until 64 step 4) image.put(headerOffset + i, "ATRI")
    image.put(headerOffset + 64, "ATARI APPROVED DATA HEADER ATRI ")
    image[headerOffset + 64 + 32 + 2] = 0xA0.toByte()
    image[headerOffset + 64 + 32 + 4 + 1] = (binarySize shr 16).toByte()
    image[headerOffset + 64 + 32 + 4 + 2] = (binarySize shr 8).toByte()
    image[headerOffset + 64 + 32 + 4 + 3] = binarySize.toByte()
    val body = headerOffset + 64 + 32 + 8
    fillImage(image, body, image.size - body)
    if (byteSwapped) {
        for (i in image.indices step 2) {
            val t = image[i]; image[i] = image[i + 1]; image[i + 1] = t
        }
    }
    return image
}

/** `generate_gamecube_iso`: [mb] MiB of filler with the GameCube magic word and a main executable of nine small segments. */
internal fun gameCubeIso(mb: Int): ByteArray {
    val image = ByteArray(mb * 1024 * 1024)
    fillImage(image)
    byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D).copyInto(image, 0x1C)
    for (i in 0 until 8) image[0x2440 + 0x14 + i] = if (i % 4 == 3) 0xFF.toByte() else 0
    for (i in 0 until 4) image[0x420 + i] = if (i % 4 == 2) 0x30 else 0
    for (i in 0 until 18 * 4) {
        image[0x3000 + i] = if (i % 4 == 2) (0x30 + 1 + i / 4).toByte() else 0
        image[0x3000 + 0x90 + i] = if (i % 8 == 3) 0xFF.toByte() else 0
    }
    return image
}

/** `convert_to_2352`: cooked sectors as raw ones with a 16-byte header (sync, time stamp, mode 2) and the data right after it. */
internal fun convertTo2352(cooked: ByteArray, firstSector: Int): ByteArray {
    val sectors = (cooked.size + 2047) / 2048
    val out = ByteArray(sectors * 2352)
    for (s in 0 until sectors) {
        val base = s * 2352
        for (i in 1..10) out[base + i] = -1
        val frames = firstSector + s + 150
        fun bcd(n: Int) = (((n / 10) shl 4) or (n % 10)).toByte()
        out[base + 12] = bcd(frames / 75 / 60)
        out[base + 13] = bcd(frames / 75 % 60)
        out[base + 14] = bcd(frames % 75)
        out[base + 15] = 2
        cooked.copyInto(out, base + 16, s * 2048, minOf(cooked.size, s * 2048 + 2048))
    }
    return out
}

internal fun ByteArray.withPatch(at: Int, vararg values: Int): ByteArray = also { values.forEachIndexed { i, v -> this[at + i] = v.toByte() } }

internal fun ByteArray.withText(at: Int, text: String): ByteArray = also { put(at, text) }
