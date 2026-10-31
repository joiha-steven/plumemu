package com.plumemu.settings

import com.plumemu.coreui.theme.Rgb
import com.plumemu.platforms.PlatformCatalog

/**
 * The colour of each system that has one, by system id (the platform data's `color`): the input of
 * `SystemAccents` for Accent color › By system. Build it once per catalog, not per screen.
 */
internal fun PlatformCatalog.systemColors(): Map<String, Rgb> =
    systems.mapNotNull { system -> system.color?.let { system.id to Rgb(it) } }.toMap()
