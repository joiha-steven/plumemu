package com.plumemu.home

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import com.plumemu.R
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.TitledHintBar
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The grid's bar: "Mega Man X  1993 · Capcom" (a gap, no dot) from where the grid starts, the hints at the right. In
 * Recent and Favorites the meta starts with the game's system ("SNES · 1993 · Capcom"), as the list does.
 */
@Composable
internal fun GridHintBar(
    entry: HomeEntry,
    state: StripState,
    dropOrder: List<Int>,
    dropBeforeMeta: List<Int>,
    hints: @Composable () -> Unit,
) {
    val row by remember(state, entry.id) { derivedStateOf { state.focus.rowOf(entry.id) } }
    val game = entry.rows.getOrNull(row)
    val art = LocalHomeArt.current
    // Through a derived state: read straight in this scope, a result that landed after the bar was
    // first composed did not reach it (HomeCoverArtTest).
    val details by remember(game, art) { derivedStateOf { game?.let { detailsOf(it, art.slot(it.scrapeKey)) } } }
    TitledHintBar(
        title = game?.title.orEmpty(),
        meta = gridMeta(game?.systemAbbr.takeIf { entry.kind.mixesSystems }, details?.let { yearAndPublisher(it) }.orEmpty(), LocalResources.current),
        startInset = PlumTheme.layout.peek + PlumDimens.columnGap,
        modifier = Modifier.testTag(HomeTags.HINT_BAR),
        dropOrder = dropOrder,
        dropBeforeMeta = dropBeforeMeta,
        hints = hints,
    )
}

/** [system] (Recent and Favorites only; null or empty is left out) joined to [rest] with the same " · " as year and publisher. */
private fun gridMeta(
    system: String?,
    rest: String,
    resources: Resources,
): String =
    when {
        system.isNullOrEmpty() -> rest
        rest.isEmpty() -> system
        else -> resources.getString(R.string.home_details_pair, system, rest)
    }
