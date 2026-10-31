package com.plumemu.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.platforms.GameSystem
import com.plumemu.platforms.SystemKind

/**
 * What the band over a system's games list shows: the badge's
 * [abbr] and the facts line's parts, all from the platform data and all optional but the badge's name.
 */
@Immutable
data class SystemInfo(
    val abbr: String,
    val maker: String? = null,
    val kind: SystemKind? = null,
    val year: Int? = null,
    val endYear: Int? = null,
    val inProduction: Boolean = false,
    /** The few lines about the system ([SystemAbouts]); null for a system without any. */
    @StringRes val about: Int? = null,
    /** True for arcade systems with no one maker: the facts line says "Arcade games from many makers" in the maker's place. */
    val manyMakers: Boolean = false,
)

/** The band's info for [system], or null when it has no short name to put in the badge. */
fun systemInfoOf(system: GameSystem): SystemInfo? =
    system.abbr.takeIf { it.isNotBlank() }?.let {
        SystemInfo(
            it, system.maker, system.kind, system.year, system.endYear, system.inProduction,
            about = SystemAbouts.of(system.id),
            manyMakers = system.maker == null && system.id in SystemAbouts.manyMakerIds,
        )
    }

/** The kind's words ("Home console"), a string resource so they translate. */
@StringRes
internal fun SystemKind.wordRes(): Int =
    when (this) {
        SystemKind.Console -> R.string.system_kind_console
        SystemKind.Handheld -> R.string.system_kind_handheld
        SystemKind.Arcade -> R.string.system_kind_arcade
        SystemKind.Computer -> R.string.system_kind_computer
        SystemKind.AddOn -> R.string.system_kind_add_on
        SystemKind.Os -> R.string.system_kind_os
    }

/**
 * The facts line's parts, already worded: [maker], [kind] word and [years] ("1990–2003", "2017–present",
 * or the launch year alone), each null when the data has none.
 */
internal class SystemFacts(
    val maker: String?,
    val kind: String?,
    val years: String?,
    /** Stands where the maker would, for a system with none ("Arcade games from many makers"). */
    val noMaker: String? = null,
) {
    /**
     * The line to show, widest first: all parts, then without the kind, then without the maker too
     * (the line never wraps; the screen takes the first that fits). A missing part leaves out its
     * separator. Empty when there are no facts at all (the badge stands alone).
     */
    fun candidates(separator: String): List<String> =
        (maker ?: noMaker).let { first -> listOf(listOf(first, kind, years), listOf(first, years), listOf(years)) }
            .map { parts -> parts.filterNotNull().joinToString(separator) }
            .filter { it.isNotEmpty() }
            .distinct()

    companion object {
        /** Years as the line writes them: needs [present] for an unfinished range ("present"). */
        fun yearsOf(
            info: SystemInfo,
            present: String,
        ): String? {
            val from = info.year ?: return null
            return when {
                info.inProduction -> "$from–$present"
                info.endYear != null && info.endYear != from -> "$from–${info.endYear}"
                else -> "$from"
            }
        }
    }
}

/** The facts line's parts for [info], worded in the current language. */
@Composable
internal fun systemFactsOf(info: SystemInfo): SystemFacts =
    SystemFacts(
        maker = info.maker,
        kind = info.kind?.let { stringResource(it.wordRes()) },
        years = SystemFacts.yearsOf(info, stringResource(R.string.system_years_present)),
        noMaker = if (info.manyMakers) stringResource(R.string.system_facts_many_makers) else null,
    )

/** The first of [lines] (widest first) that [fits], else the last: the facts line never wraps. */
internal fun firstThatFits(
    lines: List<String>,
    fits: (String) -> Boolean,
): String = lines.firstOrNull(fits) ?: lines.last()
