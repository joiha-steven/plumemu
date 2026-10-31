package com.plumemu.coreui.theme

/**
 * The "your own colour" palette of the Background setting: 8 hue columns by 6 rows (design/rules.md
 * › Colour). Columns, left to right: neutral, red, orange, yellow, green, teal, blue, purple. Rows 1
 * to 3 are light and vivid backgrounds that carry dark text (vivid, lighter, pale); rows 4 to 6 are
 * deep ones that carry light text (deep, darker, near black). Every cell keeps its hue and
 * saturation and moves in HSL lightness only as far as [Readability.backgroundAllowsText] needs
 * so the grid is whole: no cell is missing and none is unreadable.
 */
object CustomPalette {
    /** Hue in degrees; null is neutral (saturation 0). Only the Text and Accent palettes use it. */
    private val HUES: List<Int?> = listOf(null, 0, 28, 48, 135, 180, 215, 270)

    val hueCount: Int = HUES.size

    /** Rows of the Background palette and of the Text and Accent palettes on a background. */
    const val ROWS = 6

    /**
     * The 48 backgrounds, row by row (row 1 first), each row neutral first then red, orange, yellow,
     * green, teal, blue, purple. Chosen by hand, not computed, so they look like painted shells
     * rather than HSL arithmetic: rows 1 to 3 vivid, lighter and pale (dark text), rows 4 to 6 deep,
     * darker and near black (light text). Every one passes [Readability.backgroundAllowsText]
     * (`CustomPaletteTest`). Index = `row * hueCount + column`.
     */
    val backgroundSwatches: List<Rgb> =
        listOf(
            // vivid
            0xB8BCBF, 0xEF8484, 0xE98D43, 0xE8BE46, 0x6FCF8E, 0x4FC9C0, 0x7DD3FC, 0xB393EB,
            // lighter
            0xCECAC7, 0xF5A9A6, 0xF2AE74, 0xF2D77E, 0xA3E0B5, 0x8FDDD7, 0xA9E0FD, 0xB4A9D6,
            // pale
            0xF4F2EE, 0xFAD4D2, 0xF9D9BC, 0xFAEDBE, 0xD2F1DB, 0xCDF0ED, 0xD3EFFE, 0xDDD6F0,
            // deep
            0x3B3E42, 0x981539, 0x6F3B0E, 0x57470B, 0x12542B, 0x0C524F, 0x1541A9, 0x5B2A9E,
            // darker
            0x1F2124, 0x6A1F1F, 0x4A290C, 0x403409, 0x0E3D20, 0x093B39, 0x0F2B7A, 0x3D1B6E,
            // near black
            0x0A0A0B, 0x3A1214, 0x24150A, 0x231C07, 0x0A2A18, 0x052221, 0x0A1A4A, 0x1F0F3A,
        ).map(::Rgb)

    /** The background at [row] and [column] (both from 0) of [backgroundSwatches]. */
    fun backgroundAt(
        row: Int,
        column: Int,
    ): Rgb = backgroundSwatches[row * hueCount + column]

    /**
     * Saturation of the Accent palette: strong, so every hue column is vivid (the readability rule only asks 3:1).
     */
    private const val ACCENT_SATURATION = 0.85

    /** Rows of the Text and Accent palettes on a background: as many as the Background palette has. */
    val readableShadeCount: Int = ROWS

    private const val LIGHTNESS_STEP = 0.01
    private const val TEXT_LIGHTEST = 0.97
    private const val TEXT_DARKEST = 0.06
    private const val ACCENT_LIGHTEST = 0.85
    private const val ACCENT_DARKEST = 0.20

    /**
     * The Accent palette on [background]: 8 hue columns by [readableShadeCount] rows, every cell
     * passing [ColorRole.Accent] (3:1). See [readableColumn] for how a column is built.
     */
    fun accentSwatchesOn(background: Rgb): List<Rgb> = cached(background, ColorRole.Accent) { readableGrid(background, ColorRole.Accent, ACCENT_DARKEST..ACCENT_LIGHTEST, ACCENT_SATURATION) }

    /**
     * The Text palette on [background]: 8 hue columns by [readableShadeCount] rows, every cell
     * passing [ColorRole.Text]: tinted lights where Light reads better, tinted darks where Dark does.
     * Empty on a background nothing reads on.
     */
    fun textSwatchesOn(background: Rgb): List<Rgb> =
        cached(background, ColorRole.Text) {
            if (!Readability.backgroundAllowsText(background)) {
                emptyList()
            } else if (TextMode.bestOn(background) == TextMode.Light) {
                readableGrid(background, ColorRole.Text, 0.5..TEXT_LIGHTEST, TEXT_SATURATION)
            } else {
                readableGrid(background, ColorRole.Text, TEXT_DARKEST..0.5, TEXT_SATURATION)
            }
        }

    private const val TEXT_SATURATION = 0.85

    // The panel asks for the same background on every frame.
    private val lastGrid = arrayOfNulls<Triple<Rgb, ColorRole, List<Rgb>>>(1)

    private fun cached(
        background: Rgb,
        role: ColorRole,
        build: () -> List<Rgb>,
    ): List<Rgb> {
        synchronized(lastGrid) {
            lastGrid[0]?.takeIf { it.first == background && it.second == role }?.let { return it.third }
            return build().also { lastGrid[0] = Triple(background, role, it) }
        }
    }

    /** Row by row, one column per hue (grey first), like [swatches]. Index = `row * hueCount + hue`. */
    private fun readableGrid(
        background: Rgb,
        role: ColorRole,
        lightness: ClosedFloatingPointRange<Double>,
        saturation: Double,
    ): List<Rgb> {
        val columns = HUES.map { hue -> readableColumn(hue, background, role, lightness, saturation) }
        return (0 until readableShadeCount).flatMap { row -> columns.map { it[row] } }
    }

    /**
     * One hue's cells on [background]: the lightnesses in [lightness] at which the colour passes,
     * from the most saturated (nearest 0.5, where HSL is strongest) evenly to the palest (the
     * farthest end), so every column spans the whole readable range of its hue. A hue nothing
     * passes in takes the nearest passing colour of its middle.
     */
    private fun readableColumn(
        hue: Int?,
        background: Rgb,
        role: ColorRole,
        lightness: ClosedFloatingPointRange<Double>,
        saturation: Double,
    ): List<Rgb> {
        val steps = ((lightness.endInclusive - lightness.start) / LIGHTNESS_STEP).toInt()
        val passing =
            (0..steps)
                .map { lightness.start + it * LIGHTNESS_STEP }
                .map { l -> ColorMath.fromHsl((hue ?: 0) / 360.0, if (hue == null) 0.0 else saturation, l) }
                .filter { Readability.passes(it, background, role) }
                .sortedBy { kotlin.math.abs(ColorMath.toHsl(it).third - 0.5) }
        if (passing.isEmpty()) {
            val middle = ColorMath.fromHsl((hue ?: 0) / 360.0, if (hue == null) 0.0 else saturation, (lightness.start + lightness.endInclusive) / 2)
            return List(readableShadeCount) { Readability.nearestPassing(middle, background, role) }
        }
        return List(readableShadeCount) { row -> passing[Math.round(row * (passing.size - 1).toDouble() / (readableShadeCount - 1)).toInt()] }
    }
}
