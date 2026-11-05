package com.plumemu.identify.roundtrip

import java.text.Normalizer

/** One file name people could have for a title, tagged with how it was made. */
internal class Variant(val kind: String, val fileName: String)

/**
 * The file names people really use for a pack title, one per kind that applies (a kind that would
 * leave the name unchanged is left out). Deterministic: same title and region, same names.
 */
internal object NameVariants {
    /** Every kind, in report order. */
    val KINDS = listOf(
        "exact", "no-region", "lower", "upper", "underscores", "dots", "no-space", "no-space-lower",
        "dash-dropped", "colon-to-dash", "dash-to-colon", "the-moved", "roman-to-digit", "digit-to-roman",
        "ampersand-and", "ampersand-dropped", "accents-dropped", "site-suffix", "site-prefix", "goodtools", "rev-tag",
    )

    private const val EXT = ".zip"
    private val romans = mapOf("II" to 2, "III" to 3, "IV" to 4, "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "XI" to 11, "XII" to 12,
        "XIII" to 13, "XIV" to 14, "XV" to 15, "XVI" to 16, "XVII" to 17, "XVIII" to 18, "XIX" to 19, "XX" to 20,
    )
    // No 5 or 10: a lone "V" / "X" is deliberately not a numeral to the matcher.
    private val romanOf = romans.entries.associate { (k, v) -> v to k }
    private val goodTools = mapOf("USA" to "U", "Europe" to "E", "Japan" to "J", "World" to "W", "Korea" to "K", "Brazil" to "B")
    private val sceneRegion = mapOf("USA" to "USA", "Europe" to "EUR", "Japan" to "JPN")
    private val marks = Regex("\\p{M}+")

    /** [regionName] is a No-Intro region name the title was released in ("USA" when it names none). */
    fun of(title: String, regionName: String): List<Variant> {
        if ('/' in title || '\\' in title) return emptyList()
        val out = ArrayList<Variant>(24)
        val tag = " ($regionName)"
        fun add(kind: String, base: String) {
            if (base.isNotBlank()) out += Variant(kind, base + EXT)
        }
        fun addIfChanged(kind: String, changed: String?, suffix: String = tag) {
            if (changed != null && changed != title) add(kind, changed + suffix)
        }
        add("exact", title + tag)
        add("no-region", title)
        addIfChanged("lower", title.lowercase())
        addIfChanged("upper", title.uppercase())
        if (' ' in title) {
            add("underscores", (title + tag).replace(' ', '_'))
            add("dots", title.replace(' ', '.') + "." + (sceneRegion[regionName] ?: "USA") + "-GRP")
            addIfChanged("no-space", title.replace(" ", ""))
            addIfChanged("no-space-lower", title.replace(" ", "").lowercase())
        }
        if (" - " in title) {
            addIfChanged("dash-dropped", title.replace(" - ", " "))
            addIfChanged("dash-to-colon", title.replace(" - ", ": "))
        }
        if (": " in title) addIfChanged("colon-to-dash", title.replace(": ", " - "))
        addIfChanged("the-moved", theMoved(title))
        addIfChanged("roman-to-digit", mapWords(title) { w -> romans[w]?.toString() })
        addIfChanged("digit-to-roman", mapWords(title) { w -> w.toIntOrNull()?.takeIf { w == it.toString() }?.let { romanOf[it] } })
        if (" & " in title) {
            addIfChanged("ampersand-and", title.replace(" & ", " and "))
            addIfChanged("ampersand-dropped", title.replace(" & ", " "))
        }
        addIfChanged("accents-dropped", Normalizer.normalize(title, Normalizer.Form.NFD).replace(marks, ""))
        add("site-suffix", "$title$tag-romsfun")
        add("site-prefix", "emulatorgames.net - $title$tag")
        add("goodtools", "$title (${goodTools[regionName] ?: "U"}) [!]")
        add("rev-tag", "$title$tag (Rev 1)")
        return out
    }

    /** "The Simpsons - Bart" -> "Simpsons, The - Bart"; "Simpsons, The - Bart" -> "The Simpsons - Bart"; else null. */
    fun theMoved(title: String): String? {
        val dash = title.indexOf(" - ").let { if (it < 0) title.length else it }
        val head = title.substring(0, dash)
        val tail = title.substring(dash)
        return when {
            head.startsWith("The ") && head.length > 4 -> head.removePrefix("The ") + ", The" + tail
            head.endsWith(", The") -> "The " + head.removeSuffix(", The") + tail
            else -> null
        }
    }

    private fun mapWords(title: String, f: (String) -> String?): String? {
        var changed = false
        val words = title.split(' ').map { w ->
            val r = f(w)
            if (r != null) changed = true
            r ?: w
        }
        return if (changed) words.joinToString(" ") else null
    }
}
