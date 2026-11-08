package com.plumemu.coreui.theme

/**
 * The tone ladder (docs/design/rules.md > Tone ladder): every tone a screen lays on its page is the page itself moved a fixed step of CIE L*,
 * keeping the page's OKLCH hue and chroma, on dark and light pages alike: [SUNKEN] wells and tracks, the page, [SURFACE] cards and trays,
 * [RAISED] raised cards, [TOP] key caps on a tray; dividers [LINE] toward the text. One rule, so a blue page gets blue cards and a cream page
 * cream ones, never a grey or a white laid over it.
 */
object ToneLadder {
    const val SUNKEN = -4.0
    const val SURFACE = 3.0
    const val RAISED = 6.0
    const val TOP = 8.0
    const val LINE = 10.0

    /**
     * [bg] moved [step] of L* (up is lighter), its OKLCH hue and chroma kept as far as sRGB allows. Remembered per page and step: a
     * colour derivation asks for the same few tones again and again (a readability check derives once per text candidate, an accent
     * by system re-derives on every system), and one search costs about a millisecond on a slow handheld.
     */
    fun level(
        bg: Rgb,
        step: Double,
    ): Rgb {
        val key = Key(bg, step)
        synchronized(remembered) { remembered[key] }?.let { return it }
        return search(bg, step).also { synchronized(remembered) { remembered[key] = it } }
    }

    private data class Key(
        val bg: Rgb,
        val step: Double,
    )

    private val remembered =
        object : LinkedHashMap<Key, Rgb>(REMEMBERED + 1, 1f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Rgb>) = size > REMEMBERED
        }

    private fun search(
        bg: Rgb,
        step: Double,
    ): Rgb {
        val target = (ColorMath.lightness(bg) + step).coerceIn(0.0, 100.0)
        val page = Oklch.of(bg)
        var lo = 0.0
        var hi = 1.0
        repeat(SEARCH_STEPS) {
            val m = (lo + hi) / 2
            if (ColorMath.lightness(page.copy(l = m).toRgb()) < target) lo = m else hi = m
        }
        return page.copy(l = (lo + hi) / 2).toRgb()
    }

    /** A divider on [bg]: [LINE] toward [text]. */
    fun line(
        bg: Rgb,
        text: Rgb,
    ): Rgb = level(bg, if (ColorMath.lightness(text) > ColorMath.lightness(bg)) LINE else -LINE)

    private const val SEARCH_STEPS = 30
    private const val REMEMBERED = 64
}
