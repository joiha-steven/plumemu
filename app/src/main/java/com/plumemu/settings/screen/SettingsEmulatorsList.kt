package com.plumemu.settings.screen

/*
 * Library › Emulators: the row's panel lists every system with games ([PanelPage.EmulatorSystems]); A on one opens its
 * page of choices ([PanelPage.Emulators]), and a choice or Back goes back to the list on that system.
 */

/** The page of the list's system [index], on its current choice; null past the list. */
internal fun emulatorPageAt(
    focus: SettingsFocus,
    content: SettingsContent,
    index: Int,
): SettingsFocus? = content.systems.getOrNull(index)?.let { focus.copy(page = PanelPage.Emulators, panelIndex = it.currentIndex, systemId = it.systemId) }

/** The systems list on [focus]'s system, or on the first while that system is gone (it left while its page was open). */
internal fun emulatorListOn(
    focus: SettingsFocus,
    content: SettingsContent,
): SettingsFocus = focus.copy(page = PanelPage.EmulatorSystems, panelIndex = content.systems.indexOfFirst { it.systemId == focus.systemId }.coerceAtLeast(0), systemId = null)

/** The list's cursor [at] after [before] became [after]: the same system stays under it while a scan adds or removes others. */
internal fun emulatorListIndexAfter(
    at: Int,
    before: SettingsContent,
    after: SettingsContent,
): Int = before.systems.getOrNull(at)?.let { kept -> after.systems.indexOfFirst { it.systemId == kept.systemId } }?.takeIf { it >= 0 } ?: at.coerceAtMost(after.systems.lastIndex)
