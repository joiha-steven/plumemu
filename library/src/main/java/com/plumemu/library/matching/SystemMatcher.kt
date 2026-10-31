package com.plumemu.library.matching

import java.util.Locale

/** What a folder name says about the system of the games inside it. */
sealed interface FolderMatch {
    data class System(val rule: SystemRule) : FolderMatch

    /** Several systems use this folder name; the file extension decides, then rule order. */
    data class Ambiguous(val rules: List<SystemRule>) : FolderMatch

    data object Unknown : FolderMatch

    /**
     * Several folders on a directory's path name systems, nearest first ([SystemMatcher.matchPath]
     * only; each is a [System] or [Ambiguous]). Each file goes to the nearest whose system, or a
     * close kin, takes its extension (`SNES/Doom/Doom.sfc` is SNES, `ports/Doom/doom.wad` is Doom);
     * when none does, the nearest decides as if it were alone. Under a folder the person chose a
     * system for, the last candidate is [Unknown]: a file no other candidate takes goes by its
     * extension alone, as it did before the choice.
     */
    data class Path(val candidates: List<FolderMatch>) : FolderMatch
}

/** What the library decided for one file. */
sealed interface FileMatch {
    data class Matched(val systemId: String) : FileMatch

    /** No rule (or no rule of the folder's system) claims the extension. */
    data object UnknownExtension : FileMatch

    /** The folder name is unknown and several systems claim the extension (`.bin`, `.iso`, `.zip`). */
    data object AmbiguousExtension : FileMatch
}

/**
 * Bump when folder matching learns new names, or when the catalogue groups a directory's files
 * differently (4: disc markers, one game's folder, `.gdi` tracks, lone playlists; 5: Switch base, update and DLC by title id; 6: `windows` no longer claims `.steam`,
 * so such files listed as Windows games become Steam games; 7, schema 9: each directory's unknown
 * files are counted when it is listed, so every directory is listed once to find unknown folders; 8, audit items 18 and 19: a sheet's
 * parts in its own folder are not games, and sibling game folders of one game become copies; 9, `cube` names GameCube, so a `Cube` folder listed as unknown by 8 is listed again). The
 * app compares it with the value stored after its last scan and, when lower, lists every folder
 * again once (unchanged folders are otherwise skipped, so games dropped by the old matching would
 * never come back, and old grouping would stay).
 */
const val FOLDER_MATCH_VERSION = 9

/** Folder-to-system matching by folder name first, then by extension. Pure and thread-safe. */
class SystemMatcher(rules: List<SystemRule>) {
    private val byFolder: Map<String, List<SystemRule>> = index(rules) { it.folders }
    private val byName: Map<String, List<SystemRule>> = index(rules) { it.names + DAT_FOLDER_NAMES[it.id].orEmpty() }

    private val byId: Map<String, SystemRule> = rules.associateBy { it.id }

    /** Where a MAME or FinalBurn romset-family folder goes: the generic arcade system, if there is one. */
    private val arcade: SystemRule? = rules.firstOrNull { it.id == ARCADE_ID }

    private val byExtension: Map<String, List<SystemRule>> = rules
        .flatMap { rule -> rule.extensions.map { it.removePrefix(".").lowercase(Locale.ROOT) to rule } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> list.distinct() }

    /** For a file in a folder that names no system: the one system its extension points to ([soleClaimant]). */
    private val byExtensionAlone: Map<String, SystemRule> = byExtension.entries
        .mapNotNull { (extension, claimants) -> soleClaimant(extension, claimants)?.let { extension to it } }
        .toMap()

    fun matchFolder(name: String): FolderMatch = matchFolderName(name).match

    /** A folder's match, and whether only its trailing bracket group made it (see [matchPath]). */
    private class NamedMatch(val match: FolderMatch, val byBracket: Boolean)

