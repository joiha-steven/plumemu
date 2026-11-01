package com.plumemu.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.home.AppGameIcon

/*
 * The Android app cards: one app (its icon in the art slot, as an app game's details card draws it) and the Apps entry
 * (its first icons under the title). Flat, no focus look: the second screen only shows.
 */

/** One app: its icon in the art slot top-left, the top line and name beside it ([AppGameIcon]: the same icon and stand-in as the details card's). */
@Composable
internal fun BoxScope.AppCard(
    content: SecondScreenContent.App,
    iconKey: String,
    dimens: SecondScreenDimens,
) {
    val texts = cardTexts(content, separatorColor = PlumTheme.colors.muted) ?: return
    Column(Modifier.fillMaxSize().padding(dimens.margin).testTag(SecondScreenTags.DETAILS)) {
        Row(Modifier.fillMaxWidth()) {
            AppGameIcon(iconKey, content.name, DpSize(dimens.artSlot, dimens.artSlot), Modifier.testTag(SecondScreenTags.COVER))
            Spacer(Modifier.width(SecondScreenDimens.artToTextGap))
            Column(Modifier.weight(1f)) { HeadLines(texts, NO_DETAIL_LINES, dimens) }
        }
    }
}

/** The Apps entry: the count and title as everywhere, then as many of the first icons as the height left holds. */
@Composable
internal fun BoxScope.AppsCard(
    content: SecondScreenContent.Apps,
    dimens: SecondScreenDimens,
) {
    val texts = cardTexts(content, separatorColor = PlumTheme.colors.muted) ?: return
    Column(Modifier.fillMaxSize().padding(dimens.margin).testTag(SecondScreenTags.DETAILS)) {
        Column { HeadLines(texts, NO_DETAIL_LINES, dimens) }
        Spacer(Modifier.height(SecondScreenDimens.artToDescriptionGap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag(SecondScreenTags.APPS_GRID)) {
            val icon = PlumDimens.appsGridIcon
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

private val NO_DETAIL_LINES = DetailLines("", null, "")
