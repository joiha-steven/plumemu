package com.plumemu.identify.name

import java.util.Locale

/**
 * Tags a release carries that say nothing about which game it is: version and update codes,
 * Switch title ids and content kinds, sizes, and the tail of a scene release name. They are left
 * out of the title and out of [CleanTitle.variantKey], so an update or a re-dump is a copy of the
 * same game, not a second game.
 */
internal object ReleaseTags {
    /**
     * One bracket group that is only a version or update code: `v1.1`, `v131072`, `1.0.0`, `UPD`,
     * `Update 1.2`, `Base`, `DLC`, `2DLC`, `eShop`, `1.5 GB`, a Switch title id (16 hex digits), or
     * several of them joined by `+` (`v1.3.0+2DLC`). `Rev 1` is not one: a revision stays a variant.
     */
    private val versionPart = Regex(
        """v\d+(?:\.\d+)*|\d+(?:\.\d+)+|upd|update(?:\s*v?\d+(?:\.\d+)*)?|base|\d*\s*dlc|eshop|""" +
            """\d+(?:\.\d+)?\s*[gmk]i?b|01[0-9a-f]{14}""",
        RegexOption.IGNORE_CASE,
    )

    /** A lone number in square brackets (`[0]`, a Switch key generation) is a code; in round brackets it may be a title's. */
    private val squareNumber = Regex("""\d+""")

    /** True when a bracket group's [content] is only version or update codes. [square]: the group was `[...]`. */
    fun isVersionOrUpdate(content: String, square: Boolean): Boolean {
        val text = content.trim()
        if (text.isEmpty()) return false
        if (square && squareNumber.matches(text)) return true
        return text.split('+').all { versionPart.matches(it.trim()) }
    }

    /** A trailing version outside brackets: ` v1.3`, ` v1.0.2`. A dot is required: "Dreamcast V40" is a title. */
    val trailingVersion = Regex("""\s+[vV]\d+(?:\.\d+)+$""")

    /**
     * Scene platform tags: the last word before `-GROUP` in `Title.Words.USA.PSP-GROUP`, lower case.
     * A name is read as a scene release only when its tail holds one of these, a video standard or
     * a region (see [isSceneTail]).
     */
    private val PLATFORMS = setOf(
        "psp", "ps2dvd", "ps2cd", "ps2", "psx", "ps1", "nds", "gba", "gbc", "gb", "xbox", "x360", "xbox360",
        "wii", "wiiu", "ngc", "gc", "gcn", "dc", "dvd5", "dvd9", "n64", "snes", "nes", "3ds", "nsw", "ps3", "psv",
    )

    /** Scene words that are neither title nor region: video standard, languages count, release fixes. */
    private val FLAGS = setOf(
        "ntsc", "pal", "pal60", "multi", "internal", "proper", "repack", "rerip", "readnfo",
        "dirfix", "nfofix", "dupe", "iso", "cso", "rip", "dlc", "update", "upd",
    )
    private val multiLanguage = Regex("""multi\d+""")

    /**
     * True for a word a scene tail may hold: a platform tag, a flag, `MULTI5`, or a region code
     * (USA, EUR, FRA; [SCENE_REGIONS]). The word before `-GROUP` must be one for the name to be read as a scene name.
     */
    fun isSceneTail(word: String): Boolean = word.lowercase(Locale.ROOT).let {
        it in PLATFORMS || it in FLAGS || multiLanguage.matches(it) || it in SCENE_REGIONS
    }

    /** True for a platform tag ("PSP", "PS2DVD", "NGC"): the last word of a scene tail, only there. */
    fun isScenePlatform(word: String): Boolean = word.lowercase(Locale.ROOT) in PLATFORMS

    /**
     * Region codes scene names use, lower case, with the region each stands for (null: a code
     * with no [Region] of its own, dropped from the title all the same). Codes only: region
     * NAMES ("World", "Korea", "America") are words of titles ("Animal Crossing Wild World").
     */
    private val SCENE_REGIONS: Map<String, Region?> = mapOf(
        "usa" to Region.USA, "us" to Region.USA, "ntscu" to Region.USA,
        "eur" to Region.EUROPE, "eu" to Region.EUROPE,
        "jpn" to Region.JAPAN, "jp" to Region.JAPAN, "jap" to Region.JAPAN, "ntscj" to Region.JAPAN,
        "uk" to Region.UK, "ger" to Region.GERMANY, "deu" to Region.GERMANY, "fra" to Region.FRANCE,
        "ita" to Region.ITALY, "spa" to Region.SPAIN, "esp" to Region.SPAIN, "nl" to Region.NETHERLANDS,
        "ned" to Region.NETHERLANDS, "kor" to Region.KOREA, "chn" to Region.CHINA, "aus" to Region.AUSTRALIA,
        "swe" to Region.SWEDEN, "rus" to Region.RUSSIA, "nor" to null, "den" to null, "fin" to null, "pol" to null,
    )

    /** The regions of a scene region code ("USA", "EUR", "FRA"); null for anything else. */
    fun sceneRegion(word: String): List<Region>? = SCENE_REGIONS[word.lowercase(Locale.ROOT)]?.let { listOf(it) }
}
