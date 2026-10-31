package com.plumemu.identify.pack

import com.plumemu.identify.IdentifyLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import com.plumemu.identify.codec.lzma.ByteArrayInput
import com.plumemu.identify.codec.lzma.LzmaException
import com.plumemu.identify.codec.lzma.lzmaAloneDecode

/**
 * The installed name packs: one file per system, `<directory>/<systemId>.pack`, unpacked from the
 * app's bundled packs on first use or installed by a data update (E4), then read
 * memory-mapped through [NamePack].
 *
 * Installing writes to a temp file in the same directory, checks the pack's structure, its
 * system id and its SHA-256 (the trailer, recomputed, against the expected value), and only then
 * renames it over the old file. A failed or corrupt install leaves the old pack (or nothing) and
 * a log line; it never throws. Each system has its own lock, so two scans asking for the same
 * pack install it once, and different systems install in parallel.
 *
 * Meant as one store per directory. Two stores on one directory do not corrupt each other:
 * each installs through its own temp file, a temp file is removed as stale only when it is older
 * than [STALE_TEMP_MILLIS] (so another store's install in progress is left alone), and the
 * rename is atomic, so the last install wins. They may unpack the same pack twice and each maps
 * its own copy.
 *
 * A pack that opened but fails a lookup (damaged after it was checked) is reported through
 * [reportBroken]: the first time its file is removed so the next [packFor] installs it again;
 * the second time in one session it is given up until [install] succeeds.
 *
 * @param maxPackBytes largest unpacked pack accepted; a larger stream is refused (a broken or
 *   hostile gzip must not fill the storage).
 */
