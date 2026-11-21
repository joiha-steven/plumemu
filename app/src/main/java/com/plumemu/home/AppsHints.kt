package com.plumemu.home

import com.plumemu.R
import com.plumemu.coreinput.Action

/**
 * The hints of an icon grid (Apps, or a system of installed games): A, B, X Search (only with games to
 * find), START Menu, "L2 R2 Switch list" and SELECT. Its size is fixed, so there is no L1 R1 hint.
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
