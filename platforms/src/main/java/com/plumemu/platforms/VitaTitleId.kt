package com.plumemu.platforms

/**
 * A PS Vita title ID such as PCSE00000: what Vita3K starts a game by (`AppStartParameters` =
 * `-r <titleId>`). Plumemu follows ES-DE's convention: each PS Vita game is a small `.psvita` text
 * file whose whole content is the title ID of a game already installed inside the emulator.
 * Only built by [parse], so a value is always nine capital letters or digits.
 */
@JvmInline
value class VitaTitleId private constructor(val value: String) {
    companion object {
        /**
         * The most a `.psvita` file may hold: nine characters in UTF-16 plus room for a byte order
         * mark, spaces and line ends. The launcher reads one byte more and never past it, so a large
         * file is not read whole.
         */
        const val MAX_FILE_BYTES = 64

        // Games are four letters and five digits (PCSE00000); homebrew uses any nine capitals or
        // digits (VITASHELL). Lowercase is refused rather than guessed: the emulator's folders are capitals.
        private val FORMAT = Regex("^[A-Z0-9]{9}$")

        /** The title ID in a `.psvita` file's bytes, or null when they hold anything else. */
        fun parse(fileBytes: ByteArray): VitaTitleId? {
            val text = LaunchFileText.decode(fileBytes).trim()
            return if (FORMAT.matches(text)) VitaTitleId(text) else null
        }

        /** True when a `.psvita` file holds nothing but a byte order mark, NULs, spaces and line ends. */
        fun isEmptyFile(fileBytes: ByteArray): Boolean = LaunchFileText.decode(fileBytes).isBlank()
    }
}
