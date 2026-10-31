package com.plumemu.library.corpus

import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.GameFolderRow
import com.plumemu.identify.IdentifyRequest
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.library.catalog.CatalogSettings
import com.plumemu.library.catalog.DirectoryCataloguer
import com.plumemu.library.catalog.GameFolderCopies
import com.plumemu.library.catalog.JunkFilter
import com.plumemu.library.matching.SystemMatcher
import com.plumemu.library.matching.SystemRule
import com.plumemu.library.naming.ArcadeCopies
import com.plumemu.library.scan.DocumentEntry
import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.GameNaming
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanFixture
import com.plumemu.library.scan.TreeResult
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** A game the list shows and every corpus path it stands for (its file, grouped files, collapsed copies). */
internal class ShownGame(val row: GameEntity, val paths: Set<String>)

/** What the scan left in the lists, and how long it took (nanoseconds). */
internal data class CorpusScan(
    val shown: List<ShownGame>,
    val walkNanos: Long,
    val namingNanos: Long,
    val identifyLog: List<String>,
) {
    val totalNanos get() = walkNanos + namingNanos
    val gameOfPath: Map<String, ShownGame> by lazy { shown.flatMap { g -> g.paths.map { it to g } }.toMap() }
}

/**
 * Runs the app's scan over [corpus]: files created under a folder named `ROMs` in [workDir], the
 * real rules and packs, `LibraryScanner.scan()` to the end. The list is read the way `GameDao`'s
 * list queries read it (not hidden, not `is_not_game`, primary copy). Which files a row stands
 * for is not stored, so each directory is catalogued again with the scan's own
 * `DirectoryCataloguer`, which reports what it set aside; the result is checked against the rows
 * the scan wrote. Which copy of an arcade game the pack knows shows is the naming pass's
 * (`ArcadeCopies`, by the pack and the person's marks), not the catalogue's: those rows are grouped
 * again with `ArcadeCopies` over the same pack, and their rows the pass left out are shown under the
 * copy of their group that shows. Arcade sets the pack does not know are the catalogue's, checked
 * like any file.
 */
internal object CorpusRun {
    fun run(corpus: Corpus, workDir: File, rules: List<SystemRule>): CorpusScan {
        val fixture = ScanFixture(File(workDir, "fixture"), copyFixtures = false)
        try {
            createFiles(corpus, fixture.roms)
            val naming = RealData.naming(File(workDir, "packs"))
            val scanner = fixture.scanner(rules = rules, naming = naming.naming)
            var walkEnd = 0L
            val start = System.nanoTime()
            val events = ArrayList<ScanEvent>()
            runBlocking {
                scanner.scan().collect {
                    events += it
                    if (it is ScanEvent.FolderFinished) walkEnd = System.nanoTime()
                }
            }
            val end = System.nanoTime()
            val outcome = events.filterIsInstance<ScanEvent.FolderFinished>().single().outcome
            check(outcome is FolderOutcome.Scanned) { "${corpus.name}: scan failed: $outcome" }
            val rows = runBlocking { fixture.games() }
            // Arcade sets the pack knows are grouped by the pass, never by the catalogue's file-name rule.
            val arcadeGroups = arcadeGroups(rows, naming.naming)
            val owned = arcadeGroups.values.flatten().mapTo(HashSet()) { it.documentId }
            val shown = shownGames(rows, membership(fixture, rules, owned).filterKeys { it !in owned } + arcadeMembership(arcadeGroups))
            return CorpusScan(shown, walkEnd - start, end - walkEnd, naming.log.toList())
        } finally {
            fixture.close()
        }
    }

    /** Corpus paths are compared as NFC: the file system may hand names back in another form. */
    fun key(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC)

    fun createFiles(corpus: Corpus, roms: File) {
        // Android's shared storage and the Mac's disk ignore case: two paths differing only in
        // case would silently become one file.
        val seen = HashMap<String, Int>()
        for (row in corpus.rows) {
            val path = key(row.path)
            seen.put(path.lowercase(Locale.ROOT), row.line)?.let {
                throw CorpusFormatException("${corpus.name}:${row.line}: '${row.path}' is the same file as line $it on a case-blind disk")
            }
            val file = File(roms, path)
            file.parentFile?.mkdirs()
            when (val content = row.content) {
                Content.Empty -> file.writeBytes(ByteArray(0))
                is Content.Zip -> file.writeBytes(zipListing(content.entry, content.crc32, content.size))
            }
        }
    }

