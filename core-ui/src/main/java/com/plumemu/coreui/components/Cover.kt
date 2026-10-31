package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * A cover without a picture (no box art, or while it decodes): a flat tile in the `sel` token
 * with the game's [title] (13 / 600, `text2`) and, under it, [subtitle] (the year, 13 `muted`),
 * both at the bottom left inside [padding], so the tile reads like a box front's label rather
 * than an empty box. The title wraps between words up to [maxLines]; [onTitleLayout] sees how it
 * was laid out (a caller drops the subtitle when the title did not fit). [modifier] sets the size;
 * [corner]: 8 landscape, 6 portrait. [rim] (a grid's rim light, [drawCoverRim]) is drawn over the tile just inside its edge; transparent: none. [titleStyle] and [subtitleStyle] are the small grid's 11 there; a null
 * [title] leaves the tile without one (the details column names the game right under it).
 */
@Composable
fun CoverTile(
    title: String?,
    corner: Dp,
    padding: Dp,
    maxLines: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    titleStyle: TextStyle = PlumTheme.type.gridFocusTitle,
    subtitleStyle: TextStyle = PlumTheme.type.smallFact,
    onTitleLayout: (TextLayoutResult) -> Unit = {},
    rim: Color = Color.Transparent,
) {
    val colors = PlumTheme.colors
    val shape = RoundedCornerShape(corner)
    Column(
        modifier =
            modifier.clip(shape).background(colors.sel, shape).then(
                if (rim == Color.Transparent) Modifier else Modifier.drawWithContent { drawContent(); drawCoverRim(rim, corner.toPx()) },
            ).padding(padding),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap, alignment = Alignment.Bottom),
    ) {
        if (title != null) {
            BasicText(
                text = title,
                style = titleStyle.copy(color = colors.text2),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier,
                onTextLayout = onTitleLayout,
            )
        }
        if (!subtitle.isNullOrEmpty()) {
            BasicText(text = subtitle, style = subtitleStyle.copy(color = colors.muted), maxLines = 1)
        }
    }
}
