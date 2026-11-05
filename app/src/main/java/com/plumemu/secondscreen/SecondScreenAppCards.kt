package com.plumemu.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.home.AppIconStandIn
import com.plumemu.home.STAND_IN_LETTER_SHARE
import com.plumemu.coreui.theme.SecondScreenGameCardDimens as D
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.AppGameIcon

/*
 * The Android app cards: one app and the Apps entry
 * (its first icons under the title). Flat, no focus look: the second screen only shows.
 */

/**
 * One app, in the game card's manner: the name in the game title's style with a small "APP" tag at its right, the app's icon large and
 * centred under it (a disc with the name's first letter when it has none), and the package in the mono face of the game card's file line.
 */
@Composable
internal fun BoxScope.AppCard(
    content: SecondScreenContent.App,
    iconKey: String?,
    dimens: SecondScreenDimens,
) {
    val packageName = iconKey?.substringBefore('/')?.takeIf { it.isNotBlank() }
    Column(
        Modifier.fillMaxSize().padding(start = D.marginSide, top = D.marginTop, end = D.marginSide, bottom = D.marginBottom).testTag(SecondScreenTags.DETAILS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderLine(content.name, isFavorite = false, tag = stringResource(R.string.second_screen_app_tag))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(dimens.artSlot, maxWidth, maxHeight)
            if (iconKey != null) {
                AppGameIcon(iconKey, content.name, DpSize(side, side), Modifier.testTag(SecondScreenTags.COVER), maxIcon = side)
            } else {
                Box(Modifier.size(side).testTag(SecondScreenTags.COVER)) {
                    AppIconStandIn(content.name, Modifier.fillMaxSize(), letterSize = side.value * STAND_IN_LETTER_SHARE)
                }
            }
        }
        if (packageName != null) FileLine(packageName, Modifier.padding(top = D.headerGap))
    }
}

/** The Apps entry: the count and title as everywhere, then as many of the first icons as the height left holds. */
@Composable
internal fun BoxScope.AppsCard(
    content: SecondScreenContent.Apps,
    dimens: SecondScreenDimens,
) {
    val texts = cardTexts(content) ?: return
    Column(Modifier.fillMaxSize().padding(dimens.margin).testTag(SecondScreenTags.DETAILS)) {
        Column { HeadLines(texts, dimens) }
        Spacer(Modifier.height(SecondScreenDimens.artToDescriptionGap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag(SecondScreenTags.APPS_GRID)) {
            val icon = AppsGridDimens.appsGridIcon
            val gap = PlumDimens.gridGapPortrait
            val columns = ((maxWidth + gap) / (icon + gap)).toInt().coerceAtLeast(1)
            val rows = ((maxHeight + gap) / (icon + gap)).toInt()
            val shown = content.icons.take(columns * rows)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                shown.chunked(columns).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        line.forEach { app -> AppGameIcon(app.key, app.name, DpSize(icon, icon), Modifier.testTag(SecondScreenTags.APPS_ICON)) }
                    }
                }
            }
        }
    }
}