    /**
     * A zip whose central directory lists [entry] with [crc32] and uncompressed [size] (all a
     * QUICK read uses); the stored data is an empty deflate stream, so the bytes do not match.
     */
    fun zipListing(entry: String, crc32: Long, size: Long): ByteArray {
        val name = entry.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x03, 0x00)
        val out = ByteArrayOutputStream()
        fun le(n: Int, value: Long) = repeat(n) { out.write(((value ushr (8 * it)) and 0xFF).toInt()) }
        require(size < (1L shl 32)) { "zip entry size $size needs Zip64, not supported by the recipe" }
        // Local header: version, flags (bit 11: UTF-8 name), deflate, time, date, crc, sizes.
        le(4, 0x04034b50); le(2, 20); le(2, 0x0800); le(2, 8); le(2, 0); le(2, 0x21)
        le(4, crc32); le(4, data.size.toLong()); le(4, size); le(2, name.size.toLong()); le(2, 0)
        out.write(name); out.write(data)
        val directoryOffset = out.size().toLong()
        le(4, 0x02014b50); le(2, 20); le(2, 20); le(2, 0x0800); le(2, 8); le(2, 0); le(2, 0x21)
        le(4, crc32); le(4, data.size.toLong()); le(4, size); le(2, name.size.toLong()); le(2, 0); le(2, 0)
        le(2, 0); le(2, 0); le(4, 0); le(4, 0)
        out.write(name)
        val directorySize = out.size() - directoryOffset
        le(4, 0x06054b50); le(2, 0); le(2, 0); le(2, 1); le(2, 1); le(4, directorySize); le(4, directoryOffset); le(2, 0)
        return out.toByteArray()
    }

    /**
     * Document id of every file that has no row of its own or whose row is not a primary copy →
     * the document id it is shown under, from the scan's `DirectoryCataloguer` run again over
     * the same listings with the same folder matches as `FolderScan` (same skipped directories,
     * same paths). Fails if the catalogue disagrees with the rows the scan stored.
     */
    private fun membership(fixture: ScanFixture, rules: List<SystemRule>, arcadeOwned: Set<String>): Map<String, String> {
        val matcher = SystemMatcher(rules)
        val cataloguer = DirectoryCataloguer(matcher, CatalogSettings())
        val tree = fixture.tree
        val shownUnder = HashMap<String, String>()
        val catalogued = HashMap<String, Triple<Boolean, String, String?>>()
        val storedRows = runBlocking { fixture.games() }
        val notGames = storedRows.filter { it.isNotGame }.mapTo(HashSet()) { it.documentId }
        // Every catalogued row as the grouping across sibling game folders reads it (FolderScan.groupGameFolderCopies).
        val folderRows = ArrayList<Pair<String, GameFolderRow>>()
        fun visit(directoryId: String, path: String) {
            val children = (tree.listChildren(directoryId) as TreeResult.Found).value
            val under = HashMap<DocumentEntry, DocumentEntry>()
            val directoryName = if (path.isEmpty()) null else path.substringAfterLast('/')
            val catalogue = cataloguer.catalogue(matcher.matchPath("ROMs", path), children.filter { !it.isDirectory }, directoryName, under)
            catalogue.games.forEach { catalogued[it.entry.documentId] = Triple(it.isPrimaryCopy, it.systemId, it.discGroup) }
            catalogue.games.forEach { game ->
                val row = GameFolderRow(
                    id = folderRows.size.toLong(),
                    parentDocumentId = directoryId,
                    relativePath = if (path.isEmpty()) game.entry.name else "$path/${game.entry.name}",
                    fileName = game.entry.name,
                    fileSortName = game.sortName,
                    fileRegions = game.regions.joinToString(",") { it.code },
                    systemId = game.systemId,
                    isNotGame = game.entry.documentId in notGames,
                    isPrimaryCopy = game.isPrimaryCopy,
                    discCount = game.discCount,
                )
                folderRows += game.entry.documentId to row
            }
            under.forEach { (file, owner) -> shownUnder[file.documentId] = owner.documentId }
            children.filter { it.isDirectory && !JunkFilter.isSkippedDirectory(it.name) }
                // FolderScan.childPath (private there); a different path would show in the check below.
                .forEach { visit(it.documentId, if (path.isEmpty()) it.name else "$path/${it.name}") }
        }
        visit(tree.rootDocumentId, "")
        val decided = GameFolderCopies.decide(folderRows.map { it.second }, CatalogSettings().preferredRegions)
        val shownOfGroup = folderRows.filter { decided[it.second.id] == true }.associate { GameFolderCopies.groupKey(it.second) to it.first }
        for ((documentId, row) in folderRows) {
            val shown = decided[row.id] ?: continue
            catalogued.computeIfPresent(documentId) { _, (_, system, disc) -> Triple(shown, system, disc) }
            if (!shown) shownUnder[documentId] = shownOfGroup.getValue(GameFolderCopies.groupKey(row))
        }
        // Which of the pass's arcade rows show is checked by arcadeMembership, not against the catalogue.
        for (documentId in arcadeOwned) catalogued.computeIfPresent(documentId) { _, (_, system, disc) -> Triple(true, system, disc) }
        val stored = storedRows.associate {
            it.documentId to Triple(it.isPrimaryCopy || it.documentId in arcadeOwned, it.systemId, it.discGroup)
        }
        check(stored == catalogued) {
            val differ = (stored.keys + catalogued.keys).filter { stored[it] != catalogued[it] }.take(5)
            "catalogued again, ${catalogued.size} rows; the scan stored ${stored.size}; first differences " +
                differ.joinToString { "$it scan=${stored[it]} again=${catalogued[it]}" }
        }
        return shownUnder
    }

    /**
     * The arcade rows the naming pass groups (sets the pack knows, not "not a game"), by system,
     * directory and `ArcadeCopies.groupKey`: the same lookups as the pass, by short name.
     */
    private fun arcadeGroups(rows: List<GameEntity>, naming: GameNaming): Map<Triple<String, String, String>, List<GameEntity>> {
        val identify = { systemId: String ->
            { shortName: String -> naming.identifier.identify(IdentifyRequest(systemId, shortName, cleanedTitle = null)) }
        }
        return rows.filter { ArcadeSystems.isArcade(it.systemId) && !it.isNotGame }
            .mapNotNull { row ->
                val found = ArcadeCopies.find(ArcadeSystems.shortNameOf(row.fileName), identify(row.systemId)) as? ArcadeCopies.Found.Known
                found?.let { Triple(row.systemId.lowercase(Locale.ROOT), row.parentDocumentId, ArcadeCopies.groupKey(it.set)) to row }
            }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * Document id of every row of [groups] the pass left out of the lists → the row of its group
     * that shows; fails unless each group has exactly one shown row.
     */
    private fun arcadeMembership(groups: Map<Triple<String, String, String>, List<GameEntity>>): Map<String, String> {
        val shownUnder = HashMap<String, String>()
        for ((key, group) in groups) {
            val primary = group.filter { it.isPrimaryCopy }
            check(primary.size == 1) { "arcade group $key: ${primary.size} shown rows of ${group.map { it.fileName }}" }
            group.filter { !it.isPrimaryCopy }.forEach { shownUnder[it.documentId] = primary.single().documentId }
        }
        return shownUnder
    }

    private fun shownGames(rows: List<GameEntity>, shownUnder: Map<String, String>): List<ShownGame> {
        val shownRows = rows.filter { !it.isHidden && !it.isNotGame && it.isPrimaryCopy }.associateBy { it.documentId }
        val members = HashMap<String, MutableSet<String>>()
        val everyFile = shownUnder.keys + rows.map { it.documentId }
        for (documentId in everyFile) {
            var at = documentId
            var steps = 0
            while (true) {
                at = shownUnder[at] ?: break
                check(++steps < 10) { "membership of $documentId loops" }
            }
            if (at in shownRows) members.getOrPut(at) { HashSet() } += pathOf(documentId)
        }
        return shownRows.values.map { ShownGame(it, members[it.documentId].orEmpty()) }
    }

    /** `FileSystemDocumentTree` ids are `fs:` and the path below `ROMs`. */
    private fun pathOf(documentId: String): String = key(documentId.removePrefix("fs:"))
}
