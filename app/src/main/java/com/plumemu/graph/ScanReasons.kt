package com.plumemu.graph

import com.plumemu.telemetry.ScanTrigger

/**
 * Why the next pass over every folder runs. The first reason asked wins (`library_scanned.trigger`), but a folder-added
 * request is remembered even when another reason came first, so the pass tells the home about the new folder.
 * Not thread-safe: the caller holds its scan lock.
 */
internal class ScanReasons {
    private var trigger: ScanTrigger? = null
    private var folderAdded = false

    fun offer(reason: ScanTrigger) {
        if (trigger == null) trigger = reason
        if (reason == ScanTrigger.FolderAdded) folderAdded = true
    }

    /** The reason of the pass that starts now (null when none was given) and whether a folder add was merged into it. */
    fun take(): Pair<ScanTrigger?, Boolean> = (trigger to folderAdded).also {
        trigger = null
        folderAdded = false
    }

    /** The pass that [take] handed out did not finish: its folder add waits for the next pass. */
    fun keepFolderAdd() {
        folderAdded = true
    }
}
