package com.plumemu.library.catalog

import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.fingerprint.nx.SwitchTitleIds
import com.plumemu.identify.name.Region
import java.util.Locale

/**
 * A Switch game's base, update and DLC files in one directory, grouped by the base title id their
 * file names carry: `Game [0100ABCD00010000].xci`, `0100ABCD00010800.nsp` and
 * `Game Expansion Pass [0100ABCD00011001][DLC].nsp` are one game whatever the rest of their names.
 * The id is read by identify's [SwitchTitleIds] (the walk opens no file). The game's row is the base
 * file, the one an emulator runs, whatever its extension or where its name sorts; without a base, the
 * update, else the DLC with the lowest id. Among files of one kind and id (a base kept as `.xci` and
 * `.nsp`, or as `(Japan)` and `(USA, Europe)`) the person's region order decides, as for regional
 * copies ([RegionPreference]), then the shortest name, then the name: a rescan must not move a
 * person's marks from the `(USA)` copy that S4 showed to a `(Japan)` one.
 *
 * Only files of the Switch system, and only ids that are a game, an update or a DLC: 16 hex digits
 * whose low 13 bits are none of those (0, 0x800, above 0x1000) are no title id and group nothing,
 * so two files named `Tool [0123456789AB0123].nsp` and `Kit [0123456789AB0456].nsp` stay two games.
 * Files without an id are left to [DiscGrouping] and the regional-copy grouping, as before.
 */
internal object SwitchGrouping {
    /** Ids of these kinds group; [TitleKind.OTHER] does not. */
    private val GROUPED = listOf(TitleKind.BASE, TitleKind.UPDATE, TitleKind.DLC)

    /** What [take] returns: the shown file of each group, and the files left to the other rules. */
    class Taken<T>(val games: List<MatchedFile<T>>, val rest: List<MatchedFile<T>>)

    /**
     * Takes the Switch files of [files] that carry a title id. Each base id gives one game (its
     * shown file in [Taken.games]); every other file of the group goes into [grouping], mapped to
     * the shown file. A group of one file is still a game of one file. [fileName] is the file's
     * own name, read as it is (not the base name with its disc markers rewritten); [regions] the
     * regions its name gives, ranked by [preferred] as [RegionPreference.primaryCopies] does.
     */
    fun <T> take(
        files: List<MatchedFile<T>>,
        grouping: MutableMap<MatchedFile<T>, MatchedFile<T>>,
        preferred: List<Region>,
        fileName: (T) -> String,
        regions: (MatchedFile<T>) -> List<Region>,
    ): Taken<T> {
        val ids = HashMap<MatchedFile<T>, SwitchTitleIds.Id>()
        for (file in files) {
            if (!SwitchTitleIds.isSwitch(file.systemId)) continue
            val id = SwitchTitleIds.fromFileName(fileName(file.item)) ?: continue
            if (id.kind in GROUPED) ids[file] = id
        }
        if (ids.isEmpty()) return Taken(emptyList(), files)
        val games = ids.keys.groupBy { ids.getValue(it).baseId }.values.map { group ->
            val shown = group.minWith(
                compareBy<MatchedFile<T>>(
                    { GROUPED.indexOf(ids.getValue(it).kind) },
                    { ids.getValue(it).titleId },
                    { RegionPreference.rank(regions(it), preferred) },
                    { fileName(it.item).length },
                    { fileName(it.item).lowercase(Locale.ROOT) },
                ),
            )
            for (file in group) if (file !== shown) grouping[file] = shown
            shown
        }
        return Taken(games, files.filter { it !in ids })
    }
}
