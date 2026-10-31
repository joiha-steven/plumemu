package com.plumemu.library.catalog

import com.plumemu.identify.name.TitleCleaner
import java.util.Locale

/** A file that passed matching, before discs are grouped. */
internal data class MatchedFile<T>(
    val item: T,
    val baseName: String,
    val extension: String,
    val systemId: String,
)

/** A file that is shown as one game; [discGroup] is set when it stands for several discs. */
internal data class GroupedGame<T>(
    val file: MatchedFile<T>,
    val discGroup: String?,
    val discCount: Int,
)

/**
 * Multi-disc and multi-file games in one directory, by file names only (a scan never reads file
 * contents). Base names arrive with their disc markers already
 * in the `(Disc N)` form ([DiscMarkers]):
 * - a `.cue` hides the `.bin` files of the same name (and its `(Track N)` files); `.ccd` likewise
 *   hides `.img` and `.sub`, `.mds` hides `.mdf`; a `.gdi` hides its tracks whatever their
 *   extension (`.bin`, `.raw`): the `trackNN.*` files beside it and its own `(Track N)` files. The
 *   names are what a `.gdi` lists; the walk does not open it to check (identification reads it
 *   later, after the walk);
 * - an `.m3u` hides the files of the same name without their disc tag (`Game (USA).m3u` hides
 *   `Game (USA) (Disc 1).cue` and its bins);
 * - in one game's own folder (the caller says so: never a system's folder or the picked folder),
 *   a playlist whose name matches nothing still takes the folder's discs when it is the only
 *   playlist and everything else of its system is one disc set (two or more disc numbers under
 *   one disc base): `FF7/FF7.m3u` beside `Final Fantasy VII (USA) (Disc 1..3).chd`. A lone file,
 *   two games or a second playlist beside it, and it takes nothing:
 *   `PS1/Final Fantasy VII (USA).m3u` beside `PS1/Crash Bandicoot (USA).chd` stays two games;
 * - files left with a disc tag and the same name otherwise become one game on the lowest disc.
 *
 * A game's disc count is the number of distinct disc numbers it stands for, for a disc set and
 * an `.m3u` alike (a disc kept as `.cue` and `.chd` is still one disc).
 *
 * [group]'s `shownUnder`, when given, receives every file that is not shown on its own, mapped to
 * the file that set it aside (a bin to its cue, a cue to its m3u, a disc to the first disc of its
 * set). The scan passes none; the corpus harness (library tests) reads which files a game stands for.
 */
internal object DiscGrouping {
    private val trackOwners = mapOf(
        "cue" to setOf("bin", "img", "wav", "iso"),
        "ccd" to setOf("img", "sub"),
        "mds" to setOf("mdf"),
    )

    /**
     * Extensions a `.gdi` lists its tracks with: data `.bin` (old dumps `.iso`), audio `.raw`
     * (converted dumps `.wav`). Nothing else: `Crazy Taxi (USA).chd` or `.cdi` beside
     * `Crazy Taxi (USA).gdi` is another copy of the game, not a track.
     */
    private val gdiTrackExtensions = setOf("bin", "raw", "wav")

    // Dreamcast dumps: a .gdi lists track01.bin, track02.raw, ... whatever the .gdi is called.
    private val gdiTrack = Regex("""track\d+""", RegexOption.IGNORE_CASE)

    /**
     * Whether a file named [baseName] with [extension] is a track of a `.gdi` in a directory whose
     * `.gdi` files have the [gdiKeys] (`TitleCleaner.key` of their base names): `track03.raw` or
     * `track03.iso` beside any `.gdi`, `Game (Track 3).bin` or `Game.bin` beside `Game.gdi`
     * ([gdiTrackExtensions] only). Lets the cataloguer keep tracks whose extension no system takes
     * (`.raw`) with their game instead of counting them unmatched.
     */
    fun isGdiTrack(baseName: String, extension: String, gdiKeys: Set<String>): Boolean = gdiKeys.isNotEmpty() && when {
        gdiTrack.matches(baseName) -> extension in gdiTrackExtensions || extension == "iso"
        else -> extension in gdiTrackExtensions &&
            (TitleCleaner.trackBaseKey(baseName) in gdiKeys || TitleCleaner.key(baseName) in gdiKeys)
    }

