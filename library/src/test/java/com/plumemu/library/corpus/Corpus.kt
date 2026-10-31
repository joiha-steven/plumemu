package com.plumemu.library.corpus

import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * One identification corpus (a TSV of files with the answer a person expects
 * to see in the game list).
 */
internal data class Corpus(val name: String, val rows: List<CorpusRow>) {
    /** Expected games in the order their first file appears, each with its files in corpus order. */
    val games: Map<String, List<CorpusRow>> by lazy { rows.filter { it.game != null }.groupBy { it.game!! } }
}

/** What a file's bytes are made of; [Empty] when the corpus says `-`. */
internal sealed interface Content {
    object Empty : Content

    /** A zip whose central directory lists one entry [entry] with [crc32] and [size]. */
    data class Zip(val entry: String, val crc32: Long, val size: Long) : Content
}

internal data class CorpusRow(
    val line: Int,
    val path: String,
    val size: Long,
    /** Acceptable system ids; null when the file is not a game. */
    val systems: Set<String>?,
    /** The `system` column as written (`arcade|mame`), for breakdowns. */
    val systemColumn: String,
    val game: String?,
    /** Acceptable titles, trimmed; empty when the file is not a game. */
    val titles: List<String>,
    val tags: List<String>,
    val content: Content,
)

/** A corpus line that does not follow the format; the message starts with `file:line`. */
internal class CorpusFormatException(message: String) : IllegalArgumentException(message)

internal object CorpusReader {
    const val HEADER = "path\tsize\tsystem\tgame\ttitle\ttags\tcontent"

    /** [knownSystems]: the system ids of platforms.json; any other id in the `system` column is an error. */
    fun read(file: File, knownSystems: Set<String>): Corpus =
        parse(file.nameWithoutExtension, file.readText(Charsets.UTF_8), knownSystems, file.path)

    fun parse(name: String, text: String, knownSystems: Set<String>, source: String = name): Corpus {
        val rows = ArrayList<CorpusRow>()
        var headerSeen = false
        val seenPaths = HashMap<String, Int>()
        val spellings = HashMap<String, Pair<String, Int>>()
        val titleOfGame = HashMap<String, Pair<String, Int>>()
        text.split('\n').forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            fun fail(message: String): Nothing = throw CorpusFormatException("$source:$lineNumber: $message")
            if (!headerSeen) {
                if (line != HEADER) fail("header must be exactly '${HEADER.replace("\t", "<TAB>")}'")
                headerSeen = true
                return@forEachIndexed
            }
            val fields = line.split('\t')
            if (fields.size != 7) fail("expected 7 tab-separated fields, found ${fields.size}")
            val row = try {
                row(lineNumber, fields, knownSystems)
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: e.javaClass.simpleName)
            }
            seenPaths.put(row.path, lineNumber)?.let { fail("path '${row.path}' already on line $it") }
            caseClash(row.path, lineNumber, spellings)?.let { fail(it) }
            if (row.game != null) {
                val title = fields[4]
                val first = titleOfGame.getOrPut(row.game) { title to lineNumber }
                if (first.first != title) fail("game '${row.game}' has title '${first.first}' on line ${first.second}")
            }
            rows += row
        }
        if (!headerSeen) throw CorpusFormatException("$source: no header line")
        return Corpus(name, rows)
    }

    /**
     * macOS (where the harness runs) and the FAT/exFAT cards of handhelds ignore case: "SNES/a.sfc"
     * and "snes/b.sfc" would share one folder and the files of one spelling would look missing.
     * Checks [path] and every folder above it against [spellings] (case-folded key to the first
     * spelling and its line), which it updates; the message says what clashes, or null.
     */
    private fun caseClash(path: String, line: Int, spellings: HashMap<String, Pair<String, Int>>): String? {
        val parts = Normalizer.normalize(path, Normalizer.Form.NFC).split('/')
        for (end in 1..parts.size) {
            val spelling = parts.subList(0, end).joinToString("/")
            val first = spellings.getOrPut(spelling.lowercase(Locale.ROOT)) { spelling to line }
            if (first.first != spelling) {
                return "'$spelling' is the same ${if (end == parts.size) "file" else "folder"} as '${first.first}' " +
                    "(line ${first.second}) on a case-blind disk"
            }
        }
        return null
    }

    private fun row(line: Int, fields: List<String>, knownSystems: Set<String>): CorpusRow {
        val (pathField, sizeField, system, game, title, tags, content) = Fields(fields)
        val path = unescape(pathField)
        require(path.isNotEmpty() && !path.startsWith("/") && path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "path '$pathField' must be relative, '/' separated, without empty, '.' or '..' segments"
        }
        val size = sizeField.toLongOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("size '$sizeField' is not a number")
        val notGame = system == "-"
        require(notGame == (game == "-")) { "system and game must both be '-' or both be set (system '$system', game '$game')" }
        require(!notGame || title == "-") { "a file that is not a game has title '-'" }
        val systems = if (notGame) null else system.split('|').map { it.trim() }.toSet()
        require(systems == null || systems.none { it.isEmpty() }) { "empty system id in '$system'" }
        systems?.firstOrNull { it !in knownSystems }?.let { throw IllegalArgumentException("system id '$it' is not in platforms.json") }
        val titles = if (notGame) emptyList() else unescape(title).split(" || ").map { it.trim() }
        require(titles.none { it.isEmpty() }) { "empty title alternative in '$title'" }
        return CorpusRow(
            line = line,
            path = path,
            size = size,
            systems = systems,
            systemColumn = system,
            game = if (notGame) null else game,
            titles = titles,
            tags = if (tags == "-") emptyList() else tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            content = content(content),
        )
    }

    private fun content(field: String): Content {
        if (field == "-") return Content.Empty
        if (field.startsWith("zip:")) {
            val rest = field.removePrefix("zip:")
            val size = rest.substringAfterLast(':')
            val crc = rest.substringBeforeLast(':').substringAfterLast(':')
            val entry = unescape(rest.substringBeforeLast(':').substringBeforeLast(':', missingDelimiterValue = ""))
            require(entry.isNotEmpty() && Regex("[0-9a-fA-F]{8}").matches(crc) && size.toLongOrNull() != null) {
                "content '$field' must be zip:<entry>:<crc32 8 hex>:<size>"
            }
            return Content.Zip(entry, crc.toLong(16), size.toLong())
        }
        throw IllegalArgumentException("unknown content recipe '$field'")
    }

    /** Decodes `\uXXXX` escapes (surrogate pairs as two escapes); any other backslash is an error. */
    fun unescape(text: String): String {
        if ('\\' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            val hex = text.substring(minOf(i + 2, text.length), minOf(i + 6, text.length))
            require(text.startsWith("\\u", i) && hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "bad escape at column ${i + 1} of '$text' (only \\uXXXX is allowed)"
            }
            out.append(hex.toInt(16).toChar())
            i += 6
        }
        return out.toString()
    }

    private class Fields(private val f: List<String>) {
        operator fun component1() = f[0]
        operator fun component2() = f[1]
        operator fun component3() = f[2]
        operator fun component4() = f[3]
        operator fun component5() = f[4]
        operator fun component6() = f[5]
        operator fun component7() = f[6]
    }
}
