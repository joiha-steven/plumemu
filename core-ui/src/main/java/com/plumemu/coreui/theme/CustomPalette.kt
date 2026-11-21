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
    /** The hue columns of [backgroundSwatches]: neutral, red, orange, yellow, green, teal, blue, purple (degrees; null is neutral). */
    private val HUES: List<Int?> = listOf(null, 0, 28, 48, 135, 180, 215, 270)

    val hueCount: Int = HUES.size

    /** Rows of the Background palette. */
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
            0xB8BCBF, 0xF08989, 0xEA934C, 0xE8BE46, 0x6FCF8E, 0x4FC9C0, 0x7DD3FC, 0xB899EC,
            // lighter
            0xCECAC7, 0xF5A9A6, 0xF2AE74, 0xF2D77E, 0xA3E0B5, 0x8FDDD7, 0xA9E0FD, 0xBDB3DB,
            // pale
            0xF4F2EE, 0xFAD4D2, 0xF9D9BC, 0xFAEDBE, 0xD2F1DB, 0xCDF0ED, 0xD3EFFE, 0xDDD6F0,
            // deep
            0x3B3E42, 0x911436, 0x6A390D, 0x52430A, 0x115029, 0x0B4E4B, 0x143FA4, 0x5B2A9E,
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
}
