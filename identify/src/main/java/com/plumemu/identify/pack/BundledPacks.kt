package com.plumemu.identify.pack

import com.plumemu.identify.IdentifyLog
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * The packs shipped inside the app: an index text (`identify/packs.txt` in the assets) and one
 * compressed pack per system (`<id>.pack.lzma`; `.gz` still works). The Android implementation is
 * [com.plumemu.identify.android.AssetBundledPacks]; tests pass a fake. All parsing is in
 * [BundledIndex], so the Android class only opens files.
 */
interface BundledPacks {
    /** The index text, or null when the app ships none. */
    @Throws(IOException::class)
    fun readIndex(): String?

    /** Opens the compressed pack [fileName] named by the index. The caller closes it. */
    @Throws(IOException::class)
    fun open(fileName: String): InputStream

    companion object {
        /** No bundled packs at all. */
        val NONE: BundledPacks = object : BundledPacks {
            override fun readIndex(): String? = null
            override fun open(fileName: String): InputStream = throw IOException("no bundled packs")
        }
    }
}

/**
 * One pack listed in the index. [sha256] is the pack's SHA-256 trailer (of the unpacked bytes
 * before the trailer), lower-case hex.
 */
data class BundledPack(val systemId: String, val fileName: String, val dataVersion: Long, val sha256: String)

/**
 * The parsed `identify/packs.txt`: blank lines and lines starting with `#` are ignored;
 * `<systemId> <fileName> <dataVersion> <sha256>` lists a pack; `alias <systemId> <packSystemId>`
 * sends a system to another system's pack (`alias genesis megadrive`). Fields are separated by
 * spaces or tabs; system ids are compared lower-case. A malformed line is skipped and logged.
 */
class BundledIndex(val packs: Map<String, BundledPack>, val aliases: Map<String, String>) {
    companion object {
        val EMPTY = BundledIndex(emptyMap(), emptyMap())
        private val SHA256_HEX = Regex("[0-9a-f]{64}")

        fun parse(text: String, log: IdentifyLog = IdentifyLog.STDERR): BundledIndex {
            val packs = LinkedHashMap<String, BundledPack>()
            val aliases = LinkedHashMap<String, String>()
            text.lineSequence().forEachIndexed { number, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val fields = line.split(' ', '\t').filter { it.isNotEmpty() }
                if (fields[0] == "alias" && fields.size == 3) {
                    aliases[fields[1].lowercase(Locale.ROOT)] = fields[2].lowercase(Locale.ROOT)
                    return@forEachIndexed
                }
                val version = fields.getOrNull(2)?.toLongOrNull()
                val sha = fields.getOrNull(3)?.lowercase(Locale.ROOT)
                if (fields.size != 4 || version == null || version !in 0..PackWriter.UINT_MAX || sha == null || !SHA256_HEX.matches(sha)) {
                    log.log("packs.txt line ${number + 1}: not '<system> <file> <version> <sha256>' or 'alias <system> <system>', skipped")
                    return@forEachIndexed
                }
                val id = fields[0].lowercase(Locale.ROOT)
                packs[id] = BundledPack(id, fields[1], version, sha)
            }
            return BundledIndex(packs, aliases)
        }
    }
}
