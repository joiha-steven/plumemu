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
import com.plumemu.coreui.theme.AppsGridDimens

/**
 * An app game's Android icon, centred in [size] (the details column's cover slot, the second screen's art slot), at
 * [AppsGridDimens.appGameDetailsIcon] but never larger than the slot's shorter side. While it loads the place stays empty;
 * an app without a usable icon shows the round first-letter stand-in, as the Apps grid does.
 */
@Composable
internal fun AppGameIcon(
    packageName: String,
    title: String,
    size: DpSize,
    modifier: Modifier = Modifier,
    /** The most the picture may take of [size]: the details icon by default, a card that shows the app large raises it. */
    maxIcon: Dp = AppsGridDimens.appGameDetailsIcon,
) {
    val icons = LocalHomeAppIcons.current
    val generation by icons.generation.collectAsState()
    val icon: Dp = minOf(maxIcon, size.width, size.height)
    val iconPx = with(LocalDensity.current) { icon.roundToPx() }.coerceAtLeast(1)
    val shown = rememberTileIcon(packageName, iconPx, icons, generation)
    Box(modifier.size(size).semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
        val iconModifier = Modifier.size(icon)
        when (shown) {
            is TileIcon.Picture -> Image(shown.image, contentDescription = null, modifier = iconModifier.testTag(HomeTags.APP_GAME_ICON))
            TileIcon.Missing -> AppIconStandIn(title, iconModifier, letterSize = if (maxIcon > AppsGridDimens.appGameDetailsIcon) icon.value * STAND_IN_LETTER_SHARE else null)
            TileIcon.Loading -> Unit
        }
    }
}

/** A large stand-in disc carries its letter at this share of its diameter. */
internal const val STAND_IN_LETTER_SHARE = 0.45f
