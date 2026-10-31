package com.plumemu.game

import androidx.compose.runtime.Immutable
import com.plumemu.R
import com.plumemu.identify.name.TitleSearch

/**
 * Where the shown name of a game came from, for Info › "Name from". The identifier's
 * layers ([com.plumemu.identify.Method]) and the person's fixes
 * ([com.plumemu.coredata.db.NameFixKind]) in one list.
 */
internal enum class NameOrigin { ArcadeName, FileContents, GameCode, TitleInFile, NameMatch, FileName, NotReadYet, OwnName, ChosenTitle }

/** How sure the identifier was; null for a person's fix (it is theirs, not a guess). */
internal enum class NameCertainty { Sure, Likely, Unknown }

/**
 * Info's naming facts: [origin] and [certainty] in plain words, [source] the name list it came
 * from (a pack's sources, or the source of a chosen title), [hasFix] whether the person's fix is
 * stored (Back to automatic is offered only then). Names are for the screen, never for a log.
 */
@Immutable
internal data class NameFacts(
    val origin: NameOrigin,
    val certainty: NameCertainty?,
    val source: String?,
    val hasFix: Boolean,
    /** The source is the name pack's: read when Info opens ([GameActions.nameSource]), never for the menu. */
    val sourceInPack: Boolean = false,
)

/** Info's "Name from" block: the origin sentence (with its certainty), then the source line when there is one. */
internal fun nameFromLines(name: NameFacts): List<UiText> {
    val origin = UiText.Res(originText(name.origin))
    val certainty = when (name.certainty) {
        NameCertainty.Sure -> R.string.game_name_certainty_sure
        NameCertainty.Likely -> R.string.game_name_certainty_likely
        NameCertainty.Unknown, null -> null
    }
    val first = if (certainty == null) origin else UiText.Format(R.string.game_name_from_with_certainty, listOf(origin, UiText.Res(certainty)))
    return listOfNotNull(first, name.source?.let { UiText.Res(R.string.game_name_source, listOf(it)) })
}

private fun originText(origin: NameOrigin): Int =
    when (origin) {
        NameOrigin.ArcadeName -> R.string.game_name_from_arcade
        NameOrigin.FileContents -> R.string.game_name_from_contents
        NameOrigin.GameCode -> R.string.game_name_from_code
        NameOrigin.TitleInFile -> R.string.game_name_from_title_in_file
        NameOrigin.NameMatch -> R.string.game_name_from_name_match
        NameOrigin.FileName -> R.string.game_name_from_file_name
        NameOrigin.NotReadYet -> R.string.game_name_from_not_read_yet
        NameOrigin.OwnName -> R.string.game_name_from_own_name
        NameOrigin.ChosenTitle -> R.string.game_name_from_chosen
    }

/**
 * One title of a system's name list as Fix name… shows it: the name, the regions of its releases
 * ("USA, Europe"; empty when the list has none), the year, and the source it is stored with when
 * picked.
 */
@Immutable
internal data class TitleChoice(
    val name: String,
    val regions: String,
    val year: Int?,
    val source: String?,
)

/** A system's titles and the identify module's ranking over them; built off the main thread. */
internal class TitleFinder(private val choices: List<TitleChoice>) {
    private val search = TitleSearch(choices.map { it.name })

    val size: Int get() = choices.size

    /** The best titles for [query], at most [RESULT_LIMIT]; empty for a blank query. [checkpoint] may throw to stop it. */
    fun find(
        query: String,
        checkpoint: () -> Unit = {},
    ): List<TitleChoice> = search.find(query, RESULT_LIMIT, checkpoint).map { choices[it.index] }

    private companion object {
        const val RESULT_LIMIT = 100
    }
}

/** What Fix name… can search for a game. */
@Immutable
internal sealed interface TitleList {
    data class Ready(val finder: TitleFinder) : TitleList

    /** Plumemu ships no name list for the system. */
    data class NoList(val systemName: String) : TitleList

    /** Settings' Fix name…: the game is no longer in the library (removed while the page was opening). */
    data object GameGone : TitleList

    /** A list ships but could not be opened (storage full, a damaged file); logged where it failed. */
    data object Unavailable : TitleList
}

/**
 * Why box art could not be looked up after the person renamed a game without box art
 *; shown right after the rename
 * ([renameBoxArtMessage]). Only for a person with a ScreenScraper account: signed out, box art is
 * off and nothing is said.
 */
internal sealed interface RenameBoxArtNotice {
    /** No unmetered network (Wi-Fi). */
    data object NoWifi : RenameBoxArtNotice

    /** The stored account is refused (or its password no longer decrypts). */
    data object SignInNeeded : RenameBoxArtNotice

    /** The person paused box art in Settings. */
    data object Paused : RenameBoxArtNotice

    /** ScreenScraper's daily limit is reached until [untilMillis]. */
    data class DailyLimit(val untilMillis: Long) : RenameBoxArtNotice

    /** ScreenScraper is closed until [untilMillis]. */
    data class ServerClosed(val untilMillis: Long) : RenameBoxArtNotice
}
