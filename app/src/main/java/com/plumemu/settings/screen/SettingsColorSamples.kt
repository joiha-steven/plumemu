package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.coreui.theme.PlumTheme

// What the colour pages draw in their title row: the title and, at its right end, the name of the colour in effect.

private const val TITLE_LINES = 2

/**
 * A colour page's title row on the row-text lines: the page's title at the left, [name] at the right end (the colour in
 * effect, `meta` on one line), so the name needs no line of its own. The two share the width half and half; a long name ends in an ellipsis.
 */
@Composable
internal fun ColourPageTitle(
    title: String,
    name: @Composable RowScope.() -> Unit,
) {
    val colors = PlumTheme.colors
    Row(Modifier.fillMaxWidth().panelBlock().onRowTextLine(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title.withoutDots(), style = panelTitleStyle(), color = { colors.text }, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
        name()
    }
}

/** The colour's name at the right end of the title row; its code is the Color code row's. */
@Composable
internal fun RowScope.ColorName(name: String) {
    val colors = PlumTheme.colors
    BasicText(
        name,
        style = PlumTheme.type.meta.copy(textAlign = TextAlign.End),
        color = { colors.body },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).alignByBaseline().testTag(COLOUR_NAME_TAG),
    )
}