    /**
     * Order: without a list number (`01 - SNES`, `1. NES`, `05_N64`, `3. DS`: the number goes
     * first, or `3. DS` would read as 3DS); the whole name ([matchWholeName]); without a number
     * and a space (`3 Saturn`; `3 DS` stays 3DS); then the tag of a trailing bracket group, MinUI and
     * Onion style (`Super Nintendo (SFC)`), which those launchers pick the emulator by; then the
     * words before the brackets, but only when every bracket group is a [FOLDER_QUALIFIERS] word
     * (`SNES (Hacks)`, `Nintendo - SNES (Romhacks)`). A region, language or disc never counts:
     * game folders are `Doom (Europe)`, `Quake (USA)`. Both bracket rules are weak (see [matchPath]).
     */
    private fun matchFolderName(name: String): NamedMatch {
        matchWithoutBrackets(name)?.let { return NamedMatch(it, byBracket = false) }
        BRACKET_TAG.matchEntire(name)?.let { m ->
            lookup(normalizeFolderName(m.groupValues[2]))?.let { return NamedMatch(it, byBracket = true) }
        }
        BRACKET_GROUPS.matchEntire(name)?.let { m ->
            val groups = Regex("\\(([^()]+)\\)").findAll(m.groupValues[2]).map { normalizeFolderName(it.groupValues[1]) }.toList()
            if (groups.all { it in FOLDER_QUALIFIERS }) matchWithoutBrackets(m.groupValues[1])?.let { return NamedMatch(it, byBracket = true) }
        }
        return NamedMatch(FolderMatch.Unknown, byBracket = false)
    }

    private fun matchWithoutBrackets(name: String): FolderMatch? {
        LIST_NUMBER.matchEntire(name)?.let { m -> matchWholeName(m.groupValues[1])?.let { return it } }
        matchWholeName(name)?.let { return it }
        SPACED_NUMBER.matchEntire(name)?.let { m -> matchWholeName(m.groupValues[1])?.let { return it } }
        return null
    }

    /**
     * Order: (a) a folder alias as written; (b) the system's own names; (c) `Maker - Rest`, where
     * the rest (or any later part) goes through (a) and (b); (d) a leading maker word without a
     * separator (`Sony Playstation 2`). The first step that finds anything decides; several
     * systems at that step make it [FolderMatch.Ambiguous].
     */
    private fun matchWholeName(name: String): FolderMatch? {
        val key = normalizeFolderName(name)
        lookup(key)?.let { return it }
        val parts = name.split(SEPARATOR).map { normalizeFolderName(it) }.filter { it.isNotEmpty() }
        // A spaced dash is a "Maker - Rest" separator; an underscore only counts after a known maker,
        // so "Ultimate_Doom" inside a DOS folder is not read as two parts.
        val separated = parts.size > 1 && (DASH.containsMatchIn(name) || parts[0] in MAKERS)
        if (separated) {
            // "Arcade - MAME 2003 Plus" and "Arcade - MAME" are the arcade system, not the MAME one.
            if (parts[0] == ARCADE_ID && parts.drop(1).joinToString("") in ARCADE_FAMILY) arcade?.let { return FolderMatch.System(it) }
            lookup(parts.drop(1).joinToString(""))?.let { return it }
            // Parts naming different systems ("Sony - PSP - PS2") are ambiguous, not a pick of the first.
            val found = parts.drop(1).mapNotNull { lookup(it) }.flatMap { it.rulesOf() }.distinct()
            matchOf(found)?.let { return it }
        }
        return lookupWithoutMaker(key)
    }

    private fun lookup(key: String): FolderMatch? =
        matchOf(byFolder[key]) ?: matchOf(byName[key])
            ?: if (key in ARCADE_FAMILY) arcade?.let { FolderMatch.System(it) } else null

    private fun lookupWithoutMaker(key: String): FolderMatch? {
        for (maker in MAKERS) {
            if (key.length > maker.length && key.startsWith(maker)) lookup(key.substring(maker.length))?.let { return it }
        }
        return null
    }

    private fun FolderMatch.rulesOf(): List<SystemRule> = when (this) {
        is FolderMatch.System -> listOf(rule)
        is FolderMatch.Ambiguous -> rules
        is FolderMatch.Path -> candidates.flatMap { it.rulesOf() }.distinct()
        FolderMatch.Unknown -> emptyList()
    }

    private fun matchOf(rules: List<SystemRule>?): FolderMatch? = when {
        rules.isNullOrEmpty() -> null
        rules.size == 1 -> FolderMatch.System(rules.first())
        else -> FolderMatch.Ambiguous(rules)
    }

    /**
     * The match for a directory: every folder on its path whose name is known, nearest first, so
     * `snes/hacks/` is still SNES and `Arcade/Neo Geo/` is Neo Geo; with several, a
     * [FolderMatch.Path] lets each file pick (`SNES/Doom/Doom.sfc` is SNES). [rootName] is the
     * picked folder itself, which may be `snes`. A folder known only by its bracket tag (`Bust-A-Move
     * 4 (Arcade)`, `Pokemon (GB)`) counts only when no folder above it is known in any way: inside
     * `Sony PlayStation (PS)/` or `PS1/` a game's own folder must not move the game to another system.
     */
    fun matchPath(rootName: String, relativePath: String): FolderMatch = matchPath(rootName, relativePath, emptyMap())

