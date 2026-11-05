package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.rememberTextMeasurer
import com.plumemu.coreui.theme.PlumTheme

/**
 * The drawn box front of a game without box art ([BoxFrontLayout]): the board's cover, in colours and shapes picked by [title]'s
 * hash. The band shows [systemShort] ("SNES"; [system] when there is none) and [line] ("Publisher · Year", empty when unknown)
 * stands under it. It fills the size the caller gives it; the caller clips and rounds it. The cell around it carries the
 * title for accessibility, so this adds no semantics.
 */
@Composable
fun PosterCover(
    title: String,
    system: String,
    systemShort: String = system,
    modifier: Modifier = Modifier,
    line: String = "",
) {
    // The grid's shared measurer when there is one: a measurer of its own per card was one more allocation per cover.
    val measurer = LocalTileTitleFit.current?.measurer ?: rememberTextMeasurer(cacheSize = 8)
    // The theme's font family at its heaviest weight; the cover does not follow the text-weight setting.
    val family = PlumTheme.type.gridFocusTitle.fontFamily
    Box(
        modifier.drawWithCache {
            val layout = BoxFrontLayout.of(measurer, this, title, system, systemShort, line, family, size)
            onDrawBehind { layout.draw(this) }
        },
    )
}
