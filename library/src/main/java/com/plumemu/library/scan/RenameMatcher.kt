package com.plumemu.library.scan

import com.plumemu.coredata.db.StoredGame
import com.plumemu.identify.name.TitleCleaner
import java.util.Locale

/**
 * Finds files that moved or were renamed: a row about to be deleted and a row inserted by this
 * scan or by an earlier scan that did not finish, that are the same game. ExternalStorageProvider's
 * document ids are paths, so renaming `PS1/` to `psx/` or adding `Game.m3u` beside
 * `Game (Disc 1).cue` gives new ids; matching them keeps the old row and with it the favourite,
 * hidden mark, play time and emulator choice.
 *
 * Tried in order, each only where exactly one removed and one inserted row share the key (an
 * ambiguous key matches nothing rather than the wrong game):
 * 1. same system, file name and size (a folder moved or renamed);
 * 2. same system and multi-disc group name (an `.m3u` now stands for the discs);
 * 3. same directory, system, sort name and variant tags (a file renamed, region or dump marks
 *    changed; an `.m3u` over a `.cue`). The variant tags keep a hack, translation or revision
 *    (`[h1]`, `[T+Eng]`, `(Rev 1)`) from taking the original's marks.
 * Same directory and size alone is not a rename: cartridge sizes are powers of two, so two
 * different games often share one. Pure.
 */
internal object RenameMatcher {
    data class Match(val removed: StoredGame, val inserted: StoredGame)

    private val keys: List<(StoredGame) -> Any?> = listOf(
        { g -> Triple(g.fields.systemId, g.fields.fileName.lowercase(Locale.ROOT), g.fields.sizeBytes) },
        { g -> g.fields.discGroup?.let { g.fields.systemId to it.lowercase(Locale.ROOT) } },
        { g -> listOf(g.fields.parentDocumentId, g.fields.systemId, g.fields.sortName, variantKey(g)) },
    )

    /** The variant key is not stored; it comes from the file name the same way the cataloguer gets it. */
    private fun variantKey(game: StoredGame): String =
        TitleCleaner.clean(game.fields.fileName.substringBeforeLast('.')).variantKey

    fun match(removed: List<StoredGame>, inserted: List<StoredGame>): List<Match> {
        if (removed.isEmpty() || inserted.isEmpty()) return emptyList()
        val leftRemoved = removed.toMutableList()
        val leftInserted = inserted.toMutableList()
        val matches = mutableListOf<Match>()
        for (key in keys) {
            val removedByKey = leftRemoved.groupBy(key)
            val insertedByKey = leftInserted.groupBy(key)
            for ((value, candidates) in removedByKey) {
                if (value == null) continue
                val partners = insertedByKey[value] ?: continue
                if (candidates.size != 1 || partners.size != 1) continue
                matches += Match(candidates.single(), partners.single())
                leftRemoved -= candidates.single()
                leftInserted -= partners.single()
            }
        }
        return matches
    }
}
