package com.plumemu.identify.fingerprint.nx

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * Switch title ids (nothing is decrypted, no key is used or shipped).
 *
 * A Switch title id is 16 hex digits. A game (application) id has its low 13 bits clear; its
 * update is the id + 0x800 and its DLC the id + 0x1000 + n (n from 1). All three are named after
 * the game, so the lookup uses the base id ([baseOf]).
 *
 * Where the id comes from, best first:
 *
 * 1. **NSP and NSZ** (a PFS0 partition, switchbrew "PFS0"): `"PFS0"` at 0, u32 file count at 4,
 *    u32 string table size at 8, u32 reserved at 12, then one 0x18-byte entry per file (u64 data
 *    offset, u64 size, u32 name offset into the string table, u32 reserved), then the string
 *    table of NUL-terminated names. Only the header and the names are read (at most [MAX_HEADER]
 *    bytes; a real NSP's is under 1 KB). A ticket `<rights id>.tik` or its certificate
 *    `<rights id>.cert` names the rights id: the content's title id, 7 zero bytes and the key
 *    generation, so its first 16 digits are the title id of the content it unlocks. A content
 *    meta file `<title id>.cnmt.xml` named by a 16-digit id gives it too (most are named by the
 *    meta NCA's 32-digit content id instead, which is not a title id and is ignored). The NCA
 *    files themselves are encrypted and never opened. An NSP without a ticket (standard crypto)
 *    or one whose names give nothing falls back to the file name.
 * 2. **XCI and XCZ** (game card image): the card header at 0x100 (`"HEAD"`) has no title id in
 *    clear, and the partitions list NCA files by content id only; the title id is inside the
 *    encrypted NCA headers. So the file name is all there is: the file is not read.
 * 3. **The file name**: the first `01` + 14 hex digits, any case, in brackets or glued to the
 *    title ("Game[0100ABCD00010000][v0]"), not inside a longer run of hex digits: a hex digit or
 *    letter right before or after it rejects it (a 32-digit NCA name, "deadbeef0100...").
 *
 * An id read from the content wins over the file name; a note says when they disagree. With
 * several titles in one NSP (a game with its update and DLC merged), the game's id is taken.
 */
internal object SwitchReader {
    /** Plumemu's Switch system id and the folder names that mean it (`platforms.json`). */
    private val SYSTEM_IDS = setOf("switch", "nintendoswitch", "nsw")

    /** Extensions read as Switch files when the system is not known. */
    private val EXTENSIONS = setOf("nsp", "nsz", "xci", "xcz")

    private val PFS0 = ascii("PFS0")
    private val XCI_HEAD = ascii("HEAD")
    private const val XCI_HEAD_AT = 0x100L
    private const val PFS0_HEADER = 0x10
    private const val PFS0_ENTRY = 0x18

    /** Bound on the PFS0 header and string table read: a real NSP's is a few hundred bytes. */
    const val MAX_HEADER = 64 * 1024

    private const val BASE_MASK = 0x1FFFL
    private const val UPDATE_LOW = 0x800L
    private const val DLC_LOW = 0x1000L

    private val ID_IN_NAME = Regex("(?<![0-9A-Fa-f])01[0-9A-Fa-f]{14}(?![0-9A-Fa-f])")
    private val RIGHTS_FILE = Regex("(01[0-9A-Fa-f]{14})0{14}[0-9A-Fa-f]{2}\\.(?:tik|cert)", RegexOption.IGNORE_CASE)
    private val META_FILE = Regex("(01[0-9A-Fa-f]{14})\\.cnmt\\.xml", RegexOption.IGNORE_CASE)

    /** True when a file of [systemId] (or, unknown, with [extension]) is a Switch file. */
    fun claims(systemId: String?, extension: String): Boolean {
        val system = systemId?.trim()?.lowercase(Locale.ROOT)
        return if (system == null) extension.lowercase(Locale.ROOT) in EXTENSIONS else system in SYSTEM_IDS
    }

    /** What [read] found: the container, the content's own id and how it was found. */
    data class SwitchIds(
        val container: Container,
        val titleId: String?,
        val notes: List<String>,
    ) {
        /** The base application id for the pack lookup, or null. */
        val baseId: String? get() = titleId?.let(::baseOf)
        val kind: TitleKind? get() = titleId?.let(::kindOf)
        val codeKind: CodeKind? get() = if (titleId != null) CodeKind.SWITCH else null
    }

    /**
     * The title id of [source], named [fileName]: from the NSP's file table when it has one,
     * else from the name. Bad bytes give a note, never an exception.
     *
     * @throws IOException when [source] fails to read.
     */
    @Throws(IOException::class)
    fun read(source: RandomAccessSource, fileName: String, extension: String): SwitchIds {
        val notes = ArrayList<String>(2)
        val fromName = fromFileName(fileName)
        val start = source.readFully(0, PFS0_HEADER)
        val container: Container
        var fromContent: String? = null
        when {
            start.startsWith(PFS0) -> {
                container = Container.PFS0
                fromContent = fromPfs0(source, start, notes)
            }
            extension.lowercase(Locale.ROOT) in setOf("xci", "xcz") ||
                source.readFully(XCI_HEAD_AT, 4).startsWith(XCI_HEAD) -> {
                container = Container.XCI
                notes += "xci: the card header carries no title id in clear; the file name is used"
            }
            else -> container = Container.NONE
        }
        if (fromContent != null && fromName != null && fromContent != fromName) {
            notes += "switch: file name says $fromName, the content says $fromContent; the content's id is used"
        }
        return SwitchIds(container, fromContent ?: fromName, notes)
    }

    /** The first Switch title id in [fileName] (upper case), or null. */
    fun fromFileName(fileName: String): String? {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        return ID_IN_NAME.find(base)?.value?.uppercase(Locale.ROOT)
    }

    /** [titleId] with its low 13 bits cleared: the game an update or DLC belongs to. */
    fun baseOf(titleId: String): String = format(parse(titleId) and BASE_MASK.inv())

    /** What [titleId] is, by its low 13 bits. */
    fun kindOf(titleId: String): TitleKind {
        val low = parse(titleId) and BASE_MASK
        return when {
            low == 0L -> TitleKind.BASE
            low == UPDATE_LOW -> TitleKind.UPDATE
            low > DLC_LOW -> TitleKind.DLC
            else -> TitleKind.OTHER
        }
    }

    private fun parse(titleId: String): Long = java.lang.Long.parseUnsignedLong(titleId, 16)

    private fun format(id: Long): String = String.format(Locale.ROOT, "%016X", id)

    /** The title id the PFS0 file names give, or null with a note saying why. */
    @Throws(IOException::class)
    private fun fromPfs0(source: RandomAccessSource, start: ByteArray, notes: MutableList<String>): String? {
        if (start.size < PFS0_HEADER) {
            notes += "nsp: PFS0 header cut short"
            return null
        }
        val count = start.u32le(4)
        val tableSize = start.u32le(8)
        val headerSize = PFS0_HEADER + count * PFS0_ENTRY + tableSize
        if (count == 0L || headerSize > MAX_HEADER || headerSize > source.size) {
            notes += "nsp: PFS0 lists $count files and a $tableSize-byte name table; not read"
            return null
        }
        val header = source.readFully(0, headerSize.toInt())
        val tableAt = PFS0_HEADER + (count * PFS0_ENTRY).toInt()
        val ids = LinkedHashSet<String>()
        for (i in 0 until count.toInt()) {
            val nameAt = header.u32le(PFS0_HEADER + i * PFS0_ENTRY + 0x10)
            if (nameAt >= tableSize) continue
            val name = nulTerminated(header, tableAt + nameAt.toInt(), headerSize.toInt()) ?: continue
            (RIGHTS_FILE.matchEntire(name) ?: META_FILE.matchEntire(name))?.let { ids += it.groupValues[1].uppercase(Locale.ROOT) }
        }
        if (ids.isEmpty()) {
            notes += "nsp: no ticket or content meta name gives a title id"
            return null
        }
        if (ids.size > 1) notes += "nsp: ${ids.size} titles inside (${ids.joinToString(", ")})"
        return ids.minWithOrNull(compareBy<String>({ kindOf(it).ordinal }, { it }))
    }

    /** ASCII text from [at] to the first NUL before [end]; null when it is not printable ASCII. */
    private fun nulTerminated(bytes: ByteArray, at: Int, end: Int): String? {
        val out = StringBuilder()
        var i = at
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) break
            if (b < 0x20 || b > 0x7E) return null
            out.append(b.toChar())
            i++
        }
        return out.toString().ifEmpty { null }
    }
}
