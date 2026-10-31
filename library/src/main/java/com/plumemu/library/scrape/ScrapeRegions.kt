package com.plumemu.library.scrape

import com.plumemu.identify.name.Region
import java.util.Locale

/**
 * The region and language order of a ScreenScraper request: the game file's own regions first, then the region chosen in Settings (USA by
 * default), then `wor` and `ss`; the scraper falls back to any region after these.
 */
internal object ScrapeRegions {
    const val DEFAULT_PREFERRED = "us"

    /**
     * ScreenScraper's region codes for one of Plumemu's regions, most fitting first. A country
     * also gives its wider market (a French release usually carries the European box), so its
     * own box wins when ScreenScraper has one and the market's box otherwise. A `when`, so a new
     * [Region] cannot be left unmapped.
     */
    fun codesOf(region: Region): List<String> = when (region) {
        Region.USA -> listOf("us")
        Region.EUROPE -> listOf("eu")
        Region.JAPAN -> listOf("jp")
        Region.WORLD -> listOf("wor")
        Region.KOREA -> listOf("kr")
        Region.CHINA -> listOf("cn")
        Region.TAIWAN -> listOf("tw", "asi")
        // ScreenScraper has no Hong Kong region (regionsListe.php).
        Region.HONG_KONG -> listOf("asi")
        Region.ASIA -> listOf("asi")
        Region.BRAZIL -> listOf("br")
        Region.AUSTRALIA -> listOf("au", "eu")
        Region.CANADA -> listOf("ca", "us")
        Region.UK -> listOf("uk", "eu")
        Region.FRANCE -> listOf("fr", "eu")
        Region.GERMANY -> listOf("de", "eu")
        // ScreenScraper writes Spain "sp"; "es" is its Spanish language code.
        Region.SPAIN -> listOf("sp", "eu")
        Region.ITALY -> listOf("it", "eu")
        Region.NETHERLANDS -> listOf("nl", "eu")
        Region.SWEDEN -> listOf("se", "eu")
        Region.SCANDINAVIA -> listOf("se", "dk", "no", "fi", "eu")
        Region.RUSSIA -> listOf("ru")
    }

    /**
     * [shownRegions] is `game.regions` (Plumemu codes in file order, `USA,EUR`); codes this
     * version does not know are skipped. [preferred] is the Settings value; blank means USA.
     */
    fun order(shownRegions: String, preferred: String): List<String> {
        val own = shownRegions.split(',').mapNotNull { Region.fromCode(it.trim()) }.flatMap(::codesOf)
        val chosen = preferred.trim().lowercase(Locale.ROOT).ifEmpty { DEFAULT_PREFERRED }
        return (own + chosen + "wor" + "ss").distinct()
    }

    /** The device's language (`fr`, `ja`), then English. */
    fun languages(deviceLanguage: String): List<String> =
        (listOf(deviceLanguage.trim().lowercase(Locale.ROOT)) + "en").filter { it.isNotEmpty() }.distinct()
}
