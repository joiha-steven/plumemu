package com.plumemu.home

import com.plumemu.R
import com.plumemu.coreinput.Action

/**
 * The hints of an icon grid (Apps, or a system of installed games): A, B, X Search (only with games to
 * find), START Menu, "L2 R2 Switch system" and SELECT. Its size is fixed, so there is no L1 R1 hint.
 */
internal fun appsGridHints(
    play: HintSpec,
    back: HintSpec,
    search: HintSpec?,
    switchSystem: HintSpec?,
): List<HintSpec> =
    listOfNotNull(
        play,
        back,
        search,
        HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu),
        switchSystem,
        HintSpec(R.string.home_key_select, R.string.home_hint_list_view, Action.ToggleView),
    )

/** When the bar is too narrow: Switch system gives way first (the system column is a tap away), then X Search, then START Menu, then B Back; SELECT and A stay. */
internal val APPS_GRID_HINT_DROP_ORDER = listOf(Action.JumpLetterNext, Action.NextSystem, Action.Search, Action.Menu, Action.Back, Action.ToggleView)
