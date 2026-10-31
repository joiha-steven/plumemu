package com.plumemu.identify.fingerprint.disc

import java.util.Locale

/**
 * The files a `.cue` sheet, `.gdi` track list or `.m3u` playlist names, as written (relative to
 * the sheet, may carry folders). The scan uses them to tell which files are parts of a game that
 * the sheet stands for, whatever the files are called (`Game.cue` naming `Track 01.bin`).
 */
object SheetReferences {
    /** Sheets longer than this are not read (a cue of 99 tracks is about 6 KB). */
    const val MAX_BYTES = TrackLists.MAX_LIST_BYTES

    private val extensions = setOf("cue", "gdi", "m3u")
    private val cueFileTypes = setOf("BINARY", "MOTOROLA", "AIFF", "WAVE", "MP3")

    fun isSheet(extension: String): Boolean = extension.lowercase(Locale.ROOT) in extensions

    /** Names listed by a sheet with [extension] and the text [text]; empty for any other extension. */
    fun names(extension: String, text: String): List<String> {
        val lines = text.removePrefix("\uFEFF").lineSequence()
        return when (extension.lowercase(Locale.ROOT)) {
            "cue" -> lines.mapNotNull(::cueFile).toList()
            "gdi" -> TrackLists.gdiFileNames(text)
            "m3u" -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            else -> emptyList()
        }.distinct()
    }

    /** `FILE "name" BINARY`, or an unquoted `FILE name with spaces.bin BINARY`. */
    private fun cueFile(line: String): String? {
        val text = line.trim()
        if (!text.regionMatches(0, "FILE", 0, 4, ignoreCase = true) || text.length < 5 || !text[4].isWhitespace()) return null
        val rest = text.substring(5).trim()
        if (rest.startsWith('"')) {
            val end = rest.indexOf('"', 1).let { if (it < 0) rest.length else it }
            return rest.substring(1, end).ifBlank { null }
        }
        val lastSpace = rest.lastIndexOf(' ')
        val withoutType = if (lastSpace > 0 && rest.substring(lastSpace + 1).uppercase(Locale.ROOT) in cueFileTypes) rest.substring(0, lastSpace) else rest
        return withoutType.trim().ifBlank { null }
    }
}
