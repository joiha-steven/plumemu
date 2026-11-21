package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil

/** The widths of a games list's year and region columns ([GameRow] with a region): each as wide as its Mono text needs. */
@Immutable
data class RowColumns(val year: Dp, val region: Dp)

/**
 * The columns of the list a game row stands in, measured once per list ([rememberRowColumns]) so every row's year and region line up and
 * no row holds room it never uses. Null (a row outside such a list): [GameRow] measures its own.
 */
val LocalRowColumns = staticCompositionLocalOf<RowColumns?> { null }

/**
 * The year column as wide as four digits of `rowYear`, the region column as wide as [widestRegion] (at least three letters) in `tag`, both
 * measured with the text size in force (Mono glyphs are one width, rounded per glyph as the screen draws them), plus [PlumDimens.rowColumnSlack].
 */
@Composable
fun rememberRowColumns(widestRegion: String): RowColumns {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val type = PlumTheme.type
    val year = type.rowYear
    val tag = type.tag
    val region = widestRegion.padEnd(REGION_COLUMN_CHARS, 'W')
    return remember(measurer, density, year, tag, region) {
        fun width(text: String, style: TextStyle): Dp =
            with(density) { ceil(measurer.measure(text, style, softWrap = false, maxLines = 1).multiParagraph.maxIntrinsicWidth).toDp() } + PlumDimens.rowColumnSlack
        RowColumns(width(YEAR_SAMPLE, year), width(region, tag))
    }
}

/** Region codes are three letters. */
const val REGION_COLUMN_CHARS = 3

private const val YEAR_SAMPLE = "0000"
