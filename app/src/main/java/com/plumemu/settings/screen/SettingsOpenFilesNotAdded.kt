package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings

/**
 * Opens Library with its Files not added page (the home's empty state). B then goes back through Library's rows and the
 * sections as usual. False while Library has no such row (never, once the content has arrived).
 */
internal fun SettingsModel.openFilesNotAdded(
    settings: PlumSettings,
    geometry: SettingsGeometry,
): Boolean {
    val index = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.FilesNotAdded)
    if (index < 0) return false
    focusLibraryRow(index)
    return openPanel(SettingsOption.FilesNotAdded, settings, geometry)
}
