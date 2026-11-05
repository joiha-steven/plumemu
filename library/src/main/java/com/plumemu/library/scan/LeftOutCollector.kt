package com.plumemu.library.scan

import com.plumemu.coredata.db.LeftOutFileDao
import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.coredata.db.LeftOutWrite
import com.plumemu.library.catalog.LeftOutFile
import com.plumemu.library.catalog.LeftOutReason

/**
 * The files one folder's scan left out, as the catalogue reported them for each listed directory.
 * Keeps at most [LeftOutFileDao.MAX_ROWS_PER_REASON] names per reason (a folder of thousands of
 * pictures stays small in memory); the rest are counted per directory and reason.
 */
internal class LeftOutCollector(private val folderId: Long) {
    private val rows = ArrayList<LeftOutFileEntity>()
    private val kept = HashMap<LeftOutReason, Int>()

    /** (directory path, reason) → (system id, files past the cap). */
    private val more = LinkedHashMap<Pair<String, LeftOutReason>, Pair<String?, Int>>()

    private val listed = HashSet<String>()

    private fun forget(directoryPath: String) {
        rows.removeAll { row ->
            (row.directoryPath == directoryPath).also { mine -> if (mine) LeftOutReason.of(row.reason)?.let { kept[it] = (kept[it] ?: 1) - 1 } }
        }
        more.keys.removeAll { it.first == directoryPath }
    }

    fun add(
        directoryPath: String,
        files: List<LeftOutFile>,
    ) {
        // Listed again in the same scan (a directory replaced by a new one at its path): the last listing counts.
        if (directoryPath in listed) forget(directoryPath)
        listed += directoryPath
        for (file in files) {
            val count = kept[file.reason] ?: 0
            if (count < LeftOutFileDao.MAX_ROWS_PER_REASON) {
                kept[file.reason] = count + 1
                rows += LeftOutFileEntity(
                    folderId = folderId,
                    directoryPath = directoryPath,
                    relativePath = FolderScan.childPath(directoryPath, file.entry.name),
                    reason = file.reason.code,
                    systemId = file.systemId,
                )
            } else {
                val key = directoryPath to file.reason
                more[key] = file.systemId to ((more[key]?.second ?: 0) + 1)
            }
        }
    }

    /** What to store for [listedPaths] (every directory listed when [replacesAll]), leaving out directories [isGone] says went. */
    fun write(
        replacesAll: Boolean,
        listedPaths: List<String>,
        isGone: (String) -> Boolean,
    ): LeftOutWrite {
        val counted = more.map { (key, value) ->
            LeftOutFileEntity(
                folderId = folderId,
                directoryPath = key.first,
                relativePath = null,
                reason = key.second.code,
                systemId = value.first,
                more = value.second,
            )
        }
        return LeftOutWrite(replacesAll, listedPaths, (rows + counted).filter { !isGone(it.directoryPath) })
    }
}
