package com.plumemu.home

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.GameRow
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.composedAs
import com.plumemu.identify.name.VariantTag

/*
 * The rows of a games page (GamesPages.kt): each row composes with its own look state, which the
 * page's follower writes while composing the frame of a focus change, so only the rows whose
 * composed look changed are visited.
 */

/**
 * One row of a games list. It composes with its own [RowForms] state, which changes only when this
 * row's composed look does, so a move runs the two items whose look changed and no other item of
 * any page is even visited (HomeRecompositionTest); Enter and Back run the remembered row of each
 * page. The full look is read while drawing, through a `derivedStateOf` per row, for the title's
 * colour: a row whose colour did not change is not redrawn.
 */
@Composable
internal fun GamesListItem(
    index: Int,
    row: GameRowModel,
    entryId: String,
    hasBoxArt: Boolean,
    mixesSystems: Boolean,
    showsPlace: Boolean,
    isShown: () -> Boolean,
    forms: RowForms,
    state: StripState,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
) {
    val form = remember(forms, index) { forms.slot(index) }
    val drawn = remember(state, entryId, index) { derivedStateOf { gameLook(state.focus, entryId, index, panelOpen.value) } }
    val drawnLook = remember(drawn) { { drawn.value } }
    val tap = remember(index, onAction, isShown) { Modifier.tapItem(Column.Games, index, onAction).gameRowTag(index, isShown) }
    // The year from the game's ScreenScraper result: its own state, so a result that lands
    // recomposes this row alone.
    // Not at the narrow details stop: the list is slid out to 56 dp there, the right-hand text has 42 dp, and a
    // wide short name (SCUMM, WASM4) would be cut at the left. A derived state, so only a change of stop recomposes.
    val showsSystem by remember(state, mixesSystems) { derivedStateOf { mixesSystems && state.focus.column != Column.Details } }
    // Year · region only in a single system's list that has the room (listShowsPlace); the narrow details stop needs
    // no case of its own there: a narrow list never shows it beside the details, so no row reads the stop.
    val resources = LocalContext.current.resources
    val joinPlace = remember(resources) { { year: String, region: String -> resources.getString(R.string.home_details_pair, year, region) } }
    val trailing = row.trailing(hasBoxArt, LocalHomeArt.current.slot(row.scrapeKey), showsSystem, joinPlace.takeUnless { mixesSystems || !showsPlace })
    // Only a row whose title another row of the list shares has tags (withVariantTags), so the others read no string.
    val subtitle = remember(resources, row.variantTags, row.variantRegion) { variantSubtitle(resources, row.variantTags, row.variantRegion) }
    GameRow(title = row.title, trailing = trailing, look = form.look, modifier = tap, isFavorite = row.isFavorite, drawnLook = drawnLook, subtitle = subtitle)
}

/** "· Rev 1" for a row with variant tags, "· Rev 1, EUR" when [region] is given too; empty when neither has a word. */
internal fun variantSubtitle(
    resources: Resources,
    tags: List<VariantTag>,
    region: String = "",
): String =
    listOf(variantLabel(resources, tags), region).filter { it.isNotEmpty() }
        .reduceOrNull { all, next -> resources.getString(R.string.variant_join, all, next) }
        ?.let { resources.getString(R.string.variant_after_title, it) }
        .orEmpty()

/** The remembered row of an entry and the look it is composed with ([composedAs]); every other row is composed Normal. */
internal data class RememberedForm(
    val row: Int,
    val look: GameRowLook,
)

/** [entryId]'s remembered row and its composed look in [focus]. */
internal fun rememberedForm(
    focus: StripFocus,
    entryId: String,
    isPanelOpen: Boolean,
): RememberedForm {
    val row = focus.rowOf(entryId)
    return RememberedForm(row, gameLook(focus, entryId, row, isPanelOpen).composedAs())
}

/**
 * The composed looks of one page's rows, one state per composed row, so a change of the focus
 * reaches only the rows whose composed look changed. A look read through a `derivedStateOf` per
 * row would still visit every row of every built page on each focus change (about 30 with the
 * pages kept beside the shown one) to find the two that changed. [follow] is called while
 * composing the frame of the change ([FollowRowForms]); the rows it writes recompose in that same
 * frame (Compose recomposes compositions whose state another composition wrote).
 */
internal class RowForms {
    private val slots = HashMap<Int, Slot>()
    private var remembered: RememberedForm? = null

    /** The state of row [index], kept while the row is composed (remember it). */
    fun slot(index: Int): Slot = Slot(index, lookOf(index)).also { slots[index] = it }

    /** The remembered row or its look changed: rewrite the two rows concerned. */
    fun follow(now: RememberedForm) {
        val before = remembered
        if (before == now) return
        remembered = now
        before?.let { slots[it.row]?.state?.value = lookOf(it.row) }
        slots[now.row]?.state?.value = now.look
    }

    private fun lookOf(index: Int): GameRowLook = remembered?.takeIf { it.row == index }?.look ?: GameRowLook.Normal

    inner class Slot(
        private val index: Int,
        initial: GameRowLook,
    ) : RememberObserver {
        val state = mutableStateOf(initial)

        /** The row's composed look; reading it subscribes the row to this state only. */
        val look: GameRowLook get() = state.value

        override fun onRemembered() = Unit

        override fun onForgotten() = release()

        override fun onAbandoned() = release()

        private fun release() {
            if (slots[index] === this) slots.remove(index)
        }
    }
}

/**
 * Keeps [forms] on the focus: only this small scope reads it (through a `derivedStateOf` that
 * changes when the entry's remembered row or its composed look does), once per page.
 */
@Composable
internal fun FollowRowForms(
    forms: RowForms,
    state: StripState,
    entryId: String,
    panelOpen: State<Boolean>,
) {
    val now by remember(state, entryId) { derivedStateOf { rememberedForm(state.focus, entryId, panelOpen.value) } }
    forms.follow(now)
}
