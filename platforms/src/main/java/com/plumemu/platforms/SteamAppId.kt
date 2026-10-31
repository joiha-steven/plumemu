package com.plumemu.platforms

/**
 * A Steam app id such as 274190 (Broforce): what GameNative, GameHub Lite and DroidDeck start a game
 * by. Plumemu follows ES-DE's convention: each Steam game is a small `.steam` text file whose whole
 * content is the app id of a game already in the Steam app (GameNative's "Export for frontend"
 * writes one). Only built by [parse], so [value] is always a positive Int: GameNative reads `app_id`
 * as an Int extra.
 */
@JvmInline
value class SteamAppId private constructor(val value: Int) {
    /** The id as decimal digits, for a String extra or a URI ([value] without leading zeros). */
    val text: String get() = value.toString()

    companion object {
        /**
         * The most a `.steam` file may hold. An id is at most ten digits; the rest is room for a byte
         * order mark, UTF-16, spaces and line ends. The launcher reads one byte more and never past it,
         * so a large file (a game image renamed by mistake) is not read whole.
         */
        const val MAX_FILE_BYTES = 1024

        // Ten digits is Int.MAX_VALUE's length; the range is checked after.
        private val DIGITS = Regex("^[0-9]{1,10}$")

        /** The app id in a `.steam` file's bytes, or null when they hold anything else (or 0, or more than an Int). */
        fun parse(fileBytes: ByteArray): SteamAppId? {
            val text = LaunchFileText.decode(fileBytes).trim()
            if (!DIGITS.matches(text)) return null
            val number = text.toLong()
            return if (number in 1..Int.MAX_VALUE) SteamAppId(number.toInt()) else null
        }

        /** True when a `.steam` file holds nothing but a byte order mark, NULs, spaces and line ends. */
        fun isEmptyFile(fileBytes: ByteArray): Boolean = LaunchFileText.decode(fileBytes).isBlank()
    }
}
