package com.plumemu.library.naming

import java.util.Locale

/**
 * What the usage statistics may say about a game the identifier could not name: how it is stored,
 * its size, CRC, code and disc track-size key. Built from the stored `game.fingerprint` only, and
 * has no field for a file name, the name of a file inside an archive, a folder or a title, so none
 * can be sent.
 *
 * @property container how the file is stored, lower case (`zip`, `cue`, `chd`).
 * @property size the bytes [crc] covers when there is one, else the file's length.
 * @property crc CRC-32 as name databases list it (header left out), with [size] its length.
 * @property codeKind which numbering [code] is, lower case (`ps1`); null when there is no code.
 * @property code the cartridge product code or disc serial read from the file's content, never a
 *   dummy one and never one taken from the file name.
 * @property trackKey the disc's strongest track-size key.
 */
data class UnidentifiedFingerprint(
    val container: String,
    val size: Long,
    val crc: Long?,
    val codeKind: String?,
    val code: String?,
    val trackKey: Long?,
) {
    companion object {
        /**
         * The fingerprint in [stored] (`game.fingerprint`), or null when it cannot be read or holds
         * nothing that tells a game apart (no CRC, code or track key: a size alone names nothing).
         */
        fun of(stored: String?): UnidentifiedFingerprint? {
            val read = FingerprintCodec.decode(stored) ?: return null
            // The CRC with the length it covers, as the strongest fix key has it; an empty file has none.
            val crcLength = read.contentSize?.takeIf { read.crc32 != null && it > 0 }
            val crc = if (crcLength != null) read.crc32 else null
            // A code only when it is a fix key (dummy codes such as "0000" are left out there) and was read
            // from the file's content: a Switch id from the file name, or a value stored before the origin
            // was (decoded as from the name), is something a person typed and is never sent.
            val codeKey = if (read.codeFromFileName) null else read.fixKeys().firstOrNull { it.startsWith(CODE_PREFIX) }
            val code = codeKey?.let { read.productCode }
            val codeKind = codeKey?.let { read.codeKind?.name?.lowercase(Locale.ROOT) }
            val trackKey = read.trackKeys?.firstOrNull()
            if (crc == null && code == null && trackKey == null) return null
            return UnidentifiedFingerprint(
                container = read.container.name.lowercase(Locale.ROOT),
                size = crcLength ?: read.size,
                crc = crc,
                codeKind = codeKind,
                code = code,
                trackKey = trackKey,
            )
        }

        private const val CODE_PREFIX = "code:"
    }
}
