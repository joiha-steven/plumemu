package com.plumemu.secondscreen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenDimens

/**
 * A picking card that is not a game, system, Recent or Favorites entry: an app with an icon or the Apps entry with icons gets
 * its own card, every other one (an app without an icon, the Apps entry with none) the picking block. One call site for the
 * picking block whatever the content, so moving between cards recomposes it in place.
 */
@Composable
internal fun BoxScope.PickingCard(
    content: SecondScreenContent,
    dimens: SecondScreenDimens,
) {
    when {
        content is SecondScreenContent.App && dimens.showsArt -> AppCard(content, content.iconKey, dimens)
        content is SecondScreenContent.Apps && content.icons.isNotEmpty() && dimens.showsArt -> AppsCard(content, dimens)
        else -> PickingBlock(content, dimens)
    }
}

/** The top line and the title (its star kept, cut before it). */
@Composable
internal fun ColumnScope.HeadLines(
    texts: CardTexts,
    dimens: SecondScreenDimens,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    TopLine(texts.top, type.card(SecondScreenDimens.detailsTopRole).copy(color = colors.meta))
    Spacer(Modifier.height(SecondScreenDimens.topToTitleGap))
    SecondScreenTitle(
        title = texts.title,
        isFavorite = texts.isFavorite,
        titleStyle = type.card(dimens.detailsTitleRole).copy(color = colors.text),
        starStyle = type.card(dimens.starRole),
        starColor = colors.meta,
        maxLines = dimens.detailsTitleMaxLines,
        modifier = Modifier.testTag(SecondScreenTags.TITLE),
    )
}
