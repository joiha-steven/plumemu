package com.plumemu.identify.arcade

import com.plumemu.identify.name.Region
import com.plumemu.identify.pack.ArcadeEntry
import com.plumemu.identify.pack.NamePack
import java.util.Locale

/**
 * The systems whose files are arcade sets named by short name (`mslug.zip`), from
 * `platforms/src/main/assets/platforms.json`. All of them read the one shared arcade pack.
 * Not here on purpose: `neogeocd` (discs), `consolearcade` and `pcarcade` (console and PC games
 * with ordinary file names).
 */
object ArcadeSystems {
    /** The system id of the shared arcade pack. */
    const val PACK_ID = "arcade"

    val IDS: Set<String> = setOf(
        "arcade", "mame", "fba", "fbneo", "neogeo", "cps", "cps1", "cps2", "cps3",
        "atomiswave", "naomi", "naomi2", "naomigd", "stv", "model2", "model3",
    )

    fun isArcade(systemId: String): Boolean = systemId.lowercase(Locale.ROOT) in IDS

    /** The short name of an arcade file: its base name, lower case, without the extension. */
    fun shortNameOf(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        return (if (dot > 0) base.substring(0, dot) else base).lowercase(Locale.ROOT)
    }

    /**
     * The key a person's name fix for this set is stored under (the only key for arcade sets), or
     * null when the short name holds a control character: keys are stored one per line, and a
     * file name may contain a line break. Such a set falls back to its row id.
     */
    fun fixKey(shortName: String): String? =
        if (shortName.any { it.isISOControl() }) null else "arcade:" + shortName.lowercase(Locale.ROOT)
}

/**
 * The short titles arcade sets show. MAME's descriptions name the version too ("X-Men (2 Players
 * ver AAA)", "Street Fighter II' - Hyper Fighting (US 921209)"); a list shows the game's name and
 * keeps the description for Info and for ScreenScraper. library shows the description instead
 * when two games of one folder would get the same short title.
 */
object ArcadeTitles {
    /**
     * [description] cut at the first " / " outside brackets, every bracket group (round or square,
     * nested ones included; a group left open runs to the end) and every unmatched closing bracket
     * removed, spaces collapsed; the
     * description itself, spaces collapsed, when nothing would be left. A short title gives itself
     * back. The Python pack tool follows the same rule for the pack's aliases.
     */
    fun shortTitle(description: String): String {
        val out = StringBuilder(description.length)
        var depth = 0
        var i = 0
        while (i < description.length) {
            val ch = description[i]
            when {
                ch == '(' || ch == '[' -> depth++
                // A closing bracket with no opening one (MAME's own typo) is dropped too.
                ch == ')' || ch == ']' -> if (depth > 0) depth--
                depth == 0 -> {
                    if (description.startsWith(" / ", i)) break
                    out.append(ch)
                }
            }
            i++
        }
        return collapseSpaces(out).ifEmpty { collapseSpaces(description) }
    }

    /**
     * The regions [description] names, in the order found, each once: what the list's region
     * column shows for an arcade set ("Cadillacs and Dinosaurs (USA 930201)" gives USA). Every
     * bracket group (round or square, read like [shortTitle]: a nested group is part of the one
     * around it, a group left open runs to the end) is split at `,`, `/`, `+` and the word "and";
     * a part names a region when it starts with a region word ([REGION_WORDS], any case, a whole
     * word, so "World? set 1" and "US set 1" count). Not read on purpose: Konami's version
     * letters ("ver UAB"), "Oceania", "English" and other languages, a region word in the middle
     * of a part ("Joystick hack bootleg", "bootleg of US set"), and "&" ("Hong Kong & Taiwan" gives
     * Hong Kong only). Kotlin only: the pack stores the description, nothing is derived from it
     * at build time.
     */
    fun regions(description: String): List<Region> {
        val found = LinkedHashSet<Region>()
        for (group in bracketGroups(description)) {
            for (part in group.split(PART_SEPARATOR)) {
                regionAtStart(part.trim().lowercase(Locale.ROOT))?.let(found::add)
            }
        }
        return found.toList()
    }

    /**
     * What tells [description]'s set apart from other sets of the game: the text inside each
     * outermost bracket group, in order, spaces collapsed, empty groups left out ("Alien Storm
     * (World, 2 Players) (FD1094 317-0154)" gives "World, 2 Players" and "FD1094 317-0154"; a nested
     * group stays in its group, a group left open runs to the end). Text outside brackets is left
     * out, a second title after " / " too: the shown title already names the game. Empty for a
     * description without brackets (and for a tier-2 alias, whose title is already short).
     */
    fun details(description: String): List<String> =
        bracketGroups(description).map { collapseSpaces(it) }.filter { it.isNotEmpty() }

