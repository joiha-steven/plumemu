package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.BadgeImages
import com.plumemu.graph.NewAchievements
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * What the main screen shows of RetroAchievements: the trophy on mastered games' rows and the quiet line
 * after a game. Signed out every member is empty and nothing behind them is built or asked
 * (`GraphAchievements.masteredGames`, `newAchievements`). Provided by `MainScreen` through
 * [LocalHomeAchievements], [LocalMasteredGames] and `LocalBadgeImages`.
 */
@Immutable
internal class HomeAchievements(
    /** Library game ids that are mastered. */
    val mastered: Flow<Set<Long>> = flowOf(emptySet()),
    /** Unlocks found after a game. */
    val newAchievements: Flow<NewAchievements> = emptyFlow(),
    /** The badges Info draws. */
    val badges: BadgeImages = BadgeImages.None,
)

internal val LocalHomeAchievements = staticCompositionLocalOf { HomeAchievements() }

/** The mastered games' ids ([HomeAchievements.mastered]), read by every game row for its trophy; empty signed out. */
internal val LocalMasteredGames = compositionLocalOf<Set<Long>> { emptySet() }

/** Whether the row of game [key] is a mastered game's (the key is a database id; fake rows' keys are not). */
internal fun Set<Long>.contains(key: String): Boolean = isNotEmpty() && key.toLongOrNull()?.let { it in this } == true

/** How long the top bar keeps the line after a game. */
internal const val ACHIEVEMENTS_LINE_SHOWN_MS: Long = 3_000L

/** The line the top bar shows now (null: the usual text) and the opacity of the place text. */
internal class ShownAchievements(val line: NewAchievements?, val alpha: () -> Float)

/**
 * Takes each [HomeAchievements.newAchievements] from [LocalHomeAchievements]: the usual text fades out
 * ([PlumMotion.FADE_IN_MS]), the line fades in and stays [ACHIEVEMENTS_LINE_SHOWN_MS], then it fades out and
 * the usual text returns; with motion off the texts swap at once. Same fades as the library's notice
 * ([rememberShownNotice]); nothing takes focus. A line that arrives while one shows waits its turn.
 */
@Composable
internal fun rememberShownAchievements(): ShownAchievements {
    val source = LocalHomeAchievements.current.newAchievements
    var shown by remember { mutableStateOf<NewAchievements?>(null) }
    val alpha = remember { Animatable(1f) }
    val isMotionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    LaunchedEffect(source) {
        source.collect { next ->
            val spec = tween<Float>(PlumMotion.FADE_IN_MS, easing = PlumMotion.Decelerate)
            suspend fun fadeTo(target: Float) = if (isMotionOn) { alpha.animateTo(target, spec); Unit } else alpha.snapTo(target)
            fadeTo(0f)
            shown = next
            fadeTo(1f)
            delay(ACHIEVEMENTS_LINE_SHOWN_MS)
            fadeTo(0f)
            shown = null
            fadeTo(1f)
        }
    }
    return remember(shown) { ShownAchievements(shown) { alpha.value } }
}

/** The trophy's spoken name when game [key] is mastered, null otherwise (and no string is read for the other rows). */
@Composable
internal inline fun masteredLabel(key: String): String? =
    if (LocalMasteredGames.current.contains(key)) stringResource(R.string.achievement_mastered) else null
