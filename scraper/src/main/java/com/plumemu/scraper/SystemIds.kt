package com.plumemu.scraper

/**
 * Plumemu system id (platforms.json) to ScreenScraper `systemeid`.
 *
 * Plain facts about ScreenScraper's numbering, written by hand; no code from another project.
 * Every row checked by id and name against `systemesListe.php`; the list it gave is
 * the test resource `screenscraper/systems.tsv`, which `SystemIdsTest` checks every id against.
 * A system missing here is scraped by hash without a system id, and never by name. A system in
 * [isNameOnly] is scraped by name only.
 */
internal object SystemIds {
    private val SCREENSCRAPER_IDS: Map<String, Int> = mapOf(
        "megadrive" to 1,
        "mastersystem" to 2,
        "nes" to 3,
        "snes" to 4,
        "gb" to 9,
        "gbc" to 10,
        "virtualboy" to 11,
        "gba" to 12,
        "gc" to 13,
        "n64" to 14,
        "nds" to 15,
        "wii" to 16,
        "n3ds" to 17,
        "sega32x" to 19,
        "megacd" to 20,
        "gamegear" to 21,
        "saturn" to 22,
        "dreamcast" to 23,
        "ngp" to 25,
        "atari2600" to 26,
        "atarilynx" to 28,
        "pcengine" to 31,
        "atari7800" to 41,
        "wonderswan" to 45,
        "wonderswancolor" to 46,
        "psx" to 57,
        "ps2" to 58,
        "psp" to 61,
        // ScreenScraper files every arcade set under one "Arcade" system.
        "arcade" to 75,
        "mame" to 75,
        "fba" to 75,
        "fbneo" to 75,
        "ngpc" to 82,
        "pcenginecd" to 114,
        "neogeo" to 142,
        // Added from systemesListe.php: systems with one exact ScreenScraper id.
        // Several Plumemu systems share an id where ScreenScraper files them together (msx and
        // msx1, dos and pc, odyssey2 and videopac; ndsi under Nintendo DS, tg-cd under PC Engine CD).
        "3do" to 29,
        "adam" to 89,
        "amiga" to 64,
        "amigacd32" to 130,
        "amstradcpc" to 65,
        "apple2" to 86,
        "arcadia" to 94,
        "arduboy" to 263,
        "atari5200" to 40,
        "atari800" to 43,
        "atarijaguar" to 27,
        "atarijaguarcd" to 171,
        "atarist" to 42,
        "atomiswave" to 53,
        "bbcmicro" to 37,
        "c64" to 66,
        "cdimono1" to 133,
        "cdtv" to 129,
        "channelf" to 80,
        "colecovision" to 48,
        "cps1" to 6,
        "cps2" to 7,
        "cps3" to 8,
        "crvision" to 241,
        "daphne" to 49,
        "dos" to 135,
        "easyrpg" to 231,
        "fds" to 106,
        "gameandwatch" to 52,
        "gx4000" to 87,
        "intellivision" to 115,
        "j2me" to 302,
        "lowresnx" to 244,
        "lutro" to 206,
        "macintosh" to 146,
        "megaduck" to 90,
        "model2" to 54,
        "model3" to 55,
        "msx" to 113,
        "msx1" to 113,
        "msx2" to 116,
        "msxturbor" to 118,
        "n64dd" to 122,
        "naomi" to 56,
        "naomi2" to 230,
        "naomigd" to 227,
        "ndsi" to 15,
        "neogeocd" to 70,
        "ngage" to 30,
        "odyssey2" to 104,
        "palm" to 219,
        "pc" to 135,
        "pc88" to 221,
        "pc98" to 208,
        "pcfx" to 72,
        "pet" to 240,
        "pico" to 250,
        "pico8" to 234,
        "plus4" to 99,
        "pokemini" to 211,
        "ps3" to 59,
        "psvita" to 62,
        "samcoupe" to 213,
        "satellaview" to 107,
        "scummvm" to 123,
        "sg-1000" to 109,
        "sgb" to 127,
        "snesmsu1" to 210,
        "genesismsu" to 301,
        "spectravideo" to 218,
        "stv" to 69,
        "sufami" to 108,
        "supergrafx" to 105,
        "supervision" to 207,
        "switch" to 225,
        "tg-cd" to 114,
        "tic80" to 222,
        "to8" to 141,
        "type-x" to 112,
        "uzebox" to 216,
        "vc4000" to 281,
        "vectrex" to 102,
        "vic20" to 73,
        "videopac" to 104,
        "vircon32" to 272,
        "vsmile" to 120,
        "wasm4" to 262,
        "wiiu" to 18,
        "windows" to 138,
        // ScreenScraper has no Steam system; most Steam games are filed under PC Windows.
        "steam" to 138,
        "x1" to 220,
        "x68000" to 79,
        "xbox" to 32,
        "xbox360" to 33,
        "zmachine" to 215,
        "zx81" to 77,
        "zxspectrum" to 76,
    )

    /**
     * Systems whose files hold only a launch id (a `.steam` file holds a Steam app id): no hash or
     * file name of theirs means anything to ScreenScraper, so their games are looked up by name only.
     *
     * core-data mirrors this set in SQL (`PinnedShortcutDao.NOT_SKIPPED_BY_BOX_ART`: pinned
     * shortcuts of these systems are scraped); `SystemIdsTest` fails when it changes.
     */
    val NAME_ONLY: Set<String> = setOf("steam")

    fun isNameOnly(plumemuSystemId: String): Boolean = plumemuSystemId in NAME_ONLY

    /** ScreenScraper's `systemeid` for [plumemuSystemId], or null when it is not known. */
    fun screenScraperId(plumemuSystemId: String): Int? = SCREENSCRAPER_IDS[plumemuSystemId]

    /**
     * Whether a serial-only `jeuInfos` answer from ScreenScraper system [answered] may stand for a
     * game asked on [requested]. ScreenScraper matches a serial across systems (a 3DS game for a
     * SNES cartridge), so the system must agree; Game Boy (9) and Game Boy Color (10) are the one
     * declared pair (Mega Drive 1 and 32X 19 the other: a `.32x` in `megadrive/` reads a Mega Drive code), since it lists some dual titles under the other. [serialNative] is the system
     * of the format the serial was read from (a Game Boy code in a `gba/` folder, a SNES code in
     * `snesmsu1/`): an answer on it, or its pair, counts too. Nothing is
     * checked when [requested] is null (a system ScreenScraper has no id for).
     */
    fun serialAnswerAccepted(requested: Int?, answered: String?, serialNative: Int? = null): Boolean {
        if (requested == null) return true
        val id = answered?.trim()?.toIntOrNull() ?: return false
        return listOfNotNull(requested, serialNative).any { id == it || RELATED[it]?.contains(id) == true }
    }

    private val RELATED: Map<Int, Set<Int>> = mapOf(9 to setOf(10), 10 to setOf(9), 1 to setOf(19), 19 to setOf(1))

    val knownSystems: Set<String> get() = SCREENSCRAPER_IDS.keys
}
