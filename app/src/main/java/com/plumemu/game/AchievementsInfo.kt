package com.plumemu.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.AchievementLine
import com.plumemu.graph.AchievementsError
import com.plumemu.graph.AchievementsState
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/*
 * Info's RetroAchievements section. It sits under the facts, inside their
 * scroll, so Up and Down step through it row by row (`FactsRows`) and a finger scrolls it. Signed out
 * there is no state at all (`GameFacts.achievements` is null) and nothing here is composed.
 */

/** Where the section's rows start in `FactsRows`' numbering: after every fact, name and description row, which count up from 0. */
internal const val ACHIEVEMENT_ROWS_FROM = 10_000

/**
 * The section for [state]; nothing for the states that mean "no section" ([hasAchievementsSection]).
 * [place] is the scroll's modifier for row [ACHIEVEMENT_ROWS_FROM] + n.
 */
@Composable
internal fun AchievementsSection(
    state: AchievementsState,
    place: (index: Int) -> Modifier,
) {
    if (!hasAchievementsSection(state)) return
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(
        modifier = Modifier.padding(start = PlumDimens.gameRowPadding, end = PlumDimens.gameRowPadding, top = PlumDimens.panelBlockGap).testTag(GamePanelTags.ACHIEVEMENTS),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.achievementRowGap),
    ) {
        val quiet = quietLineOf(state)
        if (quiet != null) {
            BasicText(
                text = stringResource(quiet),
                style = type.meta.copy(color = colors.muted),
                modifier = place(ACHIEVEMENT_ROWS_FROM).testTag(GamePanelTags.ACHIEVEMENTS_LINE),
            )
            return@Column
        }
        val ready = state as AchievementsState.Ready
        Column(place(ACHIEVEMENT_ROWS_FROM).testTag(GamePanelTags.ACHIEVEMENTS_SUMMARY), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
            val shown = ready.awardedSoftcore.coerceAtLeast(ready.awardedHardcore)
            BasicText(
                text = stringResource(R.string.achievements_summary, shown, ready.total, achievementPercent(shown, ready.total)),
                style = type.meta.copy(color = colors.text2),
            )
            if (ready.awardedHardcore > 0) {
                BasicText(text = stringResource(R.string.achievements_hardcore, ready.awardedHardcore, ready.total), style = type.meta.copy(color = colors.muted))
            }
            if (ready.matchedByName) {
                BasicText(text = stringResource(R.string.achievements_matched_by_name), style = type.meta.copy(color = colors.muted), modifier = Modifier.testTag(GamePanelTags.ACHIEVEMENTS_BY_NAME))
            }
        }
        ready.achievements.forEachIndexed { index, line ->
            AchievementRow(line, Modifier.then(place(ACHIEVEMENT_ROWS_FROM + 1 + index)).testTag(GamePanelTags.achievement(index)))
        }
    }
}

/** Whether Info draws anything for [state]: not signed out, and not a system RetroAchievements has no console for. */
internal fun hasAchievementsSection(state: AchievementsState): Boolean =
    when (state) {
        AchievementsState.NotSignedIn, AchievementsState.NotSupported -> false
        else -> true
    }

/** The string of the one quiet line a state without a list shows; null for a set to list. */
internal fun quietLineOf(state: AchievementsState): Int? =
    when (state) {
        AchievementsState.Loading -> R.string.achievements_loading
        AchievementsState.NoSet -> R.string.achievements_no_set
        is AchievementsState.CannotHash -> R.string.achievements_cannot_hash
        is AchievementsState.Error ->
            when (state.kind) {
                AchievementsError.WrongKey -> R.string.achievements_error_wrong_key
                AchievementsError.NoConnection -> R.string.achievements_error_no_connection
                AchievementsError.Busy -> R.string.achievements_error_busy
                AchievementsError.RateLimited -> R.string.achievements_error_rate_limited
                AchievementsError.BadAnswer -> R.string.achievements_error_bad_answer
                AchievementsError.GameUnavailable -> R.string.achievements_error_unavailable
            }
        // A game RetroAchievements lists with no achievements: the same quiet line as an unknown one.
        is AchievementsState.Ready -> if (state.total <= 0) R.string.achievements_no_set else null
        AchievementsState.NotSignedIn, AchievementsState.NotSupported -> null
    }

