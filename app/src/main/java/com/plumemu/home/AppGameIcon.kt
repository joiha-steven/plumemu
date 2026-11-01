package com.plumemu.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.theme.PlumDimens

/**
 * An app game's Android icon, centred in [size] (the details column's cover slot, the second screen's art slot), at
 * [PlumDimens.appGameDetailsIcon] but never larger than the slot's shorter side. While it loads the place stays empty;
 * an app without a usable icon shows the round first-letter stand-in, as the Apps grid does.
 */
@Composable
internal fun AppGameIcon(
    packageName: String,
    title: String,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val icons = LocalHomeAppIcons.current
    val generation by icons.generation.collectAsState()
    val icon: Dp = minOf(PlumDimens.appGameDetailsIcon, size.width, size.height)
    val iconPx = with(LocalDensity.current) { icon.roundToPx() }.coerceAtLeast(1)
    val shown = rememberTileIcon(packageName, iconPx, icons, generation)
    Box(modifier.size(size).semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
        val iconModifier = Modifier.size(icon)
        when (shown) {
            is TileIcon.Picture -> Image(shown.image, contentDescription = null, modifier = iconModifier.testTag(HomeTags.APP_GAME_ICON))
            TileIcon.Missing -> AppIconStandIn(title, iconModifier)
            TileIcon.Loading -> Unit
        }
    }
}
