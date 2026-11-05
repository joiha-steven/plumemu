package com.plumemu.game

/** What follows a "No" to "Did <game> start?" for a RetroArch core: the next core to try, or none left. */
internal sealed interface CoreFollowUp {
    /** [core] is the next RetroArch core of the system, in the data's order, not yet answered No. */
    data class Next(val core: EmulatorItem) : CoreFollowUp

    /** Every core is answered No; [toGet] is the first core of the system's order, the one to download. */
    data class NoneLeft(val toGet: EmulatorItem) : CoreFollowUp
}

/** The data's id of the RetroArch MAME 2003-Plus core (`platforms.json`). */
internal const val MAME_2003_PLUS_ID = "retroarch-mame2003-plus"

/** "RetroArch: mGBA" to "mGBA": the core's own name, as the data names it. */
internal fun coreNameOf(emulator: EmulatorItem): String = emulator.name.removePrefix("RetroArch: ")

/**
 * The follow-up to No for [failedId] ([tried]: cores of this game already answered No in this attempt).
 * Null when [failedId] is not an installed RetroArch core of the listing (no core, or RetroArch gone: the person
 * picks another emulator as before). Cores come from [EmulatorListing.installed], which holds only emulators that can open the
 * game's file, in the data's order; RetroArch's own core folder is private, so no core is checked
 * for being installed. For an arcade set with an old MAME 2003-Plus name ([EmulatorListing.oldArcadeName]) that core comes first.
 */
internal fun coreFollowUp(listing: EmulatorListing, failedId: String, tried: Set<String>): CoreFollowUp? {
    // A failed core that is not "installed" means RetroArch itself is gone (uninstalled while the question was
    // up): "get a core in RetroArch" would send the person to an app that is not there, so the old list stays.
    val failed = listing.installed.firstOrNull { it.id == failedId } ?: return null
    if (failed.core == null) return null
    val siblings = listing.installed.filter { it.core != null && it.packageName == failed.packageName && !it.isLegalRisk }
    val untried = siblings.filter { it.id != failedId && it.id !in tried }
    // The one file-based hint (never the folder, never a switch): an old MAME 2003-Plus name puts that core first.
    val next = (if (listing.oldArcadeName) untried.firstOrNull { it.id == MAME_2003_PLUS_ID } else null) ?: untried.firstOrNull()
    return if (next != null) CoreFollowUp.Next(next) else CoreFollowUp.NoneLeft(siblings.firstOrNull() ?: failed)
}
