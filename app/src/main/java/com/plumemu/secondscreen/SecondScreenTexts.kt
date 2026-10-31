package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import com.plumemu.R
import com.plumemu.game.emulatorLabel
import com.plumemu.home.SystemFacts
import com.plumemu.home.SystemInfo
import com.plumemu.home.systemFactsOf
import com.plumemu.home.playTime
import com.plumemu.home.rememberCountFormat

/**
 * The facts line: [before], then [cuttable] (a name that may be cut with an ellipsis when the line
 * is too long), then [after], which is always kept.
 */
internal data class Facts(
    val before: AnnotatedString,
    val cuttable: String? = null,
    val after: AnnotatedString = AnnotatedString(""),
)

/** The words of one picking card: the top line, the title, and the facts line (null: no line). */
internal class CardTexts(
    val top: String,
    val title: String,
    val facts: Facts?,
    val isFavorite: Boolean,
    /** The system card's badge text; null: no badge. */
    val badge: String? = null,
    /** The system card's facts line, widest first ([SystemFacts.candidates]); empty: no line. */
    val systemFacts: List<String> = emptyList(),
    /** The system card's about text ([SystemInfo.about], worded); null: none, the block is as it was. */
    val systemAbout: String? = null,
)

/**
 * "Last played <name> · yesterday" from the translated [label] (which holds [NAME_MARK] where the
 * name goes). A translation that leaves the name out gives the sentence alone, once.
 */
internal fun lastGameFacts(
    label: String,
    name: String,
    ago: String?,
    separator: String,
    separatorColor: Color,
): Facts {
    val at = label.indexOf(NAME_MARK)
    val tail = if (at < 0) "" else label.substring(at + NAME_MARK.length)
    val after =
        buildAnnotatedString {
            append(tail)
            if (ago != null) {
                withStyle(SpanStyle(color = separatorColor)) { append(separator) }
                append(ago)
            }
        }
    return if (at < 0) Facts(before = buildAnnotatedString { append(label); append(after) }) else Facts(AnnotatedString(label.substring(0, at)), name, after)
}

/** Stands for the game's title while "Last played %1$s" is split around it, so translators keep their word order. */
internal const val NAME_MARK = "\u0001"

/**
 * A line of [first], the separator and [rest], where [first] is the part cut by measuring when the
 * line is too long and [rest] always stays: the played line ("DuckStation · Played 3 days ago"),
 * the details card's genre line ("Racing · 8/10"). [first] null: [rest] alone.
 */
internal fun cutFirstFacts(
    first: String?,
    rest: AnnotatedString,
    separator: String,
    separatorColor: Color,
): Facts {
    if (first == null) return Facts(rest)
    if (rest.isEmpty()) return Facts(before = AnnotatedString(""), cuttable = first)
    val after =
        buildAnnotatedString {
            withStyle(SpanStyle(color = separatorColor)) { append(separator) }
            append(rest)
        }
    return Facts(before = AnnotatedString(""), cuttable = first, after = after)
}

/** Parts joined by the quiet separator, which is drawn in [separatorColor]. */
internal fun joinFacts(
    parts: List<String>,
    separator: String,
    separatorColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) withStyle(SpanStyle(color = separatorColor)) { append(separator) }
            append(part)
        }
    }

/**
 * [fileName] without its last `.ext`, for the details card's file line: `Silent Hill (USA).chd` gives
 * `Silent Hill (USA)`. Only an extension of 1 to [MAX_EXTENSION] ASCII letters or digits with
 * something before it is dropped, so `Super Mario Bros. 3`, `.hidden` and `name.` stay as they are.
 */
internal fun fileNameWithoutExtension(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0) return fileName
    val extension = fileName.substring(dot + 1)
    val isExtension = extension.length in 1..MAX_EXTENSION && extension.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
    return if (isExtension) fileName.substring(0, dot) else fileName
}

/** The longest extension [fileNameWithoutExtension] drops (`.chd`, `.m3u`, `.7z`, `.steam`). */
private const val MAX_EXTENSION = 5

