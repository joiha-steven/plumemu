package com.plumemu.scraper.hash

import com.plumemu.scraper.HashCache
import com.plumemu.scraper.RomHashes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * [HashCache] in one text file: a line per ROM, `key TAB size TAB modified TAB crc TAB md5 TAB sha1`
 * (an empty field is a hash not computed). Loaded once, then each new entry is appended; the
 * last line for a key wins. On load, a file with damaged or superseded lines is rewritten whole.
 *
 * A cache that cannot be read or written only costs a rehash: errors are logged, never thrown.
 */
internal class FileHashCache(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val log: (String) -> Unit,
) : HashCache {
    private class Entry(val sizeBytes: Long, val lastModifiedMillis: Long, val hashes: RomHashes)

    private val mutex = Mutex()
    private var entries: MutableMap<String, Entry>? = null

    override suspend fun get(key: String, sizeBytes: Long, lastModifiedMillis: Long): RomHashes? =
        mutex.withLock {
            val entry = loaded()[key] ?: return@withLock null
            entry.hashes.takeIf {
                entry.sizeBytes == sizeBytes && entry.lastModifiedMillis == lastModifiedMillis
            }
        }

    override suspend fun put(key: String, sizeBytes: Long, lastModifiedMillis: Long, hashes: RomHashes) {
        mutex.withLock {
            loaded()[key] = Entry(sizeBytes, lastModifiedMillis, hashes)
            val line = encode(key, sizeBytes, lastModifiedMillis, hashes)
            withContext(ioDispatcher) {
                try {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
                } catch (e: IOException) {
                    log("hash cache: could not append to ${file.name}: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun loaded(): MutableMap<String, Entry> {
        entries?.let { return it }
        val map = withContext(ioDispatcher) { load() }
        entries = map
        return map
    }

    private fun load(): MutableMap<String, Entry> {
        val map = LinkedHashMap<String, Entry>()
        if (!file.exists()) return map
        var lines = 0
        var damaged = 0
        try {
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isEmpty()) return@forEachLine
                lines++
                val parsed = decode(line)
                if (parsed == null) damaged++ else map[parsed.first] = parsed.second
            }
        } catch (e: IOException) {
            log("hash cache: could not read ${file.name}: ${e.javaClass.simpleName}")
            return map
        }
        // A damaged line is most likely a write cut short; rewriting also ends the file with a
        // newline so the next append starts a fresh line.
        val superseded = lines > map.size * 2 + COMPACT_SLACK
        if (damaged > 0 || superseded || !endsWithNewline()) rewrite(map)
        return map
    }

    private fun endsWithNewline(): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            raf.length() == 0L || raf.run { seek(length() - 1); read() } == '\n'.code
        }
    } catch (e: IOException) {
        false
    }

    private fun rewrite(map: Map<String, Entry>) {
        try {
            val tmp = File.createTempFile(file.name, ".tmp", file.parentFile)
            tmp.bufferedWriter(Charsets.UTF_8).use { out ->
                map.forEach { (key, e) -> out.write(encode(key, e.sizeBytes, e.lastModifiedMillis, e.hashes)) }
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                log("hash cache: could not replace ${file.name}")
            }
        } catch (e: IOException) {
            log("hash cache: could not rewrite ${file.name}: ${e.javaClass.simpleName}")
        }
    }

    private fun encode(key: String, size: Long, modified: Long, h: RomHashes): String =
        listOf(escape(key), size.toString(), modified.toString(), h.crc32.orEmpty(), h.md5.orEmpty(), h.sha1.orEmpty())
            .joinToString("\t", postfix = "\n")

    private fun decode(line: String): Pair<String, Entry>? {
        val fields = line.split('\t')
        if (fields.size != FIELD_COUNT) return null
        val key = unescape(fields[0]) ?: return null
        val size = fields[1].toLongOrNull() ?: return null
        val modified = fields[2].toLongOrNull() ?: return null
        val crc = hashField(fields[3], CRC_HEX) ?: return null
        val md5 = hashField(fields[4], MD5_HEX) ?: return null
        val sha1 = hashField(fields[5], SHA1_HEX) ?: return null
        return key to Entry(size, modified, RomHashes(crc.value, md5.value, sha1.value))
    }

    /** A line cut short by a crash must not yield a truncated hash: each field is checked whole. */
    private class Field(val value: String?)

    private fun hashField(text: String, shape: Regex): Field? = when {
        text.isEmpty() -> Field(null)
        shape.matches(text) -> Field(text)
        else -> null
    }

    companion object {
        const val FILE_NAME = "rom-hashes.v1.tsv"
        private const val FIELD_COUNT = 6
        private const val COMPACT_SLACK = 100
        private val CRC_HEX = Regex("[0-9A-F]{8}")
        private val MD5_HEX = Regex("[0-9a-f]{32}")
        private val SHA1_HEX = Regex("[0-9a-f]{40}")

        /** Keys are paths or URIs; a tab, newline or backslash in one must not break the line. */
        fun escape(key: String): String =
            key.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

        fun unescape(text: String): String? {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c != '\\') {
                    out.append(c)
                    i++
                    continue
                }
                if (i + 1 >= text.length) return null
                when (text[i + 1]) {
                    '\\' -> out.append('\\')
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> return null
                }
                i += 2
            }
            return out.toString()
        }
    }
}
