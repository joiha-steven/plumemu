package com.plumemu.coredata.backup

import com.plumemu.coredata.settings.PlumSettings

/**
 * The settings a restore writes: every setting from
 * the file, and the one-time flags by these rules, because some describe this device or a library
 * rather than the person's choices:
 *
 * - `setupCompleted` comes from the file only with its library (an Everything file), and never
 *   sends the person back to first-run setup: true when either side says so.
 * - `homeRoleAsked`, `rescanAfterReset` and `inGameDriftNoteShown` stay this device's: the home-app
 *   question and the second screen's note are about this device, and a reset's pending scan is about
 *   the library this device has.
 * - `folderMatchVersion`, `namingRulesVersion` and `scrapeRulesVersion` come with the database
 *   they describe, so only from an Everything file (a file without `namingRulesVersion` or
 *   `scrapeRulesVersion`, written before it existed, gives 0: its rows are named again, or its
 *   wrong box-art results repaired, once).
 */
object RestoreSettings {
    fun merge(
        fromFile: PlumSettings,
        onDevice: PlumSettings,
        withLibrary: Boolean,
    ): PlumSettings {
        val device = onDevice.flags
        val flags =
            if (withLibrary) {
                device.copy(
                    setupCompleted = fromFile.flags.setupCompleted || device.setupCompleted,
                    folderMatchVersion = fromFile.flags.folderMatchVersion,
                    namingRulesVersion = fromFile.flags.namingRulesVersion,
                    scrapeRulesVersion = fromFile.flags.scrapeRulesVersion,
                )
            } else {
                device
            }
        return fromFile.copy(flags = flags)
    }
}
