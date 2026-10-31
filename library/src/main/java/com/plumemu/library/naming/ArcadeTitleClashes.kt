package com.plumemu.library.naming

import com.plumemu.coredata.db.ArcadeShownGame
import com.plumemu.identify.arcade.ArcadeTitles
import java.util.Locale

/**
 * Arcade games show short titles ("Zork Fighters" for "Zork Fighters (Japan 961201)", identify's
 * [ArcadeTitles]). Sets of one game in one directory are one row first ([ArcadeCopies]); this
 * looks only at the rows the lists show (`is_primary_copy`). When shown rows of one system in one
 * folder would show the same short title, a bootleg or hack among them shows its full
 * description; the one other row keeps the short title, and when two or more others still share
 * it (different parents, or one game in two directories with different sets) each of them shows
 * its description too, so the person can tell them apart. When the clash is gone, the short
 * title comes back. Copies the lists leave out show the short title. Pure.
 *
 * Copies of one set (the same full description twice) are not a clash: a longer name would not
 * tell them apart either. A group in which any description cannot be looked up is left as it is:
 * not knowing a description is not knowing the sets are the same.
 */
internal object ArcadeTitleClashes {
    /**
     * The titles to change, by game id. [games] are the games that show the identifier's arcade
     * name (a person's fix never comes in); [fullTitleOf] gives a game's full description, or null
     * when it cannot be looked up now (then that game's whole group is left as it is).
     */
    fun changes(games: List<ArcadeShownGame>, fullTitleOf: (ArcadeShownGame) -> String?): Map<Long, String> {
        val changes = HashMap<Long, String>()
        for (game in games.filter { !it.isPrimaryCopy }) {
            val short = ArcadeTitles.shortTitle(game.title)
            if (short != game.title) changes[game.id] = short
        }
        val shown = games.filter { it.isPrimaryCopy }
        val groups = shown.groupBy { it.systemId.lowercase(Locale.ROOT) to ArcadeTitles.shortTitle(it.title).lowercase(Locale.ROOT) }
        for (group in groups.values) {
            val full = if (group.size < 2) emptyMap() else group.associate { it.id to fullTitleOf(it) }
            if (full.values.any { it == null }) continue
            val clash = full.values.distinct().size > 1
            val bootlegs = full.filterValues { ArcadeCopies.isBootlegOrHack(it!!) }.keys
            val othersClash = full.filterKeys { it !in bootlegs }.values.distinct().size > 1
            for (game in group) {
                val describe = clash && (game.id in bootlegs || othersClash)
                val wanted = if (describe) full.getValue(game.id) ?: continue else ArcadeTitles.shortTitle(game.title)
                if (wanted != game.title) changes[game.id] = wanted
            }
        }
        return changes
    }
}
