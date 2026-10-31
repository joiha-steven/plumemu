package com.plumemu.identify.name

import java.util.Locale

/**
 * A release region read from a file name, shown as its [code] (USA, EUR, JPN, ...).
 *
 * @property noIntroName the region as No-Intro and Redump write it ("Europe", "Hong Kong"), which
 *   is also how name packs spell it (`PackMasks.REGIONS`, the first 21 entries in this order);
 *   [parseGroup] reads it back.
 */
enum class Region(val code: String, val noIntroName: String) {
    USA("USA", "USA"),
    EUROPE("EUR", "Europe"),
    JAPAN("JPN", "Japan"),
    WORLD("WLD", "World"),
    KOREA("KOR", "Korea"),
    CHINA("CHN", "China"),
    TAIWAN("TWN", "Taiwan"),
    HONG_KONG("HKG", "Hong Kong"),
    ASIA("ASI", "Asia"),
    BRAZIL("BRA", "Brazil"),
    AUSTRALIA("AUS", "Australia"),
    CANADA("CAN", "Canada"),
    UK("GBR", "UK"),
    FRANCE("FRA", "France"),
    GERMANY("DEU", "Germany"),
    SPAIN("ESP", "Spain"),
    ITALY("ITA", "Italy"),
    NETHERLANDS("NLD", "Netherlands"),
    SWEDEN("SWE", "Sweden"),
    SCANDINAVIA("SCN", "Scandinavia"),
    RUSSIA("RUS", "Russia"),
    ;

    companion object {
        /** Region names as No-Intro and Redump write them, and the usual short forms. */
        private val byName: Map<String, Region> = mapOf(
            "usa" to USA, "us" to USA, "america" to USA,
            "europe" to EUROPE, "eu" to EUROPE, "eur" to EUROPE,
            "japan" to JAPAN, "jp" to JAPAN, "jpn" to JAPAN,
            "world" to WORLD,
            "korea" to KOREA, "china" to CHINA, "taiwan" to TAIWAN, "hong kong" to HONG_KONG,
            "asia" to ASIA, "brazil" to BRAZIL, "australia" to AUSTRALIA, "canada" to CANADA,
            "uk" to UK, "united kingdom" to UK, "france" to FRANCE, "germany" to GERMANY,
            "spain" to SPAIN, "italy" to ITALY, "netherlands" to NETHERLANDS, "sweden" to SWEDEN,
            "scandinavia" to SCANDINAVIA, "russia" to RUSSIA,
        )

        /** GoodTools single-letter codes, which may be joined: `(U)`, `(JU)`, `(JUE)`. */
        private val byLetter: Map<Char, Region> = mapOf(
            'U' to USA, 'E' to EUROPE, 'J' to JAPAN, 'W' to WORLD, 'K' to KOREA,
            'B' to BRAZIL, 'A' to AUSTRALIA, 'F' to FRANCE, 'G' to GERMANY, 'S' to SPAIN,
            'I' to ITALY, 'C' to CHINA,
        )

        fun fromCode(code: String): Region? = entries.firstOrNull { it.code == code }

        /**
         * The regions of region names as name packs write them ("USA", "Hong Kong"), in their
         * order; names this enum does not know ("Greece", "Latin America") are dropped.
         */
        fun fromNoIntroNames(names: List<String>): List<Region> =
            names.mapNotNull { name -> entries.firstOrNull { it.noIntroName.equals(name.trim(), ignoreCase = true) } }.distinct()

        /**
         * The regions in one bracket group (`USA, Europe`, `JU`), or null when the group is not a
         * region group (`Rev 1`, `En,Fr,De`).
         */
        fun parseGroup(content: String): List<Region>? {
            parseNamed(content)?.let { return it }
            val letters = content.trim()
            if (letters.length in 1..4 && letters.all { it in byLetter }) {
                return letters.mapNotNull { byLetter[it] }.distinct()
            }
            return null
        }

        /**
         * The regions of region names only (`USA, Europe`, `US`, `EUR`), without GoodTools letters:
         * for groups and words where a lone letter is not a region (`[US]` in a Switch name, `EUR`
         * in a scene name). Null when any part is not a region name.
         */
        fun parseNamed(content: String): List<Region>? {
            val tokens = content.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            val named = tokens.map { byName[it.lowercase(Locale.ROOT)] }
            return if (named.all { it != null }) named.filterNotNull().distinct() else null
        }
    }
}