/** [done] of [total] as a whole percent, rounded to the nearest (12 of 45 is 27); 100 only when every one is done, 0 only when none is; 0 for an empty set. */
internal fun achievementPercent(
    done: Int,
    total: Int,
): Int {
    if (total <= 0) return 0
    val shown = done.coerceIn(0, total)
    if (shown == total) return PERCENT_FULL
    return ((shown * PERCENT_FULL.toDouble() / total).roundToInt()).coerceIn(if (shown > 0) 1 else 0, PERCENT_FULL - 1)
}

private const val PERCENT_FULL = 100

@Composable
private fun AchievementRow(
    line: AchievementLine,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val earned = line.isEarned
    Row(modifier) {
        Badge(url = if (earned) line.badgeUrl else line.lockedBadgeUrl, isLocked = !earned)
        Spacer(Modifier.width(PlumDimens.factsColumnGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
            BasicText(
                text = line.title,
                style = type.meta.copy(color = if (earned) colors.text else colors.muted),
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = line.description,
                style = type.smallFact.copy(color = if (earned) colors.text2 else colors.muted),
                maxLines = DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(text = pointsLine(line), style = type.smallFact.copy(color = colors.muted), maxLines = POINTS_LINES, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "50 points", or "50 points · Earned 30 Oct 2026" (with "· Hardcore" for a hardcore unlock) once unlocked. */
@Composable
private fun pointsLine(line: AchievementLine): String {
    val points = pluralStringResource(R.plurals.achievement_points, line.points, line.points)
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val hardcore = line.earnedHardcoreAt != null
    val unlockedOn = earnedDate(line.earnedHardcoreAt ?: line.earnedAt, locale) ?: return points
    return stringResource(if (hardcore) R.string.achievement_earned_hardcore else R.string.achievement_earned, points, unlockedOn)
}

/**
 * RetroAchievements' time text (date then time, in UTC) as a date in the person's language and time
 * zone; null for no text. Text it cannot read is shown as its first ten characters rather than hidden.
 */
internal fun earnedDate(
    text: String?,
    locale: Locale,
    zone: TimeZone = TimeZone.getDefault(),
): String? {
    if (text.isNullOrBlank()) return null
    val parsed =
        try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(text.trim())
        } catch (_: ParseException) {
            null
        } ?: return text.trim().take(DATE_TEXT_LENGTH)
    // No-break spaces: a wrapping line moves the whole date, never leaves "2026" alone.
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone }.format(parsed).replace(' ', NO_BREAK_SPACE)
}

private const val NO_BREAK_SPACE = '\u00A0'

/**
 * One badge, [PlumDimens.achievementBadgeSize] square. A locked one is dimmed. While it loads, and when it
 * cannot be fetched, a plain `sel` square stands in: no error, no crash, and the row keeps its size.
 */
@Composable
private fun Badge(
    url: String?,
    isLocked: Boolean,
) {
    val images = LocalBadgeImages.current
    val size = PlumDimens.achievementBadgeSize
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    var bitmap: ImageBitmap? by remember(url, sizePx) { mutableStateOf(url?.let { images.cached(it, sizePx) }) }
    if (url != null && bitmap == null) {
        LaunchedEffect(url, sizePx) { bitmap = images.load(url, sizePx) }
    }
    val shape = RoundedCornerShape(PlumDimens.cornerBadge)
    val dim = if (isLocked) Modifier.alpha(PlumDimens.LOCKED_BADGE_ALPHA) else Modifier
    val shown = bitmap
    Box(Modifier.size(size).then(dim).testTag(GamePanelTags.ACHIEVEMENT_BADGE).then(if (shown == null) Modifier.background(PlumTheme.colors.sel, shape) else Modifier)) {
        if (shown != null) Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    }
}

private const val TITLE_LINES = 3
private const val DESCRIPTION_LINES = 6
private const val POINTS_LINES = 2
private const val DATE_TEXT_LENGTH = 10
