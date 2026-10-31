package com.plumemu.coreui.theme

/**
 * The three Plum fonts and their tuning row. Pure numbers; the Compose families are
 * in [PlumFonts].
 *
 * Measured from the TTF files in `res/font/` with fontTools (`OS/2.sxHeight`, `hhea`), and the
 * glyph bounds of "x" agree with sxHeight in every file. `FontMetricsTest` re-reads the files and
 * fails if a font update changes them:
 *
 * | Font  | unitsPerEm | sxHeight      | hhea ascent / descent |
 * |-------|------------|---------------|-----------------------|
 * | Sans  | 2048       | 1118 (0.5459) | 1984 / -494           |
 * | Serif | 1000       | 512 (0.512); 515 in the 600 face | 1177 / -308 |
 * | Mono  | 1000       | 550 (0.550)   | 1020 / -300           |
 */
enum class PlumFont(
    /**
     * Multiplies every type size so the font's x-height matches the board, which draws Serif and
     * Mono with CSS `font-size-adjust: 0.53` (x-height = 0.53 × size) and Plum Sans unadjusted.
     * Factor = 0.53 / (sxHeight / unitsPerEm) of the 400 face.
     */
    val sizeFactor: Float,
    /**
     * Line height as a multiple of the scaled size: the font's own "normal" line,
     * (hhea ascent + descent) / unitsPerEm, which is what the board renders.
     */
    val lineHeightEm: Float,
    /**
     * Weight added on dark backgrounds (light text) to offset light-on-dark text looking bolder
     * or thinner. Zero for all three: the board makes no such change, and with static 400/600
     * (or 400/700) faces there is no small step to take.
     */
    val darkBackgroundWeightAdjust: Int,
    /**
     * Height of the digits' ink in em (glyph bounds of "8" in the 400 face, round overshoot
     * included): what a glyph drawn beside a number must match (the top bar's Wi-Fi glyph).
     */
    val digitHeightEm: Float,
    /**
     * How far below the middle of the text line the digits' ink is centred, in em: the hhea
     * ascent less the digits' middle above the baseline, less half [lineHeightEm] (Serif: see
     * the constant below).
     */
    val digitCentreBelowLineCentreEm: Float,
) {
    Sans(
        sizeFactor = 1f,
        lineHeightEm = (1984f + 494f) / 2048f,
        darkBackgroundWeightAdjust = 0,
        digitHeightEm = (1510f + 20f) / 2048f,
        digitCentreBelowLineCentreEm = 1984f / 2048f - (1510f - 20f) / 2f / 2048f - (1984f + 494f) / 2048f / 2f,
    ),
    Serif(
        sizeFactor = BOARD_X_HEIGHT / (512f / 1000f),
        lineHeightEm = (1177f + 308f) / 1000f,
        darkBackgroundWeightAdjust = 0,
        digitHeightEm = 0.727f,
        digitCentreBelowLineCentreEm = SERIF_DIGIT_CENTRE_BELOW_LINE_CENTRE,
    ),
    Mono(
        sizeFactor = BOARD_X_HEIGHT / (550f / 1000f),
        lineHeightEm = (1020f + 300f) / 1000f,
        darkBackgroundWeightAdjust = 0,
        digitHeightEm = 0.75f,
        digitCentreBelowLineCentreEm = 1.02f - 0.365f - (1.02f + 0.3f) / 2f,
    ),
    ;

    companion object {
        val Default: PlumFont = Sans
    }
}

/**
 * The board's `font-size-adjust` for Serif and Mono. Plum Sans' own x-height is 0.5459, so on
 * the board (and here) Serif and Mono sit about 3 % lower in x-height than Sans; matching Sans
 * exactly would need factors 1.066 (Serif) and 0.993 (Mono). Kept at the board's value.
 */
private const val BOARD_X_HEIGHT = 0.53f

/**
 * Serif's digits sit lower than the hhea metrics say (0.084 em): measured in the drawn bitmap,
 * Android places Serif's line about 0.09 em higher, so 0.175 centres the Wi-Fi glyph on them
 * (`WifiTopBarAlignTest` measures ink at every size). Sans and Mono follow their hhea numbers.
 */
private const val SERIF_DIGIT_CENTRE_BELOW_LINE_CENTRE = 0.175f
