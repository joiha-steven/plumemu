package com.plumemu.identify

import com.plumemu.identify.arcade.ArcadeLookup
import com.plumemu.identify.arcade.ArcadeName
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.extensionOf
import com.plumemu.identify.fingerprint.nx.SwitchReader
import com.plumemu.identify.name.HackNames
import com.plumemu.identify.name.NameCandidates
import com.plumemu.identify.name.NameMatcher
import com.plumemu.identify.name.TitleCase
import com.plumemu.identify.name.TitleCleaner
import com.plumemu.identify.pack.CorruptPackException
import com.plumemu.identify.pack.NamePack
import com.plumemu.identify.pack.PackKind
import com.plumemu.identify.pack.PackMasks
import com.plumemu.identify.pack.PackRelease
import com.plumemu.identify.pack.PackStore
import java.io.IOException

/**
 * Names a game file (entirely on the device), cheapest
 * layer first:
 *
 * 1. Arcade systems ([ArcadeSystems]): the file's short name in the arcade pack (MAME 2003-Plus
 *    names too, through the pack's alias records). Nothing is read. The title is the set's short
 *    title ([com.plumemu.identify.arcade.ArcadeTitles]), the regions those its description names
 *    ([com.plumemu.identify.arcade.ArcadeTitles.regions], as No-Intro names), the description is
 *    [Identification.fullTitle].
 * 2. Fingerprints ([FingerprintReader], or [IdentifyRequest.known] when the scan cached them).
 * 3. The system's pack: CRC (the content CRC, then a zip's directory CRC when they differ), then
 *    product code (EXACT). A code counts only when it is not a
 *    dummy (it gives a `code:` fix key). When the releases found belong to two or more titles,
 *    the layer gives no answer and the next one is tried. When they are releases of one title,
 *    those whose regions meet [IdentifyRequest.regionsFromName] are kept (all of them when none
 *    do); the result has their regions together, and their variant only when they share one.
 * 4. The header title, when the fingerprints say it is the full official title (EXACT).
 * 5. A name match of [IdentifyRequest.cleanedTitle] and its other cleanings ([NameCandidates]:
 *    words split at case changes, a DS / GBA release number dropped) against the pack's titles,
 *    the spelling closest to the file's among titles with the same key (LIKELY).
 * 6. The cleaned title; without one, the file's base name cleaned by [TitleCleaner]; in title
 *    case when it is all lower or ALL UPPER ([TitleCase]) (UNKNOWN). Never the raw name.
 *
 * A system without a pack skips the pack layers (the store logs that once per system). A pack
 * that fails a lookup (damaged after it was installed) is logged, skipped for the rest of the
 * call and reported to [PackStore.reportBroken], which reinstalls it once and then gives up. Holds a name matcher per
 * pack, built on first use; safe to call from several threads. Blocking: call it off the main
 * thread.
 */