class PackStore(
    private val directory: File,
    private val bundled: BundledPacks,
    private val log: IdentifyLog = IdentifyLog.STDERR,
    private val maxPackBytes: Long = DEFAULT_MAX_PACK_BYTES,
) {
    private val opened = ConcurrentHashMap<String, NamePack>()
    /** Packs that could not be had; a map used as a set because `newKeySet` is API 24. */
    private val unavailable = ConcurrentHashMap<String, Unit>()
    private val locks = ConcurrentHashMap<String, Any>()

    /** Packs already reinstalled once after [reportBroken] in this session. */
    private val reinstalledAfterBreak = ConcurrentHashMap<String, Unit>()

    /** True when the app ships no index at all (a build without name packs): games are named from file names. */
    @Volatile private var noPacksBundled = false

    private val index: BundledIndex by lazy {
        try {
            val text = bundled.readIndex()
            if (text == null) {
                noPacksBundled = true
                log.log("no name packs are bundled in this build; games are named from their file names")
            }
            text?.let { BundledIndex.parse(it, log) } ?: BundledIndex.EMPTY
        } catch (e: IOException) {
            log.log("bundled pack index unreadable: ${e.javaClass.simpleName}")
            BundledIndex.EMPTY
        }
    }

    /** The pack id [systemId] uses: itself, or the target of an alias line, lower-case. */
    fun packIdFor(systemId: String): String {
        val id = systemId.lowercase(Locale.ROOT)
        return index.aliases[id] ?: id
    }

    /**
     * Whether the APK ships a pack for [systemId] (aliases resolved): when [packFor] then answers
     * null, the pack is expected but could not be installed, which is not the same as a system
     * that has no pack at all.
     */
    fun hasBundled(systemId: String): Boolean = index.packs.containsKey(packIdFor(systemId))

    /**
     * The pack for [systemId] (aliases resolved), installing the bundled one first when none is
     * installed or the installed one is older. Null when there is no pack or it could not be
     * installed; that is logged once and remembered until [install] succeeds for the system.
     * Blocking I/O on first use: call it off the main thread.
     */
    fun packFor(systemId: String): NamePack? {
        val id = packIdFor(systemId)
        opened[id]?.let { return it }
        if (unavailable.containsKey(id)) return null
        synchronized(lockFor(id)) {
            opened[id]?.let { return it }
            if (unavailable.containsKey(id)) return null
            val pack = load(id)
            if (pack == null) unavailable[id] = Unit else opened[id] = pack
            return pack
        }
    }

    /**
     * Unpacks the packs of [systemIds] (the systems the person has) so the first scan does not
     * wait on them one by one. Returns the pack ids that are ready.
     */
    fun prepare(systemIds: Collection<String>): Set<String> =
        systemIds.map(::packIdFor).distinct().filter { packFor(it) != null }.toSet()

    /**
     * Installs the gzip-compressed pack in [stream] for [systemId] (a data update). The pack
     * must name the same system, have [expectedSha256] as its trailer, and not be older than the
     * installed one; otherwise the installed pack stays. Does not close [stream].
     */
    fun install(systemId: String, stream: InputStream, expectedSha256: String): InstallResult {
        val id = packIdFor(systemId)
        synchronized(lockFor(id)) {
            val result = installLocked(id, stream, expectedSha256.lowercase(Locale.ROOT), "update")
            if (result is InstallResult.Installed) {
                opened[id] = result.pack
                unavailable.remove(id)
            }
            return result
        }
    }

    /**
     * Tells the store that [pack] failed a lookup with [cause]. Ignored when [pack] is no longer
     * the store's pack for its system (another thread reported it already). The first report
     * removes the installed file so the next [packFor] reinstalls it; a second one in the same
     * session marks the system as without a pack, so a damaged bundle is not unpacked per file.
     */
    fun reportBroken(pack: NamePack, cause: Throwable) {
        val id = pack.systemId
        synchronized(lockFor(id)) {
            if (opened[id] !== pack) return
            opened.remove(id)
            if (reinstalledAfterBreak.putIfAbsent(id, Unit) == null) {
                log.log("pack $id: broken (${cause.javaClass.simpleName}), reinstalling on next use")
                val file = packFile(id)
                if (file.exists() && !file.delete()) log.log("pack $id: broken file not removed")
            } else {
                log.log("pack $id: still broken after reinstall (${cause.javaClass.simpleName}), skipped this session")
                unavailable[id] = Unit
            }
        }
    }

    private fun lockFor(id: String): Any = locks.getOrPut(id) { Any() }

    private fun load(id: String): NamePack? {
        removeStaleTemps(id)
        val installed = openInstalled(id)
        val shipped = index.packs[id]
        if (shipped != null && (installed == null || installed.dataVersion < shipped.dataVersion)) {
            val result = try {
                bundled.open(shipped.fileName).use { installLocked(id, it, shipped.sha256, ORIGIN_BUNDLED, shipped.dataVersion, lzma = shipped.fileName.endsWith(LZMA_SUFFIX)) }
            } catch (e: IOException) {
                InstallResult.Failed("bundled pack unreadable: ${e.javaClass.simpleName}").also { log.log("pack $id: ${it.reason}") }
            }
            if (result is InstallResult.Installed) return result.pack
        }
        if (installed == null && shipped == null && !noPacksBundled) log.log("pack $id: none installed or bundled")
        return installed
    }

    private fun openInstalled(id: String): NamePack? {
        val file = packFile(id)
        if (!file.isFile) return null
        return try {
            when (val result = NamePack.open(file)) {
                is PackOpenResult.Opened -> result.pack.takeIf { it.systemId == id }
                    ?: null.also { log.log("pack $id: installed file names another system, ignored") }
                is PackOpenResult.Rejected -> null.also { log.log("pack $id: installed file rejected: ${result.reason}") }
            }
        } catch (e: IOException) {
            log.log("pack $id: installed file unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Caller holds the lock of [id]. */
    private fun installLocked(
        id: String,
        gzipped: InputStream,
        expectedSha256: String,
        origin: String,
        expectedVersion: Long? = null,
        lzma: Boolean = false,
    ): InstallResult {
        val temp = try {
            directory.mkdirs()
            File.createTempFile(TEMP_PREFIX + id + ".", TEMP_SUFFIX, directory)
        } catch (e: IOException) {
            return fail(id, origin, "cannot create a temp file: ${e.javaClass.simpleName}")
        }
        try {
            // Only the APK's own packs may come un-gzipped (see unpack); a data update must be gzip.
            (if (lzma) unpackLzma(id, gzipped, temp) else unpack(gzipped, temp, plainAllowed = origin == ORIGIN_BUNDLED))?.let { return fail(id, origin, it) }
            val pack = when (val opened = NamePack.open(temp)) {
                is PackOpenResult.Opened -> opened.pack
                is PackOpenResult.Rejected -> return fail(id, origin, "rejected: ${opened.reason}")
            }
            if (pack.systemId != id) return fail(id, origin, "pack is for system ${pack.systemId}")
            if (pack.checksum != expectedSha256) return fail(id, origin, "SHA-256 differs from the expected one")
            if (!pack.verifyChecksum()) return fail(id, origin, "SHA-256 does not match the content")
            if (expectedVersion != null && pack.dataVersion != expectedVersion) {
                return fail(id, origin, "version ${pack.dataVersion} differs from the index's $expectedVersion")
            }
            val current = opened[id] ?: openInstalled(id)
            if (current != null && current.dataVersion > pack.dataVersion) {
                return fail(id, origin, "version ${pack.dataVersion} is older than installed ${current.dataVersion}")
            }
            if (!temp.renameTo(packFile(id))) return fail(id, origin, "rename into place failed")
            log.log("pack $id: installed version ${pack.dataVersion} ($origin)")
            return InstallResult.Installed(pack)
        } catch (e: IOException) {
            return fail(id, origin, "I/O error: ${e.javaClass.simpleName}")
        } finally {
            if (temp.exists() && !temp.delete()) log.log("pack $id: temp file left behind")
        }
    }

    /**
     * Unpacks [gzipped] into [target]; returns why it failed, or null. With [plainAllowed], a stream
     * that does not start with the gzip magic is taken as the pack itself: the Android Gradle plugin un-gzips `.gz`
     * assets when it merges them, so the APK holds `identify/<id>.pack` (stored deflated in the
     * APK instead), and [com.plumemu.identify.android.AssetBundledPacks] opens that. Either way
     * the size cap and the SHA-256 checks that follow apply to the same pack bytes.
     */
    private fun unpack(gzipped: InputStream, target: File, plainAllowed: Boolean): String? {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        try {
            FileOutputStream(target).use { out ->
                packBytesOf(BufferedInputStream(NonClosingStream(gzipped), COPY_BUFFER), plainAllowed).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxPackBytes) return "unpacked pack is larger than $maxPackBytes bytes"
                        out.write(buffer, 0, n)
                    }
                }
                out.fd.sync()
            }
        } catch (e: IOException) {
            return "unpack failed: ${e.javaClass.simpleName}"
        }
        return null
    }

    /**
     * Unpacks the `.lzma` (LZMA_Alone) file in [stream], a bundled pack, into [target]; returns why
     * it failed, or null. The whole file is read and decoded in memory (the largest pack is a few MB),
     * bounded by [maxPackBytes] on both sides; a corrupt or truncated file is logged with the
     * pack's id and the decoder's message, and the caller falls back as for a missing pack.
     */
    private fun unpackLzma(id: String, stream: InputStream, target: File): String? {
        try {
            val compressed = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (compressed.size() + n > maxPackBytes) return "compressed pack is larger than $maxPackBytes bytes"
                compressed.write(buffer, 0, n)
            }
            val packed = compressed.toByteArray()
            // Tighter than maxPackBytes: the output buffer doubles, so a stream that never ends would
            // otherwise take up to twice maxPackBytes of memory (OutOfMemoryError on a 2 GB phone).
            val cap = minOf(maxPackBytes, BUNDLED_LZMA_MAX_BYTES).toInt()
            val plain = try {
                lzmaAloneDecode(ByteArrayInput(packed), cap)
            } catch (e: LzmaException) {
                log.log("pack $id: corrupt LZMA data: ${e.message}")
                return "corrupt LZMA data"
            }
            if (plain.size >= cap) {
                log.log("pack $id: LZMA data does not end within $cap bytes")
                return "unpacked pack is larger than $cap bytes"
            }
            FileOutputStream(target).use { out ->
                out.write(plain)
                out.fd.sync()
            }
        } catch (e: IOException) {
            return "unpack failed: ${e.javaClass.simpleName}"
        }
        return null
    }

    private fun packBytesOf(input: BufferedInputStream, plainAllowed: Boolean): InputStream {
        if (!plainAllowed) return GZIPInputStream(input)
        input.mark(2)
        val isGzip = input.read() == GZIP_MAGIC_FIRST && input.read() == GZIP_MAGIC_SECOND
        input.reset()
        return if (isGzip) GZIPInputStream(input) else input
    }

    private fun fail(id: String, origin: String, reason: String): InstallResult.Failed {
        log.log("pack $id: $origin install failed: $reason")
        return InstallResult.Failed(reason)
    }

    private fun packFile(id: String): File = File(directory, "$id$PACK_SUFFIX")

    /** Removes temp files of [id] left by an install that died; younger ones may be in progress. */
    private fun removeStaleTemps(id: String) {
        val cutoff = System.currentTimeMillis() - STALE_TEMP_MILLIS
        directory.listFiles { file ->
            file.name.startsWith("$TEMP_PREFIX$id.") && file.name.endsWith(TEMP_SUFFIX) && file.lastModified() < cutoff
        }
            ?.forEach { if (!it.delete()) log.log("pack $id: stale temp file not removed") }
    }

    /** Lets [GZIPInputStream] be dropped without closing the caller's stream. */
    private class NonClosingStream(input: InputStream) : java.io.FilterInputStream(input) {
        override fun close() = Unit
    }

    companion object {
        /** 64 MB: about four times the largest pack expected. */
        const val DEFAULT_MAX_PACK_BYTES: Long = 64L * 1024 * 1024
        const val PACK_SUFFIX = ".pack"
        /** Most a bundled `.lzma` pack may unpack to: 16 MB, six times the largest (switch, 2.6 MB). */
        internal const val BUNDLED_LZMA_MAX_BYTES: Long = 16L * 1024 * 1024
        /** A temp file older than this is from an install that died, not one in progress. */
        const val STALE_TEMP_MILLIS: Long = 10L * 60 * 1000
        /** Temp files are `install.<id>.<random>.tmp`: no system id holds a dot, so `pico` never matches `pico-8`. */
        internal const val TEMP_PREFIX = "install."
        internal const val TEMP_SUFFIX = ".tmp"
        private const val COPY_BUFFER = 64 * 1024
        private const val LZMA_SUFFIX = ".lzma"
        private const val ORIGIN_BUNDLED = "bundled"
        private const val GZIP_MAGIC_FIRST = 0x1f
        private const val GZIP_MAGIC_SECOND = 0x8b
    }
}

/** What [PackStore.install] did. */
sealed class InstallResult {
    data class Installed(val pack: NamePack) : InstallResult()

    /** Nothing changed; [reason] is plain English (no paths). */
    data class Failed(val reason: String) : InstallResult()
}
