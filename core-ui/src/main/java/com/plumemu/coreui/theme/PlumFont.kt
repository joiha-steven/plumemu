package com.plumemu.coreui.theme

/**
 * The four fonts and their tuning row. Pure numbers; the Compose families are
 * in [PlumFonts].
 *
 * Measured from the TTF files in `res/font/` with fontTools (`OS/2.sxHeight`, `hhea`), and the
 * glyph bounds of "x" agree with sxHeight in every file. `FontMetricsTest` re-reads the files and
 * fails if a font update changes them:
 *
 * | Font  | unitsPerEm | sxHeight      | hhea ascent / descent |
 * |-------|------------|---------------|-----------------------|
 * | Jost  | 1000       | 460 (0.460)   | 1070 / -375           |
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
    /** Jost is drawn unadjusted, as the V2 drawings set it: its x-height (0.46) sits below Plum Sans' (0.546). */
    Jost(
        sizeFactor = 1f,
        lineHeightEm = (1070f + 375f) / 1000f,
        darkBackgroundWeightAdjust = 0,
        digitHeightEm = (710f + 10f + 20f) / 1000f,
        // Less 0.01 em: measured in the drawn bitmap, the bolt and the Wi-Fi glyph sit about half a pixel low with the metrics alone.
        digitCentreBelowLineCentreEm = 1070f / 1000f - (710f - 10f) / 2f / 1000f - (1070f + 375f) / 1000f / 2f - 0.01f,
    ),
    Sans(
        sizeFactor = 1f,
        lineHeightEm = (1984f + 494f) / 2048f,
        darkBackgroundWeightAdjust = 0,
        digitHeightEm = (1510f + 20f + 60f) / 2048f,
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
        val Default: PlumFont = Jost
    }
}

/**
 * The board's `font-size-adjust` for Serif and Mono. Plum Sans' own x-height is 0.5459, so on
 * the board (and here) Serif and Mono sit about 3 % lower in x-height than Sans; matching Sans
 * exactly would need factors 1.066 (Serif) and 0.993 (Mono). Kept at the board's value.
 */
private const val BOARD_X_HEIGHT = 0.53f

/**
 * Serif's digits sit 0.085 em below the middle of the line (hhea ascent and the digits' height give 0.071; measured in the
 * drawn bitmap with the glyph centred on the ink, `WifiTopBarAlignTest` at every size). The top bar is as tall as its
 * tallest line, so its text is never squeezed into a box shorter than its line, which used to push Serif's line up
 * by 0.09 em and to need 0.175 here.
 */
private const val SERIF_DIGIT_CENTRE_BELOW_LINE_CENTRE = 0.085f