class Identifier(
    private val packs: PackStore,
    private val fingerprints: FingerprintReader = FingerprintReader(),
    private val log: IdentifyLog = IdentifyLog.STDERR,
) {
    private class PackMatcher(val pack: NamePack, val matcher: NameMatcher)

    private val matchers = HashMap<String, PackMatcher>()

    /** Builds the matcher of a pack; replaced only by tests, to make a lookup fail. */
    internal var matcherFactory: (NamePack) -> NameMatcher = { NameMatcher(it) }

    /**
     * The name of [request]'s file. Bad or unexpected bytes never throw (they leave fingerprints
     * out and the name falls back to a later layer).
     *
     * @throws IOException when [IdentifyRequest.source] fails to read: a failing card or a
     *   revoked document must reach the caller, not look like an unknown game.
     */
    @Throws(IOException::class)
    fun identify(request: IdentifyRequest): Identification {
        if (ArcadeSystems.isArcade(request.systemId)) return identifyArcade(request)
        val pack = PackUse(packs.packFor(request.systemId)?.takeIf { it.kind == PackKind.CONSOLE })
        return identifyConsole(request, pack).copy(packVersion = pack.version)
    }

    private fun identifyConsole(request: IdentifyRequest, pack: PackUse): Identification {
        // A launch file (a .steam app id) has no fingerprint to read, even when a source is given.
        val source = request.source?.takeUnless { LaunchFileSystems.isLaunchFile(request.systemId) }
        var read = request.known?.let { withSwitchIdFromName(it, request) }
            ?: source?.let { fingerprints.read(it, request.fileName, request.systemId, request.depth, request.siblings) }
        // A hack keeps the original's code: once the code is judged not to name this file it is
        // dropped from the result, so the stored fingerprint, fix keys and the box-art lookup
        // behave as for a file without a serial.
        var codeRejected = HackNames.hasHackMarker(request.fileName)
        if (read != null) {
            val fingerprint = read
            pack.lookup { byFingerprint(it, fingerprint, request) { codeRejected = true } }?.let { return it }
        }
        val headerTitle = read?.internalTitle
        if (read != null && !headerTitle.isNullOrBlank() && read.titleIsCanonical) {
            if (HackNames.rejectsCodeName(request.fileName, request.cleanedTitle, headerTitle, wordRule = false)) {
                codeRejected = true
            } else {
                return result(request, read, headerTitle, request.regionsFromName, Confidence.EXACT, Method.HEADER_TITLE)
            }
        }
        if (codeRejected && read != null) read = read.copy(productCode = null, codeKind = null)
        val cleaned = request.cleanedTitle
        if (!cleaned.isNullOrBlank()) {
            pack.lookup { byName(it, cleaned, request, read) }?.let { return it }
        }
        return result(request, read, fallbackTitle(request), request.regionsFromName, Confidence.UNKNOWN, Method.FILE_NAME)
    }

    private fun byFingerprint(
        pack: NamePack,
        read: Fingerprints,
        request: IdentifyRequest,
        onCodeRejected: () -> Unit,
    ): Identification? {
        val crc = read.crc32
        if (crc != null) {
            val size = read.contentSize
            pickRelease(pack.byCrc(crc, size), request.regionsFromName)?.let {
                return fromRelease(request, read, it, Method.CRC)
            }
        }
        // A zipped ROM unpacked at FULL has its header-less CRC above; a pack that lists the
        // headered dump (the archive's own CRC of the entry as stored) is tried second.
        val archiveCrc = read.archiveCrc32
        if (archiveCrc != null && archiveCrc != crc) {
            val size = read.contentSize?.plus(read.headerBytesSkipped)
            pickRelease(pack.byCrc(archiveCrc, size), request.regionsFromName)?.let {
                return fromRelease(request, read, it, Method.CRC)
            }
        }
        val code = read.productCode
        // fixKeys leaves dummy codes out ("0000", "00000000"): such a code names nothing.
        if (!code.isNullOrBlank() && read.fixKeys().any { it.startsWith(CODE_KEY_PREFIX) }) {
            pickRelease(pack.byCode(code), request.regionsFromName)?.let {
                if (HackNames.rejectsCodeName(request.fileName, request.cleanedTitle, it.title, read.codeKind !in NO_WORD_RULE_KINDS)) {
                    onCodeRejected()
                    return null
                }
                return fromRelease(request, read, it, Method.PRODUCT_CODE)
            }
        }
        return null
    }

    /**
     * Cached fingerprints from before Switch ids were read have none: the file name's
     * id is added, as a fresh read would, so the row is named by its id without opening it.
     */
    private fun withSwitchIdFromName(known: Fingerprints, request: IdentifyRequest): Fingerprints {
        if (known.titleId != null || !SwitchReader.claims(request.systemId, extensionOf(request.fileName))) return known
        val titleId = SwitchReader.fromFileName(request.fileName) ?: return known
        return known.copy(
            productCode = SwitchReader.baseOf(titleId),
            codeKind = CodeKind.SWITCH,
            titleId = titleId,
            titleKind = SwitchReader.kindOf(titleId),
        )
    }

    private fun byName(pack: NamePack, cleaned: String, request: IdentifyRequest, read: Fingerprints?): Identification? {
        val candidates = NameCandidates.of(cleaned, request.systemId)
        val match = matcherFor(pack).match(candidates, request.regionsFromName) ?: return null
        val title = pack.title(match.titleIndex)
        return result(request, read, title.name, request.regionsFromName, Confidence.LIKELY, Method.NAME_MATCH)
            .copy(year = title.year, publisher = title.publisher)
    }

    private fun identifyArcade(request: IdentifyRequest): Identification {
        val shortName = ArcadeSystems.shortNameOf(request.fileName)
        val fixKeys = if (shortName.isEmpty()) emptyList() else listOfNotNull(ArcadeSystems.fixKey(shortName))
        val pack = PackUse(packs.packFor(ArcadeSystems.PACK_ID)?.takeIf { it.kind == PackKind.ARCADE })
        val found: ArcadeName? = pack.lookup { ArcadeLookup(it).find(request.fileName) }
        if (found == null) {
            val title = request.cleanedTitle?.takeIf { it.isNotBlank() } ?: shortName.ifEmpty { request.fileName }
            return result(request, request.known, title, emptyList(), Confidence.UNKNOWN, Method.FILE_NAME)
                .copy(fixKeys = fixKeys, packVersion = pack.version)
        }
        // The list shows the short title and the regions the description names; the full
        // description stays for ScreenScraper and the clash rule (Info looks it up again).
        val regions = found.regions.map { it.noIntroName }
        val named = result(request, request.known, found.shortTitle, regions, Confidence.EXACT, Method.ARCADE_NAME)
        return named.copy(
            fullTitle = found.title,
            scrapeHints = named.scrapeHints.copy(canonicalTitle = found.title),
            year = found.year,
            publisher = found.maker,
            hide = found.hide,
            parent = found.parent,
            fixKeys = fixKeys,
            packVersion = pack.version,
        )
    }

    private fun fromRelease(request: IdentifyRequest, read: Fingerprints, release: PickedRelease, method: Method): Identification =
        result(request, read, release.title, release.regions, Confidence.EXACT, method).copy(
            variant = release.variant,
            year = release.year,
            publisher = release.publisher,
        )

    private fun result(
        request: IdentifyRequest,
        read: Fingerprints?,
        title: String,
        regions: List<String>,
        confidence: Confidence,
        method: Method,
    ) = Identification(
        title = title,
        regions = regions,
        variant = null,
        year = null,
        publisher = null,
        confidence = confidence,
        method = method,
        hide = false,
        parent = null,
        fingerprints = read,
        fixKeys = read?.fixKeys().orEmpty(),
        scrapeHints = ScrapeHints(
            crc32 = read?.crc32,
            size = read?.contentSize,
            fileSize = read?.size,
            serial = read?.productCode,
            romFileName = request.fileName,
            canonicalTitle = title.takeIf { confidence != Confidence.UNKNOWN },
            systemId = request.systemId,
        ),
    )

    /**
     * The matcher of [pack]: one per pack instance, made once even when threads race (its index
     * is then built once, by the matcher's own lazy lock, outside this one). A plain lock, since
     * `ConcurrentHashMap.compute` is API 24.
     */
    internal fun matcherFor(pack: NamePack): NameMatcher = synchronized(matchers) {
        val cached = matchers[pack.systemId]
        if (cached != null && cached.pack === pack) return cached.matcher
        PackMatcher(pack, matcherFactory(pack)).also { matchers[pack.systemId] = it }.matcher
    }

    /**
     * The pack of one call. A lookup that throws [CorruptPackException] (a pack that contradicts
     * itself) is logged, the pack is reported to the store and not used again in this call. Any
     * other RuntimeException is a bug in a lookup, not proof the pack file is bad: it is logged
     * and the pack layers are skipped for this call only; the pack is not reinstalled.
     */
    private inner class PackUse(private var pack: NamePack?) {
        /** Data version of the pack while it answered; null when there was none or a lookup failed. */
        val version: Long? get() = pack?.dataVersion

        fun <T> lookup(block: (NamePack) -> T?): T? {
            val current = pack ?: return null
            return try {
                block(current)
            } catch (e: CorruptPackException) {
                log.log("pack ${current.systemId}: lookup failed: ${e.javaClass.simpleName}")
                pack = null
                packs.reportBroken(current, e)
                null
            } catch (e: RuntimeException) {
                log.log("pack ${current.systemId}: lookup failed, pack kept: ${e.javaClass.simpleName}")
                pack = null
                null
            }
        }
    }

    /** What the releases found for one CRC or code say together. */
    private class PickedRelease(val title: String, val regions: List<String>, val variant: String?, val year: Int?, val publisher: String?)

    private companion object {
        const val CODE_KEY_PREFIX = "code:"

        /** Codes judged by the hack tag alone: disc serials and Switch title ids ([HackNames.rejectsCodeName]). */
        val NO_WORD_RULE_KINDS = setOf(
            CodeKind.PS1, CodeKind.PS2, CodeKind.PSP, CodeKind.GAMECUBE, CodeKind.WII,
            CodeKind.SATURN, CodeKind.SEGACD, CodeKind.DREAMCAST, CodeKind.SWITCH,
        )

        fun pickRelease(releases: List<PackRelease>, regionsFromName: List<String>): PickedRelease? {
            if (releases.isEmpty()) return null
            if (releases.any { it.titleIndex != releases[0].titleIndex }) return null
            val inRegion = releases.filter { release ->
                release.regions.any { region -> regionsFromName.any { it.equals(region, ignoreCase = true) } }
            }
            val kept = inRegion.ifEmpty { releases }
            val first = kept[0]
            val regions = kept.flatMap { it.regions }.toSet()
            return PickedRelease(
                title = first.title,
                regions = PackMasks.REGIONS.filter { it in regions },
                variant = first.variant.takeIf { kept.all { r -> r.variant == first.variant } },
                year = first.year,
                publisher = first.publisher,
            )
        }

        /** Console only (arcade keeps its short name): the cleaned title, in title case when it is all one case. */
        fun fallbackTitle(request: IdentifyRequest): String {
            val cleaned = request.cleanedTitle?.takeIf { it.isNotBlank() } ?: run {
                val base = request.fileName.substringAfterLast('/').substringAfterLast('\\')
                val dot = base.lastIndexOf('.')
                TitleCleaner.clean(if (dot > 0) base.substring(0, dot) else base).title
            }
            return TitleCase.forSystem(cleaned, request.systemId)
        }
    }
}
