package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings

/**
 * Opens About with its Updates panel (the main screen's pill, or START while an update waits). False while About has
 * no such row (the content has not arrived, or no update waits): nothing moves. B then goes back through
 * About's rows and the sections as usual.
 */
internal fun SettingsModel.openUpdate(
    settings: PlumSettings,
    geometry: SettingsGeometry,
): Boolean {
    if (content.update.found == null) return false
    val index = SettingsSection.About.rowsIn(content).indexOf(SettingsOption.Updates)
    if (index < 0) return false
    focusAboutRow(index)
    return openPanel(SettingsOption.Updates, settings, geometry)
}
