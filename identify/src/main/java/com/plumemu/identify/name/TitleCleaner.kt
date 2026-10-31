package com.plumemu.identify.name

import java.text.Normalizer
import java.util.Locale

/** Disc number read from `(Disc 2)` or `(Disc 2 of 3)`. */
data class DiscTag(val number: Int, val total: Int?)

/** A file name turned into what the list shows and how it sorts. */
data class CleanTitle(
    val title: String,
    val sortName: String,
    val regions: List<Region>,
    val disc: DiscTag?,
    /**
     * The name's tags other than region, language, disc and dump-quality marks, lowercased:
     * `[t+eng]`, `(hack)`, `(rev 1)`, `(beta)`. Copies are regional copies of one game only when
     * this is equal, so a translation or a hack is never hidden behind the original.
     */
    val variantKey: String,
)

/**
 * Title cleaning for file names (No-Intro, Redump and GoodTools style, and the habits of people's
 * collections), in this order:
 *
 * 1. Download-site marks outside brackets go ([SiteMarks]).
 * 2. A scene release name (`Title.Words.USA.PSP-GROUP`, `Title_Words_NTSC_PS2DVD-GROUP`) loses its
 *    `-GROUP` and its tail of scene words ([ReleaseTags.isSceneTail]); a region word in that tail
 *    is kept as a region. Otherwise, in a name without spaces, underscores and dots read as spaces
 *    (a dot between two digits stays, and so do the dots of a name made only of single letters,
 *    "M.A.S.H").
 *    A trailing download or copy suffix (` (1)` to ` (9)`, ` - Copy`, ` copy`, after every other tag)
 *    goes first, so `Game (USA) (1).sfc` groups with `Game (USA).sfc`. The name is first folded
 *    with NFKC, so full-width letters and brackets read as plain ones.
 * 3. A DS / GBA release number with a leading zero (`0445 - Title`) goes. One without a leading
 *    zero may be a title ("1942 - ...", "1943 - The Battle of Midway"): the identifier tries the
 *    name without it as a candidate only (`NameCandidates`).
 * 4. Every bracket group (`(...)`, `[...]`, `{...}`) is removed from the title; region groups are
 *    kept as [CleanTitle.regions] (`[US]` too); site marks, version and update codes, Switch ids,
 *    region, language, disc and dump-quality groups stay out of [CleanTitle.variantKey].
 * 5. A trailing version outside brackets (` v1.3`) goes; a trailing "Title, The" article moves to
 *    the front.
 *
 * The title keeps the file's case: an all-lower or ALL-UPPER title is put in title case only where
 * the shown fallback is chosen for a known console system ([TitleCase.forSystem]), never for
 * arcade short names.
 *
 * Pure. The Python port in the build tools shares only the rules that apply to DAT names
 * (brackets, regions, languages, articles, underscores); steps 1-3 and 5's version are for
 * file names only.
 */
