package com.plumemu.game

import androidx.annotation.StringRes
import com.plumemu.R
import com.plumemu.coredata.settings.SortGamesSetting

/**
 * The sorts a person can pick now: every [SortGamesSetting] in its declared order, except Year while
 * no listed game has a year from ScreenScraper and Rating while none has a rating (never an empty
 * choice). The sort in use is always listed, so a Year or Rating picked earlier does not vanish from
 * the list when the values do. The games list's "Sort by…" offers it (Settings has no Sort games row).
 */
internal data class SortChoices(
    val current: SortGamesSetting,
    val hasYear: Boolean,
    val hasRating: Boolean,
) {
    val options: List<SortGamesSetting> =
        SortGamesSetting.entries.filter { sort ->
            when (sort) {
                SortGamesSetting.Year -> hasYear || current == sort
                SortGamesSetting.Rating -> hasRating || current == sort
                else -> true
            }
        }
}

/** The choices panel for [game] over [from] (null: alone, B closes it), the sort in use focused. */
internal fun SortChoices.panel(game: GameRef, from: OpenPanel?) =
    OpenPanel(game, PanelScreen.SortMenu(this), focus = options.indexOf(current).coerceAtLeast(0), parent = from)

/** The name a sort goes by, in Settings and in "Sort by…". */
@StringRes
internal fun SortGamesSetting.nameRes(): Int =
    when (this) {
        SortGamesSetting.Name -> R.string.settings_sort_by_name
        SortGamesSetting.LastPlayed -> R.string.settings_sort_by_last_played
        SortGamesSetting.MostPlayed -> R.string.settings_sort_by_most_played
        SortGamesSetting.RecentlyAdded -> R.string.settings_sort_by_recently_added
        SortGamesSetting.Year -> R.string.settings_sort_by_year
        SortGamesSetting.Rating -> R.string.settings_sort_by_rating
    }

/**
 * What the games list's "Sort by…" asks of the app: a seam so the screen's tests run on a fake.
 * Production: `GraphSortActions`.
 */
internal interface SortActions {
    /** The sorts on offer and the one in use, read when the choices open. */
    suspend fun choices(): SortChoices

    /** Stores [sort] as the one stored sort; the lists follow it. Throws when it could not be stored. */
    suspend fun set(sort: SortGamesSetting)

    /** Benchmark and preview builds: Name only, nothing stored. */
    object None : SortActions {
        override suspend fun choices() = SortChoices(SortGamesSetting.Name, hasYear = false, hasRating = false)

        override suspend fun set(sort: SortGamesSetting) = Unit
    }
}
