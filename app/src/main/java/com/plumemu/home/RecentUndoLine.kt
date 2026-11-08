package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.ButtonLetters

/** Whether Remove from Recent's Undo is offered now (`RecentUndo.game`); provided by `HomeScreen`. */
internal val LocalRecentUndoOffered = staticCompositionLocalOf<() -> Boolean> { { false } }

/** The top bar's "Removed from Recent" and "Undo" ([line], null: the usual text) and the opacity of the place text. */
internal class ShownUndo(val line: Pair<String, String>?, val alpha: () -> Float)

/**
 * While Undo is offered the top bar shows its line in the notice slot, with the same fades as
 * [rememberShownNotice] (the usual text out, the line in, and back at the end); with motion off the texts swap at once.
 * The opacity is read while drawing.
 */
@Composable
internal fun rememberShownUndo(): ShownUndo {
    val offered = LocalRecentUndoOffered.current()
    var isShown by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }
    val isMotionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    LaunchedEffect(offered) {
        if (offered == isShown) return@LaunchedEffect
        val spec = tween<Float>(PlumMotion.FADE_IN_MS, easing = PlumMotion.Decelerate)
        suspend fun fadeTo(target: Float) = if (isMotionOn) { alpha.animateTo(target, spec); Unit } else alpha.snapTo(target)
        fadeTo(0f)
        isShown = offered
        fadeTo(1f)
    }
    val lead = stringResource(R.string.home_notice_removed_from_recent)
    val tail = stringResource(R.string.home_hint_undo)
    return remember(isShown, lead, tail) { ShownUndo(if (isShown) lead to tail else null) { alpha.value } }
}

/**
 * While Undo is offered X says "Undo": in place of "Search" where the bar has it, else (a cover grid, the narrow details
 * stop) after B Back, so the key is never live with no hint drawn. The same action, so a tap on it undoes too.
 */
internal fun List<HintSpec>.withUndo(
    offered: Boolean,
    letters: ButtonLetters,
): List<HintSpec> {
    if (!offered) return this
    if (any { it.action == Action.Search }) return map { if (it.action == Action.Search) it.copy(label = R.string.home_hint_undo) else it }
    val at = indexOfFirst { it.action == Action.Back } + 1
    return take(at) + HintSpec(letters.search, R.string.home_hint_undo, Action.Search) + drop(at)
}
