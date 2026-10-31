package com.plumemu.home

/**
 * The entry the Home button, and the end of first-run setup, land on: Recent
 * when it has games (its first is the last game played, so A plays it again), else the first
 * system in the column's order ([com.plumemu.content.SystemOrder] already arranged it), else null:
 * with no game anywhere the content is empty or not final yet (a scan is running), and the caller
 * waits for a target instead of landing on Apps.
 */
internal fun HomeContent.landingEntryId(): String? =
    entries.firstOrNull { it.kind == EntryKind.Recent && it.count > 0 }?.id
        ?: entries.firstOrNull { it.kind == EntryKind.System && it.count > 0 }?.id
