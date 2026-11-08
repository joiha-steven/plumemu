package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.coreinput.strip.StripFocus
import androidx.compose.ui.geometry.Rect
import com.plumemu.coreui.theme.ContentFade
import com.plumemu.coreui.theme.LaunchOrigin
import com.plumemu.coreui.theme.SwapStep
import com.plumemu.coreui.theme.rememberSteppedSwap
import kotlin.math.abs
import kotlin.math.sign

/**
 * The second screen moves like the top one (design/rules.md › Second screen, Motion): a card that follows the cursor
 * comes in from the side the cursor went, a card for Settings settles in like a screen, and the in-game card comes and
 * goes with depth. The same tokens and curve as the top screen.
 */
internal object SecondScreenMotion {
    /** Where on the top screen a card was offered from: its column (left to right), system and row. */
    private data class Place(
        val column: Int,
        val system: Int,
        val row: Int,
        val systems: Int,
    )

    /** The places of the last cards offered, by [cardIdentity] (a card filled in late keeps its place). Main thread only. */
    private val places =
        object : LinkedHashMap<Any, Place>(PLACES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Place>?) = size > PLACES
        }

    /** Where the top screen draws its focused game or app ([LaunchOrigin]); null when no home follows. */
    private var origin: LaunchOrigin? = null

    /**
     * The card on the window and where it was: its place and the focused item's rectangle on the top screen when it
     * came. Kept apart from [places], so a held key that offers many cards never evicts the one still shown.
     */
    private var shownKey: Any? = null
    private var shownPlace: Place? = null
    private var shownRect: Rect? = null

    /** [card] was offered for the top screen's cursor at [focus], in a column of [systems] systems; [origin] draws it. */
    fun noteOffered(
        card: SecondScreenContent,
        focus: StripFocus,
        systems: Int,
        origin: LaunchOrigin? = this.origin,
    ) {
        this.origin = origin
        val system = focus.systemId
        places[cardIdentity(card)] = Place(focus.column.ordinal, focus.systemIndex, if (system == null) 0 else focus.rowOf(system), systems)
    }

    /** [card] is now on the window: keep where it came from for the next step. */
    fun showing(card: SecondScreenContent) {
        val key = cardIdentity(card)
        if (key == shownKey) return
        shownKey = key
        shownPlace = places[key]
        shownRect = origin?.window
    }

    /**
     * How [to] comes in after [from]: with depth to and from Statistics (Settings), else from the side the cursor went.
     * Within one list or grid the side is where the focused item moved on the top screen (a grid's step right comes from
     * the right), or, when the list scrolled under a cursor that stayed in place, the way the rows went.
     */
    fun stepBetween(
        from: SecondScreenContent?,
        to: SecondScreenContent,
    ): SwapStep? {
        if (from == null) return null
        if (to is SecondScreenContent.Statistics) return SwapStep(depth = -1)
        if (from is SecondScreenContent.Statistics) return SwapStep(depth = 1)
        val fromKey = cardIdentity(from)
        val a = (if (fromKey == shownKey) shownPlace else null) ?: places[fromKey] ?: return null
        val b = places[cardIdentity(to)] ?: return null
        return when {
            a.column != b.column -> SwapStep(x = (b.column - a.column).coerceIn(-1, 1))
            a.system != b.system -> SwapStep(y = ContentFade.sideOf(b.system - a.system, b.systems).toInt())
            a.row != b.row -> stepOnScreen(if (fromKey == shownKey) shownRect else null, origin?.window) ?: SwapStep(y = (b.row - a.row).coerceIn(-1, 1))
            else -> null
        }
    }

    /** The side the focused item moved to between [was] and [now] on the top screen; null when it stayed in place. */
    internal fun stepOnScreen(
        was: Rect?,
        now: Rect?,
    ): SwapStep? {
        if (was == null || now == null) return null
        val dx = now.center.x - was.center.x
        val dy = now.center.y - was.center.y
        return when {
            maxOf(abs(dx), abs(dy)) < minOf(now.width, now.height) / 4 -> null
            abs(dx) > abs(dy) -> SwapStep(x = sign(dx).toInt())
            else -> SwapStep(y = sign(dy).toInt())
        }
    }

    /** Going into a game the in-game card settles in from smaller; coming back the cards settle in from larger. */
    fun stepBetween(
        from: WindowMode,
        to: WindowMode,
    ): SwapStep? =
        when {
            from == to -> null
            to == WindowMode.Picking -> SwapStep(depth = 1)
            else -> SwapStep(depth = -1)
        }

    /** Forgets every place (tests). */
    fun reset() {
        places.clear()
        origin = null
        shownKey = null
        shownPlace = null
        shownRect = null
    }

    private const val PLACES = 16
}

/** What a card is, whatever of its facts arrive late: a late fact fills the card in place and starts no swap. */
internal fun cardIdentity(card: SecondScreenContent): Any =
    when (card) {
        is SecondScreenContent.Game -> listOf("game", card.gameId ?: card.title, card.systemName)
        is SecondScreenContent.App -> listOf("app", card.iconKey ?: card.name)
        is SecondScreenContent.Apps -> "apps"
        is SecondScreenContent.Statistics -> "statistics"
        is SystemEntry -> listOf("system", card.systemId)
        is RecentEntry -> "recent"
        is FavoritesEntry -> "favorites"
        else -> card
    }

/**
 * The swap of the picking window's card: keyed on what the card is, coming as [SecondScreenMotion.stepBetween] says. The
 * first card after [SecondScreenContent.Calm] (the window's empty page, also after a wake) shows at once: motion is for
 * steps the person took, not for content arriving. Statistics is one: opening Settings is a step, so it settles in even
 * when its figures took long enough for the calm page to show first.
 */
@Composable
internal fun rememberCardSwap(card: SecondScreenContent): ContentFade {
    val last = remember { arrayOfNulls<SecondScreenContent>(1) }
    val from = last[0]
    last[0] = card
    return rememberSteppedSwap(cardIdentity(card), fadeIn = { from != null && (from != SecondScreenContent.Calm || card is SecondScreenContent.Statistics) }) {
        (SecondScreenMotion.stepBetween(from, card) ?: SwapStep.None).also { SecondScreenMotion.showing(card) }
    }.also { if (from == null) SecondScreenMotion.showing(card) }
}

/** The swap of the window's mode: the in-game card and the picking cards come and go with depth. */
@Composable
internal fun rememberModeSwap(mode: WindowMode): ContentFade {
    val last = remember { arrayOfNulls<WindowMode>(1) }
    val from = last[0] ?: mode
    last[0] = mode
    return rememberSteppedSwap(mode) { SecondScreenMotion.stepBetween(from, mode) ?: SwapStep.None }
}
