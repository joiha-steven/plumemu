package com.plumemu.home.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.components.keyboard.KeyboardSpec
import com.plumemu.keyboard.KeyboardTray

/**
 * Plumemu's keyboard for Search: the shared [KeyboardTray], up
 * while [SearchState.isTrayUp], no shift (Search ignores case), the action key "Search". The results
 * run on underneath it. Taps on its keys go to [SearchState.onKeyboardEvent].
 */
@Composable
internal fun SearchKeyboardTray(
    search: SearchState,
    belowContent: Dp,
) {
    val actionLabel = stringResource(R.string.home_hint_search)
    val spec = remember(actionLabel) { KeyboardSpec(actionLabel = actionLabel, hasShift = false) }
    KeyboardTray(
        owner = search,
        isUp = search.isTrayUp,
        keys = search.keys,
        spec = spec,
        onEvent = search::onKeyboardEvent,
        belowContent = belowContent,
        tag = SearchTags.TRAY,
    )
}