    fun <T> group(
        files: List<MatchedFile<T>>,
        shownUnder: MutableMap<MatchedFile<T>, MatchedFile<T>>? = null,
        inOneGamesFolder: Boolean = false,
    ): List<GroupedGame<T>> {
        // Keys computed once per file: a PlayStation folder can hold thousands of cue and bin files.
        val keyed = files.map {
            Keyed(it, TitleCleaner.key(it.baseName), TitleCleaner.trackBaseKey(it.baseName), TitleCleaner.discBaseKey(it.baseName))
        }
        val hidden = HashSet<MatchedFile<T>>()
        hideTracks(keyed, hidden, shownUnder)

        val byDiscBase = keyed.filter { it.file.extension != "m3u" }.groupBy { it.file.systemId to it.discBase }
        val playlists = keyed.filter { it.file.extension == "m3u" }
        val playlistDiscs = HashMap<MatchedFile<T>, Int>()
        val namedPlaylists = HashSet<MatchedFile<T>>()
        for (playlist in playlists) {
            val discs = byDiscBase[playlist.file.systemId to playlist.key].orEmpty().map { it.file }
            if (discs.isNotEmpty()) namedPlaylists += playlist.file
            hidden += discs
            if (shownUnder != null) for (disc in discs) shownUnder.getOrPut(disc) { playlist.file }
            playlistDiscs[playlist.file] = discCount(discs)
        }

        val shown = files.filter { it !in hidden }
        val (tagged, single) = shown.partition { it.extension != "m3u" && TitleCleaner.hasDiscTag(it.baseName) }
        val discSets = tagged.groupBy { it.systemId to TitleCleaner.discBaseKey(it.baseName) }.values.map { set ->
            val first = set.minWith(
                compareBy({ discTag(it)?.number ?: Int.MAX_VALUE }, { it.baseName.lowercase(Locale.ROOT) }),
            )
            DiscSet(first, set)
        }
        val loneTaken = if (inOneGamesFolder) lonePlaylistTakes(playlists.map { it.file }, namedPlaylists, single, discSets) else LoneTaken(null, emptySet(), null, 0)
        val games = single.filter { it !in loneTaken.files }.map { file ->
            val discs = playlistDiscs[file]
            if (discs == null) GroupedGame(file, null, 1) else GroupedGame(file, file.baseName, maxOf(discs, 1))
        }
        val shownSets = discSets.filter { it !== loneTaken.set }.map { set ->
            if (shownUnder != null) for (disc in set.files) if (disc !== set.first) shownUnder.getOrPut(disc) { set.first }
            GroupedGame(set.first, TitleCleaner.withoutDiscTag(set.first.baseName), maxOf(discCount(set.files), 1))
        }
        val playlist = loneTaken.playlist ?: return games + shownSets
        if (shownUnder != null) for (file in loneTaken.files) shownUnder.getOrPut(file) { playlist }
        val taken = GroupedGame(playlist, playlist.baseName, maxOf(loneTaken.discs, 1))
        return games.filter { it.file !== playlist } + taken + shownSets
    }

    private class DiscSet<T>(val first: MatchedFile<T>, val files: List<MatchedFile<T>>)

    private class LoneTaken<T>(
        val playlist: MatchedFile<T>?,
        val files: Set<MatchedFile<T>>,
        val set: DiscSet<T>?,
        val discs: Int,
    )

    /**
     * The disc set a playlist that matched no file by name stands for, in one game's folder: when
     * it is the folder's only playlist and every other shown file of its system is one disc set.
     * A lone file, two games or a second playlist beside it, and it takes nothing.
     */
    private fun <T> lonePlaylistTakes(
        playlists: List<MatchedFile<T>>,
        namedPlaylists: Set<MatchedFile<T>>,
        single: List<MatchedFile<T>>,
        discSets: List<DiscSet<T>>,
    ): LoneTaken<T> {
        val none = LoneTaken<T>(null, emptySet(), null, 0)
        val playlist = playlists.singleOrNull() ?: return none
        if (playlist in namedPlaylists) return none
        val system = playlist.systemId
        val otherSingles = single.filter { it !== playlist && it.systemId == system }
        val sets = discSets.filter { it.first.systemId == system }
        if (otherSingles.isNotEmpty() || sets.size != 1) return none
        val set = sets.single()
        // A set is two or more discs: one disc-tagged file beside the playlist may be anything.
        val discs = discCount(set.files)
        if (discs < 2) return none
        return LoneTaken(playlist, set.files.toSet(), set, discs)
    }

    /** Distinct disc numbers among [files]; 0 when none has a disc tag. */
    private fun discCount(files: List<MatchedFile<*>>): Int = files.mapNotNull { discTag(it)?.number }.distinct().size

    private class Keyed<T>(val file: MatchedFile<T>, val key: String, val trackBase: String, val discBase: String)

    private fun <T> hideTracks(
        files: List<Keyed<T>>,
        hidden: MutableSet<MatchedFile<T>>,
        shownUnder: MutableMap<MatchedFile<T>, MatchedFile<T>>?,
    ) {
        val byKey = files.groupBy { it.file.systemId to it.key }
        val byTrackBase = files.groupBy { it.file.systemId to it.trackBase }
        val gdis = files.filter { it.file.extension == "gdi" }
        for ((system, owners) in gdis.groupBy { it.file.systemId }) {
            val keys = owners.mapTo(HashSet()) { it.key }
            files.filter { it.file.systemId == system && isGdiTrack(it.file.baseName, it.file.extension, keys) }.forEach { track ->
                hidden += track.file
                // Its own `(Track N)` names point at one .gdi; `trackNN` names at the directory's first.
                val owner = owners.firstOrNull { it.key == track.trackBase } ?: owners.first()
                shownUnder?.getOrPut(track.file) { owner.file }
            }
        }
        for (owner in files) {
            val ownedExtensions = trackOwners[owner.file.extension] ?: continue
            val id = owner.file.systemId to owner.key
            (byKey[id].orEmpty() + byTrackBase[id].orEmpty())
                .filter { it !== owner && it.file.extension in ownedExtensions }
                .forEach { track ->
                    hidden += track.file
                    shownUnder?.getOrPut(track.file) { owner.file }
                }
        }
    }

    private fun discTag(file: MatchedFile<*>) = TitleCleaner.clean(file.baseName).disc
}