    /** The text inside each outermost bracket group of [description], nested brackets included. */
    private fun bracketGroups(description: String): List<String> {
        val groups = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in description) {
            when {
                ch == '(' || ch == '[' -> {
                    if (depth > 0) current.append(ch)
                    depth++
                }
                ch == ')' || ch == ']' -> if (depth > 0) {
                    depth--
                    if (depth == 0) {
                        groups += current.toString()
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                depth > 0 -> current.append(ch)
            }
        }
        if (depth > 0) groups += current.toString()
        return groups
    }

    /** The region [part] (lower case, trimmed) starts with as a whole word, or null. */
    private fun regionAtStart(part: String): Region? {
        for ((word, region) in REGION_WORDS) {
            if (part.startsWith(word) && (part.length == word.length || !part[word.length].isLetterOrDigit())) return region
        }
        return null
    }

    /** Where a bracket group is split into parts: `,`, `/`, `+` and the word "and". */
    private val PART_SEPARATOR = Regex("[,/+]|\\band\\b", RegexOption.IGNORE_CASE)

    /**
     * The words that name a region at the start of a part, lower case. Whole words only, so the
     * order does not matter ("us" never matches "usa").
     */
    private val REGION_WORDS: List<Pair<String, Region>> = listOf(
        "world" to Region.WORLD, "usa" to Region.USA, "us" to Region.USA, "japan" to Region.JAPAN,
        "europe" to Region.EUROPE, "euro" to Region.EUROPE, "asia" to Region.ASIA, "korea" to Region.KOREA,
        "taiwan" to Region.TAIWAN, "hong kong" to Region.HONG_KONG, "china" to Region.CHINA,
        "brazil" to Region.BRAZIL, "canada" to Region.CANADA, "uk" to Region.UK, "france" to Region.FRANCE,
        "germany" to Region.GERMANY, "spain" to Region.SPAIN, "italy" to Region.ITALY,
        "australia" to Region.AUSTRALIA,
    )

    /** Runs of whitespace (any, as Python's `str.split()`) become one space; none at either end. */
    private fun collapseSpaces(text: CharSequence): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                pendingSpace = out.isNotEmpty()
            } else {
                if (pendingSpace) out.append(' ')
                pendingSpace = false
                out.append(ch)
            }
        }
        return out.toString()
    }
}

/**
 * What the arcade pack says about one set.
 *
 * @property title the pack's title: MAME's full description ("Zork Fighters (Japan 961201)").
 * @property hide true for BIOS, device and other non-game sets: the scan leaves them out (I3).
 * @property parent the parent's short name for a clone, so clones can be grouped with it.
 */
data class ArcadeName(
    val shortName: String,
    val title: String,
    val year: Int?,
    val maker: String?,
    val parent: String?,
    val isBios: Boolean,
    val isDevice: Boolean,
    val isMechanical: Boolean,
    val isClone: Boolean,
    val hide: Boolean,
) {
    /** The title a list shows ([ArcadeTitles.shortTitle] of [title]). */
    val shortTitle: String get() = ArcadeTitles.shortTitle(title)

    /** The regions [title] names ([ArcadeTitles.regions]); empty for a tier-2 alias, whose title is already short. */
    val regions: List<Region> get() = ArcadeTitles.regions(title)
}

/** Looks arcade files up in an arcade pack by short name. */
class ArcadeLookup(private val pack: NamePack) {
    /** The set [fileName] (a file name or a short name) holds, or null when the pack does not list it. */
    fun find(fileName: String): ArcadeName? {
        val shortName = ArcadeSystems.shortNameOf(fileName)
        if (shortName.isEmpty()) return null
        return pack.byShortName(shortName)?.let(::toName)
    }

    private fun toName(entry: ArcadeEntry) = ArcadeName(
        shortName = entry.shortName,
        title = entry.title,
        year = entry.year,
        maker = entry.maker,
        parent = entry.parentShortName,
        isBios = entry.isBios,
        isDevice = entry.isDevice,
        isMechanical = entry.isMechanical,
        isClone = entry.isClone,
        hide = entry.isBios || entry.isDevice || entry.isNotAGame,
    )
}
