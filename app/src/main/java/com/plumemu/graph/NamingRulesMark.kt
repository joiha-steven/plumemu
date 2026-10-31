package com.plumemu.graph

import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.scan.ScanEvent
import kotlinx.coroutines.flow.Flow

/**
 * Before a scan: a library named by older naming rules ([NAMING_RULES_VERSION] above
 * [storedVersion], the app's `flags.namingRulesVersion`; null, settings not read yet, counts as
 * current) gets the rows of the systems named differently since marked ([mark],
 * `LibraryScanner.nameAgainForNewRules`), then the new version recorded ([record]); the scan
 * that follows names them. True when it marked. Nothing is recorded when [mark] throws, so the
 * next scan tries again (marking twice only names the rows once more).
 */
internal suspend fun markForNamingRules(
    storedVersion: Int?,
    mark: suspend (Int) -> Int,
    record: suspend (Int) -> Unit,
): Boolean {
    if (storedVersion == null || storedVersion >= NAMING_RULES_VERSION) return false
    mark(storedVersion)
    record(NAMING_RULES_VERSION)
    return true
}

/**
 * A library last scanned with older folder-matching rules lists every folder again (unchanged
 * folders would be skipped and keep the games the old rules dropped); otherwise a normal scan.
 * True when it rescanned everything, so the caller records the new version.
 */
internal suspend fun scanForFolderRules(
    storedVersion: Int,
    scan: () -> Flow<ScanEvent>,
    rescanEverything: () -> Flow<ScanEvent>,
    onEvent: (ScanEvent) -> Unit,
): Boolean {
    val stale = storedVersion < FOLDER_MATCH_VERSION
    (if (stale) rescanEverything() else scan()).collect(onEvent)
    return stale
}
