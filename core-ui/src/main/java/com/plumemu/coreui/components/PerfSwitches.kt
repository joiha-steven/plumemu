package com.plumemu.coreui.components

/**
 * Switches that turn one family of drawing off, so a benchmark run can measure what each costs. All are off (nothing skipped) unless
 * a benchmark build sets them from its launch extra ([set]); a normal build never calls it.
 */
object PerfSwitches {
    /** Slab glow and blob, cover halos, collar glows. */
    @Volatile var noGlow = false

    /** The collar's lip, well and shadow. */
    @Volatile var noCollar = false

    /** The game menu's row text bands. */
    @Volatile var noBands = false

    /** Reads a comma list of `glow`, `collar`, `bands`; anything else is ignored. */
    fun set(csv: String?) {
        val names = csv.orEmpty().split(',').map { it.trim() }.toSet()
        noGlow = "glow" in names
        noCollar = "collar" in names
        noBands = "bands" in names
    }
}
