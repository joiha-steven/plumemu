package com.plumemu.settings.screen

import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.HslColors
import com.plumemu.coreui.theme.Rgb
import kotlin.math.abs

/*
 * The three sliders under the "+" palette: Hue, Saturation and Lightness of the
 * colour shown. Pure arithmetic and the readable-values rule; the cursor and the writes are in
 * [SettingsCustomColor], the drawing in SettingsColorSliderRows.kt.
 */

/** One slider: its range and the size of a D-pad step (hue in degrees, the others in percent). */
internal enum class ColorSlider(
    val max: Int,
    val step: Int,
) {
    Hue(360, 6),
    Saturation(100, 4),
    Lightness(100, 4),
    ;

    /** The values a thumb can stop on from a D-pad step: 0, [step], 2 x [step] ... [max]. */
    val count: Int get() = max / step + 1
}

/** A colour as the sliders see it: hue 0..360 degrees, saturation and lightness 0..100 percent. */
internal data class Hsl(
    val hue: Int,
    val saturation: Int,
    val lightness: Int,
) {
    operator fun get(slider: ColorSlider): Int =
        when (slider) {
            ColorSlider.Hue -> hue
            ColorSlider.Saturation -> saturation
            ColorSlider.Lightness -> lightness
        }

    fun with(
        slider: ColorSlider,
        value: Int,
    ): Hsl =
        when (slider) {
            ColorSlider.Hue -> copy(hue = value)
            ColorSlider.Saturation -> copy(saturation = value)
            ColorSlider.Lightness -> copy(lightness = value)
        }

    fun rgb(): Rgb = HslColors.fromHsl(hue / DEGREES, saturation / PERCENT, lightness / PERCENT)

    companion object {
        private const val DEGREES = 360.0
        private const val PERCENT = 100.0

        fun of(rgb: Rgb): Hsl {
            val (h, s, l) = HslColors.toHsl(rgb)
            return Hsl(Math.round(h * DEGREES).toInt(), Math.round(s * PERCENT).toInt(), Math.round(l * PERCENT).toInt())
        }
    }
}

/**
 * Which values of a slider may be stopped on: those whose colour [reads] (a text or accent that
 * passes `Readability`, a background some text reads on). The other two values stay as they are.
 */
internal class SliderReach(
    private val reads: (Rgb) -> Boolean,
) {
    /**
     * The last [units] of each slider, by the other two values: a finger drag on one slider keeps them
     * the same on every move, so the track and the finger's search share one pass over the values
     * (361 readability checks for Hue took 1 ms of every frame on the Retroid Pocket Classic).
     */
    private val lastUnits = arrayOfNulls<Pair<Hsl, BooleanArray>>(ColorSlider.entries.size)

    fun reaches(
        at: Hsl,
        slider: ColorSlider,
        value: Int,
    ): Boolean = reads(at.with(slider, value).rgb())

    /** For each of [ColorSlider.count] values, whether it can be stopped on. */
    fun cells(
        at: Hsl,
        slider: ColorSlider,
    ): BooleanArray = BooleanArray(slider.count) { reaches(at, slider, it * slider.step) }

    /** For each whole unit 0..[ColorSlider.max] of the slider, whether it can be stopped on (what a finger can land on). */
    fun units(
        at: Hsl,
        slider: ColorSlider,
    ): BooleanArray {
        val others = at.with(slider, 0)
        lastUnits[slider.ordinal]?.takeIf { it.first == others }?.let { return it.second }
        val units = BooleanArray(slider.max + 1) { reaches(at, slider, it) }
        lastUnits[slider.ordinal] = others to units
        return units
    }

    /**
     * The runs of whole values a finger can land on, as the track draws them: from the first to the
     * last value of each run (never at the D-pad's coarser step, so no value is drawn that cannot be
     * stopped on, nor stopped on where nothing is drawn).
     */
    fun drawnStretches(
        at: Hsl,
        slider: ColorSlider,
    ): List<IntRange> {
        val units = units(at, slider)
        val runs = mutableListOf<IntRange>()
        var start = 0
        while (start < units.size) {
            if (!units[start]) {
                start++
                continue
            }
            var end = start
            while (end + 1 < units.size && units[end + 1]) end++
            runs += start..end
            start = end + 1
        }
        return runs
    }

    /** The next step in [direction] (+1 or -1) from the thumb that can be stopped on, skipping the ones that cannot; null at the end. */
    fun stepped(
        at: Hsl,
        slider: ColorSlider,
        direction: Int,
    ): Hsl? {
        val now = at[slider]
        val first = if (direction > 0) Math.floorDiv(now, slider.step) + 1 else -Math.floorDiv(-now, slider.step) - 1
        var index = first
        while (index in 0 until slider.count) {
            val value = index * slider.step
            if (reaches(at, slider, value)) return at.with(slider, value)
            index += direction
        }
        return null
    }

    /**
     * The value at [fraction] (0..1) of the track, in whole units of the slider (hue 1 degree, the
     * others 1 percent: [ColorSlider.max] is the unit count), moved to the nearest one that can be
     * stopped on; null when none can. A finger uses this fine grain; the D-pad's [ColorSlider.step]
     * is only for [stepped] and for the stretches drawn from [cells].
     */
    fun nearest(
        at: Hsl,
        slider: ColorSlider,
        fraction: Float,
    ): Hsl? {
        val target = fraction.coerceIn(0f, 1f) * slider.max
        val centre = Math.round(target).coerceIn(0, slider.max)
        val units = units(at, slider)
        // Outwards from the finger; of the two values at one distance the one nearer the finger's exact place goes first.
        for (distance in 0..slider.max) {
            val below = centre - distance
            val above = centre + distance
            val first = if (abs(below - target) <= abs(above - target)) below else above
            val second = if (first == below) above else below
            if (first in 0..slider.max && units[first]) return at.with(slider, first)
            if (second != first && second in 0..slider.max && units[second]) return at.with(slider, second)
        }
        return null
    }
}

/**
 * Where the cursor goes from [index] of a custom page with [swatches] swatches: the swatch grid, then Down
 * to Hue, Saturation, Lightness and the hex row (Up goes back). Left and Right on a slider are not moves
 * (they step it). Same [index] when there is nowhere to go.
 */
internal fun nextCustomIndex(
    swatches: Int,
    index: Int,
    direction: Direction,
): Int {
    val hexRow = swatches + ColorSlider.entries.size
    if (index < swatches) {
        val moved = PanelGrid(swatches, ColorPages.PALETTE_COLUMNS).move(index, direction)
        return if (moved == index && direction == Direction.Down) swatches else moved
    }
    return when (direction) {
        // Up from Hue lands on the last row's first swatch, as Up from the hex row did.
        Direction.Up -> if (index == swatches) lastRowStart(swatches) else index - 1
        Direction.Down -> if (index < hexRow) index + 1 else index
        else -> index
    }
}

private fun lastRowStart(swatches: Int): Int = (swatches - 1).coerceAtLeast(0) / ColorPages.PALETTE_COLUMNS * ColorPages.PALETTE_COLUMNS