object TitleCleaner {
    // Every closing bracket escaped: Android's ICU regex rejects a bare `}` (and the class failed
    // to load on Android 6), while the JVM accepts it, so JVM tests cannot catch this.
    private val bracketGroup = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]|\{([^{}]*)\}""")
    private val discGroup = Regex("""\(\s*(?:disc|disk|cd)\s*(\d+)(?:\s*of\s*(\d+))?\s*\)""", RegexOption.IGNORE_CASE)
    private val trackGroup = Regex("""\(\s*track\s*\d+\s*\)""", RegexOption.IGNORE_CASE)
    private val trailingArticle = Regex("""^(.+?),\s+(The|A|An)(\s+-\s+.*|:\s+.*)?$""", RegexOption.IGNORE_CASE)
    private val leadingArticle = Regex("""^(the|a|an)\s+""")
    private val spaces = Regex("""\s+""")
    private val diacritics = Regex("""\p{Mn}+""")
    private val languageGroup = Regex("""[A-Z][a-z](?:[-+][A-Z][a-z])?(?:\s*,\s*[A-Z][a-z](?:[-+][A-Z][a-z])?)*""")
    private val dumpMark = Regex("""!|[abof]\d*""")
    private val sceneName = Regex("""^(.*[._].*)-([A-Za-z0-9]+)$""")
    // A download or file-manager copy: "Game (USA) (1)", "Game - Copy", "Game copy", also stacked.
    private val copySuffix = Regex("""(?:\s+\(\s*[1-9]\s*\)|\s+-\s+copy|\s+copy)+\s*$""", RegexOption.IGNORE_CASE)
    private val leadingZeroNumber = Regex("""^0\d{3}\s*-\s+""")
    private const val TRIM_END = "-,;:+_ "
    private const val TRIM_START = "-+_ "

    /**
     * [baseName] without its download / copy suffix, or null when it has none. A caller that sees
     * the whole folder uses it to drop the suffix only when a file of the plain name exists (`Game
     * (1)`, `Game (2)` alone are different files, not copies): `clean(name, dropCopySuffix = true)`.
     * [clean] keeps the suffix by default, so callers that see one name never merge files.
     */
    fun withoutCopySuffix(baseName: String): String? {
        val folded = Normalizer.normalize(baseName, Normalizer.Form.NFKC)
        return copySuffix.find(folded)?.let { folded.substring(0, it.range.first) }
    }

    /** Cleans [baseName], the file name without its extension. */
    fun clean(baseName: String, dropCopySuffix: Boolean = false): CleanTitle {
        val regions = mutableListOf<Region>()
        // Compatibility fold first: full-width letters, digits, brackets and the ideographic space
        // become their plain forms, so the region and the match key read as for an ASCII name.
        val folded = Normalizer.normalize(baseName, Normalizer.Form.NFKC)
        val unmarked = SiteMarks.strip(if (dropCopySuffix) folded.replaceFirst(copySuffix, "") else folded)
        // Leading dots belong to the title (".hack", "...Iru!"), never a separator.
        val leadingDots = unmarked.takeWhile { it == '.' }
        val rest = unmarked.substring(leadingDots.length)
        val scene = sceneSplit(rest)
        var name = leadingDots + if (scene != null) {
            regions += scene.second
            scene.first
        } else {
            spacesForSeparators(rest)
        }
        name = name.replaceFirst(leadingZeroNumber, "")
        val groups = bracketGroup.findAll(name).toList()
        groups.forEach { match ->
            val round = match.groups[1]?.value
            val square = match.groups[2]?.value
            when {
                round != null -> Region.parseGroup(round)?.let(regions::addAll)
                square != null -> Region.parseNamed(square)?.let(regions::addAll)
            }
        }
        val variant = groups.filterNot(::isSameGameGroup).joinToString(" ") { key(it.value) }
        val disc = discGroup.find(name)?.let { m ->
            DiscTag(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull())
        }
        var title = bracketGroup.replace(name, " ").replace(spaces, " ").trim().trimEnd { it in TRIM_END }
        title = title.replaceFirst(ReleaseTags.trailingVersion, "").trimEnd { it in TRIM_END }.trimStart { it in TRIM_START }
        title = moveTrailingArticle(title)
        if (title.isEmpty()) title = folded.trim()
        return CleanTitle(title, sortName(title), regions.distinct(), disc, variant)
    }

    /**
     * The content of every bracket group of [name] that keeps copies apart (the ones [CleanTitle.variantKey]
     * holds), in order: `Rev 1`, `T+Eng`, `Hack`. [VariantTags] reads them. [name] is already NFKC-folded.
     */
    internal fun variantGroups(name: String): List<String> =
        bracketGroup.findAll(SiteMarks.strip(name)).filterNot(::isSameGameGroup).map { m -> m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value.orEmpty() }.toList()

    /**
     * True when a bracket group says nothing about which game this is: region, language, disc,
     * dump quality, site mark, version or update code. Everything else (`(Rev 1)`, `(Hack)`,
     * `[T+Eng]`, `[h1C]`) keeps copies apart.
     */
    private fun isSameGameGroup(match: MatchResult): Boolean {
        val round = match.groups[1]?.value
        val other = round ?: match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
        val square = round == null
        return when {
            SiteMarks.isSite(other) || ReleaseTags.isVersionOrUpdate(other, square) -> true
            round != null -> Region.parseGroup(round) != null || languageGroup.matches(round.trim()) || discGroup.matches(match.value)
            else -> dumpMark.matches(other.trim()) || Region.parseNamed(other) != null
        }
    }

    /**
     * A scene release name as its title and the regions of its tail, or null when [name] is not
     * one: no spaces, words joined by dots or underscores, `-GROUP` at the end, and a scene word
     * ([ReleaseTags.isSceneTail]) right before it.
     */
    private fun sceneSplit(name: String): Pair<String, List<Region>>? {
        if (' ' in name) return null
        val match = sceneName.matchEntire(name) ?: return null
        val words = splitAt(match.groupValues[1], "._").filter { it.trimStart('.', '_').isNotEmpty() }.toMutableList()
        fun last() = words.last().trimStart('.', '_')
        if (words.size < 2 || !ReleaseTags.isSceneTail(last())) return null
        val regions = mutableListOf<Region>()
        // The platform tag comes once, last; before it only flags and regions ("Ocarina GC.NTSC.NGC").
        if (ReleaseTags.isScenePlatform(last())) words.removeAt(words.size - 1)
        while (words.size > 1 && ReleaseTags.isSceneTail(last()) && !ReleaseTags.isScenePlatform(last())) {
            ReleaseTags.sceneRegion(last())?.let { regions.addAll(0, it) }
            words.removeAt(words.size - 1)
        }
        return joinWords(words) to regions
    }

    /** Underscores and dots as spaces in a name without spaces; see the class KDoc for the dots kept. */
    private fun spacesForSeparators(name: String): String {
        if (' ' in name) return name
        val underscored = name.replace('_', ' ')
        if ('.' !in underscored) return underscored
        val beforeBrackets = underscored.substringBefore('(').substringBefore('[')
        if (beforeBrackets.split('.').all { it.trim().length <= 1 }) return underscored
        return joinWords(splitAt(underscored, "."))
    }

    /**
     * [text] split at each of [separators], each word but the first keeping the separator before it
     * as its first character, so [joinWords] can tell a dot from an underscore.
     */
    private fun splitAt(text: String, separators: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        for (i in text.indices) {
            if (text[i] in separators) {
                out += text.substring(start, i)
                start = i
            }
        }
        out += text.substring(start)
        return out
    }

    /**
     * Words from [splitAt] joined by spaces, except a dot between two digits ("1.00"); empty words
     * dropped.
     */
    private fun joinWords(words: List<String>): String {
        val out = StringBuilder()
        for (raw in words) {
            val separator = raw.firstOrNull()?.takeIf { out.isNotEmpty() && (it == '.' || it == '_') }
            val word = raw.trimStart('.', '_')
            if (word.isEmpty()) continue
            if (out.isNotEmpty()) {
                val numberDot = separator == '.' && out.last().isDigit() && word.first().isDigit()
                out.append(if (numberDot) '.' else ' ')
            }
            out.append(word)
        }
        return out.toString()
    }

    /** Sort key: no case, no accents, no leading article ("The", "A", "An"), no leading symbols. */
    fun sortName(title: String): String {
        val folded = Normalizer.normalize(Normalizer.normalize(title, Normalizer.Form.NFKC), Normalizer.Form.NFD).replace(diacritics, "")
            .lowercase(Locale.ROOT).replace(spaces, " ").trim()
        return folded.replaceFirst(leadingArticle, "").trimStart { !it.isLetterOrDigit() }
    }

    /**
     * Key that disc files of one game share: the name without its disc tag, without case and with
     * single spaces. `Game (USA) (Disc 2)` and `Game (USA) (Disc 1 of 2)` give `game (usa)`.
     */
    fun discBaseKey(baseName: String): String = key(discGroup.replace(baseName, " "))

    /** Like [discBaseKey] for audio tracks: `Game (Track 02)` gives `game`. */
    fun trackBaseKey(baseName: String): String = key(trackGroup.replace(baseName, " "))

    /** The name without its disc tag, case kept, for a multi-disc game's group name. */
    fun withoutDiscTag(baseName: String): String = discGroup.replace(baseName, " ").replace(spaces, " ").trim()

    fun hasDiscTag(baseName: String): Boolean = discGroup.containsMatchIn(baseName)

    fun key(name: String): String = name.lowercase(Locale.ROOT).replace(spaces, " ").trim()

    private fun moveTrailingArticle(title: String): String {
        val match = trailingArticle.matchEntire(title) ?: return title
        val (main, article, rest) = match.destructured
        return "$article $main$rest"
    }
}
