package com.plumemu.graph

import android.content.Context
import com.plumemu.library.matching.SystemRule
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader

/*
 * What the library's scan takes from the platform data (moved out of AppGraph.kt unchanged): the
 * shipped catalogue and each system's matching rules. The full rescan after a folder-rules change
 * is `scanForFolderRules` in NamingRulesMark.kt.
 */

/**
 * The library's matching rules from the platform data: folder names and extensions per system. A
 * system of installed apps (`android`) has no files: it is left out, or a folder named
 * "Android" or "Android games" would be matched to it by name.
 */
internal fun systemRulesOf(catalog: PlatformCatalog): List<SystemRule> =
    catalog.systems.filterNot { it.fromInstalledApps }.map {
        SystemRule(it.id, it.folders.toSet(), it.extensions.toSet(), setOf(it.name, it.shortName, it.displayName), it.emulatorIds.toSet())
    }

private const val CATALOG_ASSET = "platforms.json"

/** Null when the shipped file is broken; the loader has logged which check failed. */
internal fun loadShippedCatalog(context: Context): PlatformCatalog? {
    val result = context.assets.open(CATALOG_ASSET).use { PlatformLoader.load(it, "assets/$CATALOG_ASSET") }
    return (result as? PlatformLoadResult.Loaded)?.catalog
}
