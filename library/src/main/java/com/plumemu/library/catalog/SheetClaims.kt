package com.plumemu.library.catalog

import com.plumemu.library.scan.DocumentEntry

/**
 * Which files of one directory are parts of a game because a `.cue`, `.gdi` or `.m3u` of the
 * directory names them, whatever the files are called (`Game.cue` naming `Track 01.bin`,
 * `Final Fantasy VII.m3u` naming `FF7 (Disc 1).chd`). Names alone cannot show that; the scan reads
 * the sheets and gives [DirectoryCataloguer] their document ids.
 *
 * A file is claimed by the first sheet (by file name) that names it; a sheet may itself be named
 * by a playlist (m3u → cue → bin). Sheets that name each other claim nothing. Only files of the
 * same system as the sheet are taken from the game rows, and only when the sheet ends up a row
 * itself: otherwise the file stays as it was.
 */
internal class SheetClaims private constructor(
    /** Part document id → the sheet that names it. */
    val claimOf: Map<String, String>,
    private val sheets: Map<String, MatchedFile<DocumentEntry>>,
) {
    fun sheetOf(partId: String): MatchedFile<DocumentEntry> = sheets.getValue(claimOf.getValue(partId))

    private fun rootOf(id: String): String {
        var at = id
        while (true) at = claimOf[at] ?: return at
    }

    /**
     * [rows] without the games that are parts of a sheet that is a row; each such game is recorded
     * in [grouping] under its sheet. A playlist's disc count grows by the discs it took.
     */
    fun take(
        rows: List<GroupedGame<DocumentEntry>>,
        grouping: MutableMap<MatchedFile<DocumentEntry>, MatchedFile<DocumentEntry>>,
    ): List<GroupedGame<DocumentEntry>> {
        if (claimOf.isEmpty()) return rows
        val rowIds = rows.mapTo(HashSet()) { it.file.item.documentId }
        val kept = ArrayList<GroupedGame<DocumentEntry>>()
        val takenDiscs = HashMap<String, Int>()
        for (row in rows) {
            val id = row.file.item.documentId
            val sheet = claimOf[id]?.let(sheets::getValue)
            val root = rootOf(id)
            if (sheet == null || root !in rowIds || sheet.systemId != row.file.systemId) {
                kept += row
                continue
            }
            grouping[row.file] = sheet
            // Only what the playlist itself names is a disc of it; a cue's bins are not.
            if (sheet.item.documentId == root) takenDiscs[root] = (takenDiscs[root] ?: 0) + row.discCount
        }
        return kept.map { row ->
            val extra = takenDiscs[row.file.item.documentId] ?: return@map row
            if (row.file.extension != "m3u") return@map row
            // A playlist that matched no disc by name already counts one disc: the larger count wins.
            GroupedGame(row.file, row.file.baseName, maxOf(row.discCount, extra))
        }
    }

    companion object {
        val EXTENSIONS = setOf("cue", "gdi", "m3u")

        fun of(
            sheetParts: Map<String, Set<String>>,
            matchedSheets: List<MatchedFile<DocumentEntry>>,
            directoryIds: Set<String>,
        ): SheetClaims {
            if (sheetParts.isEmpty()) return SheetClaims(emptyMap(), emptyMap())
            val sheets = matchedSheets.associateBy { it.item.documentId }
            val claim = HashMap<String, String>()
            for (sheet in matchedSheets.sortedBy { it.item.name }) {
                val id = sheet.item.documentId
                for (part in sheetParts[id].orEmpty()) {
                    if (part != id && part in directoryIds && part !in claim) claim[part] = id
                }
            }
            // Sheets that name each other (or a longer ring) claim nothing.
            for (start in claim.keys.toList()) {
                val seen = HashSet<String>()
                var at: String? = start
                while (at != null && seen.add(at)) at = claim[at]
                if (at != null) claim.keys.removeAll(seen)
            }
            return SheetClaims(claim, sheets)
        }
    }
}