/**
 * "Played yesterday", "Played 3 days ago": whole sentences, so a translation can order the words its
 * own way (lower-casing the details column's "Yesterday" works only in English).
 */
@Composable
private fun playedAgo(daysAgo: Int): String = when (daysAgo) {
    0 -> stringResource(R.string.second_screen_played_today)
    1 -> stringResource(R.string.second_screen_played_yesterday)
    else -> pluralStringResource(R.plurals.second_screen_played_days_ago, daysAgo, rememberCountFormat().format(daysAgo))
}

/** "yesterday", "3 days ago" after the last game's title on the system card ("Last played Super Metroid · yesterday"). */
@Composable
private fun agoAfterTitle(daysAgo: Int): String = when (daysAgo) {
    0 -> stringResource(R.string.second_screen_ago_today)
    1 -> stringResource(R.string.second_screen_ago_yesterday)
    else -> pluralStringResource(R.plurals.second_screen_ago_days, daysAgo, rememberCountFormat().format(daysAgo))
}

/** The facts line's candidates for [info] (brief 2's words and rules, `home/SystemInfo.kt`), widest first. */
@Composable
private fun systemFactLines(info: SystemInfo): List<String> =
    systemFactsOf(info).candidates(stringResource(R.string.system_facts_separator))

/** Words for [content]; the calm screen, the setup question and the statistics card have none here. */
@Composable
internal fun cardTexts(
    content: SecondScreenContent,
    separatorColor: Color,
): CardTexts? {
    val numbers = rememberCountFormat()
    val separator = stringResource(R.string.second_screen_separator)
    return when (content) {
        is SecondScreenContent.Game -> {
            val parts = mutableListOf<String>()
            // "DuckStation · Played 3 days ago": the emulator A starts, in the Info panel's words. It is
            // the part cut when the line is too long, so the play facts after it always stay.
            val emulator = content.emulator?.let { emulatorLabel(it.toStart) }
            if (content.lastPlayedDaysAgo == null) {
                parts += stringResource(R.string.second_screen_not_played)
            } else {
                parts += playedAgo(content.lastPlayedDaysAgo)
                content.playTimeMinutes?.takeIf { it > 0 }?.let { parts += playTime(it) }
            }
            CardTexts(
                top = listOfNotNull(content.systemName, content.region?.takeIf { it.isNotEmpty() }).joinToString(separator),
                title = content.title,
                // Not read yet (or the read failed): no line, never "Not played yet" before the answer.
                facts = if (content.isPlayKnown) cutFirstFacts(emulator, joinFacts(parts, separator, separatorColor), separator, separatorColor) else null,
                isFavorite = content.isFavorite,
            )
        }
        is SecondScreenContent.System -> {
            val label = stringResource(R.string.second_screen_last_game, NAME_MARK)
            val ago = if (content.lastGameTitle == null) null else content.lastPlayedDaysAgo?.let { agoAfterTitle(it) }
            CardTexts(
                top = pluralStringResource(R.plurals.home_games_count, content.gameCount, numbers.format(content.gameCount)),
                title = content.name,
                facts = content.lastGameTitle?.let { lastGameFacts(label, it, ago, separator, separatorColor) },
                isFavorite = false,
                badge = content.info?.abbr,
                systemFacts = content.info?.let { systemFactLines(it) }.orEmpty(),
                systemAbout = content.info?.about?.let { stringResource(it) },
            )
        }
        is SecondScreenContent.Apps ->
            CardTexts(
                top = pluralStringResource(R.plurals.home_apps_count, content.appCount, numbers.format(content.appCount)),
                title = stringResource(R.string.home_entry_apps),
                facts = null,
                isFavorite = false,
            )
        is SecondScreenContent.App ->
            CardTexts(top = stringResource(R.string.second_screen_android_app), title = content.name, facts = null, isFavorite = false)
        SecondScreenContent.Calm, SecondScreenContent.SetupQuestion, is SecondScreenContent.Statistics -> null
    }
}
