package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * A short message in the middle of the strip's area, for states the board does not draw (an
 * empty library). Built from the strip's tokens: the title in the game-title style
 * (`text2`), the line under it in the meta style (`muted`), at most as wide as the games list. [modifier] sets the area it centres in.
 */
@Composable
fun CentredMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = PlumDimens.gamesListMinWidth),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(text = title, style = type.messageTitle.copy(color = colors.text2, textAlign = TextAlign.Center))
            BasicText(text = body, style = type.meta.copy(color = colors.muted, textAlign = TextAlign.Center))
        }
    }
}
