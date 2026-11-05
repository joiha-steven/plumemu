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
    private const val MAX_KEPT_FORMS = 12
    private const val MAX_KEPT_GROUPS = 3

    /**
     * [baseName] without its download / copy suffix, or null when it has none. A caller that sees
     * the whole folder uses it to drop the suffix only when a file of the plain name exists (`Game
     * (1)`, `Game (2)` alone are different files, not copies): `clean(name, dropCopySuffix = true)`.
     * [clean] keeps the suffix by default, so callers that see one name never merge files.
     */
    fun withoutCopySuffix(baseName: String): String? {
        val folded = fold(baseName)
        return copySuffix.find(folded)?.let { folded.substring(0, it.range.first) }
    }

    /**
     * NFKC, except the acute accent U+00B4 used as an apostrophe: NFKC would turn it into a space and a
     * combining accent, which breaks a dotted name ("Milon<acute>s.Secret.Castle") into two words.
     * `MatchKey` drops it like any apostrophe.
     */
    private fun fold(baseName: String): String =
        baseName.split('\u00b4').joinToString("\u00b4") { Normalizer.normalize(it, Normalizer.Form.NFKC) }

    /** Cleans [baseName], the file name without its extension. */
    fun clean(baseName: String, dropCopySuffix: Boolean = false): CleanTitle {
        val regions = mutableListOf<Region>()
        // Compatibility fold first: full-width letters, digits, brackets and the ideographic space
        // become their plain forms, so the region and the match key read as for an ASCII name.
        val folded = fold(baseName)
        val unmarked = SiteMarks.strip(if (dropCopySuffix) folded.replaceFirst(copySuffix, "") else folded)
        val prepared = prepare(unmarked)
        regions += prepared.regions
        var name = prepared.name
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

    /** A name with its separators read: [alternatives] are readings that keep tail words of a scene name. */
    private class Prepared(val name: String, val regions: List<Region>, val alternatives: List<Alternative>)

    /** A scene reading [text] that keeps tail words; [ungated] when [NameMatcher] may take it even if the plain title is in the pack. */
    private class Alternative(val text: String, val ungated: Boolean)

    /** The kept forms of a name, and those of them [NameMatcher] takes without its region gate. */
    internal class KeptForms(val forms: List<String>, val ungated: Set<String>)

    private class KeptCollector {
        val forms = LinkedHashSet<String>()
        val ungated = HashSet<String>()
        fun add(form: String, isUngated: Boolean) {
            forms += form
            if (isUngated) ungated += form
        }
    }

    /** Steps 2-3 of the class KDoc on [unmarked] (site marks and copy suffix already gone). */
    private fun prepare(unmarked: String): Prepared {
        // Leading dots belong to the title (".hack", "...Iru!"), never a separator.
        val leadingDots = unmarked.takeWhile { it == '.' }
        val rest = unmarked.substring(leadingDots.length)
        val scene = sceneSplit(rest)
        val name = leadingDots + (scene?.title ?: spacesForSeparators(rest))
        val alternatives = scene?.alternatives.orEmpty().map { Alternative((leadingDots + it.text).replaceFirst(leadingZeroNumber, ""), it.ungated) }
        return Prepared(name.replaceFirst(leadingZeroNumber, ""), scene?.regions.orEmpty(), alternatives)
    }

    /**
     * Spellings of [baseName] that keep what [clean] throws away, for a matcher that accepts one only
     * when it equals a pack title's key (`NameCandidates`): a trailing version (`Game v1.0`); the
     * bracket groups that are not region, language, disc or code tags (`Game (Demo)`, `(Italiano) Game`);
     * a name that is only brackets; a domain-like start (`Amiga.it_Numero_1`); the scene tail words
     * that can be part of a title (`Gekikame.Ninja.Den.JPN-GRP`, `Zero:.Update.EUR-GRP`). Most names give none.
     */
    internal fun keptForms(baseName: String): List<String> = keptFormsWithGate(baseName).forms

    /** [keptForms] with the ones a region repeated by the next scene word makes ("Cruis'n.USA.USA-GRP"). */
    internal fun keptFormsWithGate(baseName: String): KeptForms {
        val folded = fold(baseName).replaceFirst(copySuffix, "")
        val out = KeptCollector()
        val unmarked = SiteMarks.strip(folded)
        addKeptForms(prepare(unmarked), out, plain = false)
        if (unmarked != folded) addKeptForms(prepare(folded), out, plain = true)
        val forms = if (out.forms.size <= MAX_KEPT_FORMS) out.forms.toList() else out.forms.toList().subList(0, MAX_KEPT_FORMS)
        return KeptForms(forms, out.ungated.filter { it in forms }.toSet())
    }

    private fun addKeptForms(prepared: Prepared, out: KeptCollector, plain: Boolean) {
        addGroupForms(prepared.name, out, plain, ungated = false)
        for (alternative in prepared.alternatives) {
            addGroupForms(alternative.text, out, plain = true, ungated = alternative.ungated)
        }
    }

    /** [plain]: also the name without any group, which [clean] would not give (a site mark or scene word kept). */
    private fun addGroupForms(name: String, out: KeptCollector, plain: Boolean, ungated: Boolean) {
        val groups = bracketGroup.findAll(name).toList()
        val bare = bracketGroup.replace(name, " ").replace(spaces, " ").trim().trimEnd { it in TRIM_END }.trimStart { it in TRIM_START }
        if (plain && bare.isNotEmpty()) out.add(moveTrailingArticle(bare), ungated)
        if (ReleaseTags.trailingVersion.containsMatchIn(bare)) out.add(moveTrailingArticle(bare), ungated)
        if (groups.isEmpty()) return
        // A name that is only brackets has nothing else to be: every group but a region is its title.
        val variants = if (bare.isEmpty()) {
            groups.filter { Region.parseGroup(it.groups[1]?.value ?: it.value) == null }
        } else {
            groups.filterNot(::isSameGameGroup)
        }
        // A title can hold a group that looks like a version or content code: "Re Mind (DLC) Cloud Version".
        val codeLike = groups.filter { it.groups[1]?.value?.let { round -> ReleaseTags.isVersionOrUpdate(round, square = false) } == true }
        // A group stuck to the word before it is part of that word ("Northern Light(s)"), unless it is a region, language or
        // dump group ("Game(USA)"); "(s)" is the plural, though "S" is also a region code.
        val attached = groups.filter { it.range.first > 0 && name[it.range.first - 1].isLetterOrDigit() && (it.value.equals("(s)", ignoreCase = true) || !isSameGameGroup(it)) }
        val sets = ArrayList<List<MatchResult>>()
        if (attached.isNotEmpty()) sets += attached
        if (variants.isNotEmpty()) {
            sets += variants
            if (variants.size > 1) variants.take(MAX_KEPT_GROUPS).forEach { sets += listOf(it) }
        }
        if (codeLike.isNotEmpty()) {
            sets += codeLike
            if (variants.isNotEmpty()) sets += groups.filter { it in variants || it in codeLike }
        }
        if (bare.isEmpty() && groups.size > 1) {
            sets += groups
            sets += groups.distinctBy { it.value }
        }
        for (set in sets) {
            val text = withGroups(name, groups, set)
            if (text.isEmpty()) continue
            out.add(text, ungated)
            if (ReleaseTags.trailingVersion.containsMatchIn(text)) {
                out.add(text.replaceFirst(ReleaseTags.trailingVersion, "").trimEnd { it in TRIM_END }, ungated)
            }
        }
    }

    /** [name] with every bracket group of [groups] removed except the ones in [keep], which stay in place. */
    private fun withGroups(name: String, groups: List<MatchResult>, keep: List<MatchResult>): String {
        val text = StringBuilder(name.length)
        var from = 0
        for (group in groups) {
            text.append(name, from, group.range.first)
            text.append(if (group in keep) group.value else " ")
            from = group.range.last + 1
        }
        text.append(name, from, name.length)
        return text.toString().replace(spaces, " ").trim().trimEnd { it in TRIM_END }.trimStart { it in TRIM_START }
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
    private class Scene(val title: String, val regions: List<Region>, val alternatives: List<Alternative>)

    private fun sceneSplit(name: String): Scene? {
        if (' ' in name) return null
        val match = sceneName.matchEntire(name) ?: return null
        val words = splitAt(match.groupValues[1], "._").filter { it.trimStart('.', '_').isNotEmpty() }.toMutableList()
        fun last() = words.last().trimStart('.', '_')
        if (words.size < 2 || !ReleaseTags.isSceneTail(last())) return null
        val regions = mutableListOf<Region>()
        // The platform tag comes once, last; before it only flags and regions ("Ocarina GC.NTSC.NGC").
        if (ReleaseTags.isScenePlatform(last())) words.removeAt(words.size - 1)
        val tail = ArrayList<String>()
        while (words.size > 1 && ReleaseTags.isSceneTail(last()) && !ReleaseTags.isScenePlatform(last())) {
            ReleaseTags.sceneRegion(last())?.let { regions.addAll(0, it) }
            tail.add(0, words.removeAt(words.size - 1))
        }
        // A tail word can be the title's own ("Ninja.Den", "Zero:.Update", "Sheep.It.Up!.-.NTSC"): the
        // title with its first one, two, ... tail words put back, for a matcher that checks them. A
        // form that ends at the last tail word (the region) is never made ("Super.Mario.USA.NTSC-GRP" is
        // "Super Mario"). A form that ends at a region code with another region after it ("Ninja.Den.JPN",
        // "Super.Mario.USA.EUR") is made, but [NameMatcher] takes it only when the plain title is not in the pack.
        fun isRegion(i: Int) = ReleaseTags.isSceneRegionCode(tail[i].trimStart('.', '_'))
        // A region the next word repeats is the title's own ("Cruis'n.USA.USA-GRP"): its form carries
        // ungated, so NameMatcher takes it even when the plain title is in the pack.
        fun repeats(i: Int) =
            i + 1 < tail.size && ReleaseTags.sceneRegion(tail[i].trimStart('.', '_')).let { it != null && it == ReleaseTags.sceneRegion(tail[i + 1].trimStart('.', '_')) }
        val alternatives = (1..tail.size)
            .filter { !(isRegion(it - 1) && (it == tail.size || !isRegion(it))) }
            .map { Alternative(joinWords(words + tail.take(it)), isRegion(it - 1) && repeats(it - 1)) }
        return Scene(joinWords(words), regions, alternatives)
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