    /**
     * [matchPath] with the person's answers for folders whose name matches no system (Settings ›
     * Library › Unknown folder): [chosen] maps a path below the picked folder (`Xbox 360`) to the
     * system its folder stands for, as if its name were that system's, so a known folder below it
     * still wins for its own files (`Consoles/SNES/` stays SNES under `Consoles` chosen as Xbox).
     * A file the chosen system does not take goes by its extension alone, as before the choice: a
     * `.gba` beside the `.iso` files of a folder chosen as PlayStation 2 stays a Game Boy Advance
     * game rather than being dropped (with its favourite and play time).
     */
    fun matchPath(rootName: String, relativePath: String, chosen: Map<String, SystemRule>): FolderMatch {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        val names = listOf(rootName) + segments
        val prefixes = listOf("") + segments.indices.map { segments.subList(0, it + 1).joinToString("/") }
        var isChosen = false
        val found = names.indices.map { i ->
            val rule = if (i == 0 || chosen.isEmpty()) null else chosen[prefixes[i]]
            if (rule != null) isChosen = true
            if (rule != null) NamedMatch(FolderMatch.System(rule), byBracket = false) else matchFolderName(names[i])
        }.asReversed()
        val known = found.indices
            .filter { i -> found[i].match != FolderMatch.Unknown }
            .filter { i -> !found[i].byBracket || (i + 1 until found.size).all { found[it].match == FolderMatch.Unknown } }
            .map { found[it].match }
            .distinct()
        val candidates = if (isChosen) known + FolderMatch.Unknown else known
        return when (candidates.size) {
            0 -> FolderMatch.Unknown
            1 -> candidates.single()
            else -> FolderMatch.Path(candidates)
        }
    }

    /** The rule of system [id], or null when the platform data has no such system (any more). */
    fun rule(id: String): SystemRule? = byId[id]

    /**
     * A file some system could open by its extension (archives included): what an unknown folder's
     * "N files" counts. Junk (`._*`, saves) is the caller's to leave out first (`JunkFilter`).
     */
    fun isCandidateFile(fileName: String): Boolean = extensionOf(fileName)?.let { it in byExtension } == true

    fun matchFile(folder: FolderMatch, fileName: String): FileMatch {
        if (folder is FolderMatch.Path) {
            for (candidate in folder.candidates) {
                matchFile(candidate, fileName).let { if (it is FileMatch.Matched) return it }
            }
            return matchFile(folder.candidates.first(), fileName)
        }
        val extension = extensionOf(fileName) ?: return FileMatch.UnknownExtension
        val claimants = byExtension[extension].orEmpty()
        val inFolder = when (folder) {
            is FolderMatch.System -> claimants.firstOrNull { it == folder.rule }
            is FolderMatch.Ambiguous -> folder.rules.firstOrNull { it in claimants }
            FolderMatch.Unknown, is FolderMatch.Path -> null
        }
        if (inFolder != null) return FileMatch.Matched(inFolder.id)
        val claimant = claimants.singleOrNull()
        return when {
            claimants.isEmpty() -> FileMatch.UnknownExtension
            folder == FolderMatch.Unknown -> byExtensionAlone[extension]?.let { FileMatch.Matched(it.id) } ?: FileMatch.AmbiguousExtension
            // The folder's system does not claim this extension. Only a close kin of the folder's
            // system keeps the file (a .gba in "Nintendo - Game Boy"); a .doc or .avi in a DOS
            // folder is not a game of the one system that happens to list that extension.
            claimant != null && folder.rulesOf().any { it.emulators.intersect(claimant.emulators).isNotEmpty() } ->
                FileMatch.Matched(claimant.id)
            else -> FileMatch.UnknownExtension
        }
    }

