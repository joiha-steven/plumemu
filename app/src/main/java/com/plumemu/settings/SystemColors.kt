package com.plumemu.settings

import com.plumemu.coreui.theme.Rgb
import com.plumemu.platforms.PlatformCatalog

/**
 * The colour of each system that has one, by system id (the platform data's `color`): the input of
 * `SystemAccents` for Accent color › By system. Build it once per catalog, not per screen.
 */
internal fun PlatformCatalog.systemColors(): Map<String, Rgb> =
    systems.mapNotNull { system -> system.color?.let { system.id to Rgb(it) } }.toMap()

/** The same colours by the system's short code in capitals ("SNES"), which is what a drawn box front knows its system by. */
internal fun PlatformCatalog.systemTints(): Map<String, Rgb> =
    systems.mapNotNull { system -> system.color?.let { system.shortName.uppercase(java.util.Locale.ROOT) to Rgb(it) } }.toMap()
