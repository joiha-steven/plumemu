package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.graph.ScanPhase
import com.plumemu.home.rememberCountFormat
import com.plumemu.library.scan.UnavailableReason
import com.plumemu.setup.folderLabel

/*
 * Rescan library's panel text: the counts while the scan runs, from its events, and
 * what it did once it ended. The graph keeps the numbers (`ScanReport`); this turns them into the
 * lines the panel shows.
 */

/** The panel's lines for [content]: progress while a scan runs, else the last result, else none. */
@Composable
internal fun rescanLines(content: SettingsContent): List<String> {
    val scan = content.scan
    val format = rememberCountFormat()
    if (content.isScanning) {
        val line =
            when (scan.phase) {
                ScanPhase.Naming ->
                    if (scan.gamesToName == 0) {
                        stringResource(R.string.settings_rescan_naming_count, format.format(scan.gamesNamed))
                    } else {
                        stringResource(R.string.settings_rescan_naming, format.format(scan.gamesNamed), format.format(scan.gamesToName))
                    }
                ScanPhase.Reading ->
                    stringResource(R.string.settings_rescan_reading, pluralStringResource(R.plurals.settings_rescan_files, scan.filesSeen, format.format(scan.filesSeen)))
                // A pass queued behind another one shows no phase yet.
                ScanPhase.Starting, ScanPhase.Idle -> stringResource(R.string.settings_rescan_starting)
            }
        return listOf(line)
    }
    val result = scan.result ?: return emptyList()
    if (result.failed) return listOf(stringResource(R.string.settings_rescan_failed))
    val lines = ArrayList<String>()
    if (result.scannedFolders > 0 || result.unavailable.isEmpty()) {
        val separator = stringResource(R.string.settings_rescan_part_separator)
        val systems = content.systemCount
        val parts = ArrayList<String>()
        parts += stringResource(R.string.settings_rescan_done)
        parts +=
            if (content.gameCount == 0) {
                stringResource(R.string.settings_rescan_no_games)
            } else {
                stringResource(
                    R.string.settings_rescan_in_systems,
                    pluralStringResource(R.plurals.home_games_count, content.gameCount, format.format(content.gameCount)),
                    pluralStringResource(R.plurals.home_systems_count, systems, format.format(systems)),
                )
            }
        if (result.gamesAdded > 0) parts += pluralStringResource(R.plurals.settings_rescan_added, result.gamesAdded, format.format(result.gamesAdded))
        if (result.gamesRemoved > 0) parts += pluralStringResource(R.plurals.settings_rescan_removed, result.gamesRemoved, format.format(result.gamesRemoved))
        if (result.gamesAdded == 0 && result.gamesRemoved == 0) parts += stringResource(R.string.settings_rescan_no_changes)
        lines += parts.joinToString(separator)
    }
    for (folder in result.unavailable) {
        val name = content.folder(folder.folderId)?.let { folderLabel(it) } ?: stringResource(R.string.settings_rescan_a_folder)
        val text =
            when (folder.reason) {
                UnavailableReason.PERMISSION_REVOKED -> R.string.settings_rescan_revoked
                UnavailableReason.FOLDER_MISSING -> R.string.settings_rescan_missing
                UnavailableReason.READ_FAILED -> R.string.settings_rescan_read_failed
            }
        lines += stringResource(text, name)
    }
    return lines
}