    internal companion object {
        private const val ARCADE_ID = "arcade"

        /** `01 - SNES`, `1. NES`, `10. PSP`, `05_N64`, `3) DS`: up to three digits, then `.`, `)`, `_` or a spaced dash. */
        private val LIST_NUMBER = Regex("\\s*\\d{1,3}(?:[.)]\\s*|\\s*_\\s*|\\s+[-\u2013\u2014]\\s+)(.+)")

        /** `3 Saturn`: up to three digits and a space, tried after the whole name (`3 DS` is 3DS). */
        private val SPACED_NUMBER = Regex("\\s*\\d{1,3}\\s+(.+)")

        /** `SNES (Hacks) (Favorites)`: the words before and every trailing bracket group. */
        private val BRACKET_GROUPS = Regex("(.*?)((?:\\s*\\([^()]+\\))+)\\s*")

        /**
         * Bracket words (normalized) that say what a person keeps in a system folder, not which
         * game it is: `SNES (Hacks)`, `GBA (Translations)`, `NES (Aftermarket)`. Never a region,
         * language or disc: `Doom (Europe)` is a game folder.
         */
        private val FOLDER_QUALIFIERS = setOf(
            "hacks", "hack", "romhacks", "translations", "translated", "english", "homebrew", "unlicensed", "unl",
            "aftermarket", "prototypes", "protos", "betas", "demos", "msu", "msu1", "favorites", "favourites",
            "collection", "complete", "fullset",
        )

        /** Archives any system's emulator may open: never a system on their own. */
        private val CONTAINERS = setOf("zip", "7z", "rar")

        /** `Super Nintendo (SFC)`: the words before and the last bracket group. */
        private val BRACKET_TAG = Regex("(.*?)\\s*\\(([^()]+)\\)\\s*")

        /**
         * Where a file goes when its folder names no system (files right in the picked folder):
         * (1) the only system that claims the extension; (2) else the only claimant the extension
         * names, by id or folder alias (`.sfc` is the system people also call `sfc`, `.nes` is NES
         * not Famicom Disk System, `.gb` is Game Boy, not Super Game Boy); (3) else the only
         * claimant every other claimant is a variant of: the variant's id starts with its id, they
         * share an emulator and it claims extensions the variant does not (`.z64`: N64, not 64DD;
         * `.prx`: PSP, not PSP Minis). The id test keeps out systems that only share an emulator
         * (Amstrad CPC tapes are not GX4000 games, a `.bat` is not a DOS game); the last keeps out
         * pairs with the same extensions (`.ngc`: NGP or NGP Color). Archives (`.zip`, `.7z`,
         * `.rar`) and anything else (`.bin`, `.iso`, `.chd`, `.32x`) stay unmatched rather than guessed.
         */
        private fun soleClaimant(extension: String, claimants: List<SystemRule>): SystemRule? {
            if (extension in CONTAINERS) return null
            claimants.singleOrNull()?.let { return it }
            claimants.singleOrNull { rule -> normalizeFolderName(rule.id) == extension || rule.folders.any { normalizeFolderName(it) == extension } }
                ?.let { return it }
            val extensionsOf = claimants.associateWith { rule -> rule.extensions.mapTo(HashSet()) { it.removePrefix(".").lowercase(Locale.ROOT) } }
            return claimants.singleOrNull { main ->
                claimants.all { other ->
                    other == main ||
                        (
                            normalizeFolderName(other.id).startsWith(normalizeFolderName(main.id)) &&
                                other.emulators.intersect(main.emulators).isNotEmpty() &&
                                !extensionsOf.getValue(other).containsAll(extensionsOf.getValue(main))
                            )
                }
            }
        }

        /** Separators of `Maker - Rest`: a spaced dash, en or em dash, or an underscore. */
        private val SEPARATOR = Regex("\\s+[-\u2013\u2014]\\s+|_")
        private val DASH = Regex("\\s+[-\u2013\u2014]\\s+")

        /** Leading maker words (normalized); the same list as MAKER_PREFIXES and DISPLAY_MAKER_PREFIXES in tools/platforms/tables.py. */
        private val MAKERS = listOf(
            "nintendo", "sony", "sega", "microsoft", "bandai", "coleco", "mattel", "magnavox", "nec", "snk",
            "philips", "panasonic", "emerson", "atari", "commodore", "apple",
        ).sortedByDescending { it.length }

        /** Normalized names of arcade emulators and romset families that mean the generic arcade system. */
        private val ARCADE_FAMILY = setOf(
            "mame", "mame2000", "mame2003", "mame2003plus", "mame2010", "mame2015", "mame2016",
            "fbneo", "finalburnneo", "fbalpha", "finalburnalpha", "fba",
        )

        private fun index(rules: List<SystemRule>, names: (SystemRule) -> Set<String>): Map<String, List<SystemRule>> =
            rules.flatMap { rule -> names(rule).map { normalizeFolderName(it) to rule } }
                .filter { it.first.isNotEmpty() }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, list) -> list.distinct() }

        fun normalizeFolderName(name: String): String =
            name.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }

        fun extensionOf(fileName: String): String? {
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) return null
            return fileName.substring(dot + 1).lowercase(Locale.ROOT)
        }
    }
}
