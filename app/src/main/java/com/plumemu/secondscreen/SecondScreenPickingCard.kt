package com.plumemu.secondscreen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
        content is SecondScreenContent.Apps && content.icons.isNotEmpty() && dimens.showsArt -> AppsCard(content)
        else -> PickingBlock(content, dimens)
    }
}
