package com.plumemu.graph

import android.content.Context
import com.plumemu.library.matching.SystemRule
import android.util.Log
import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.platforms.PlatformDataVersions
import com.plumemu.update.LoadedPlatformData
import com.plumemu.update.PlatformDataStore
import com.plumemu.update.selectPlatformData
import com.plumemu.settings.screen.SettingsContent
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/*
 * What the library's scan takes from the platform data (moved out of AppGraph.kt): the
 * catalogue in use (bundled, or a newer downloaded copy) and each system's matching rules. The full rescan after a folder-rules change
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
private const val PLATFORM_DATA_TAG = "PlumUpdate"

/** Under `noBackupFilesDir`: the platform data a check downloaded ([PlatformDataStore]). */
private const val PLATFORM_DATA_FOLDER = "platform-data"

internal fun platformDataStore(context: Context): PlatformDataStore =
    PlatformDataStore(File(context.noBackupFilesDir, PLATFORM_DATA_FOLDER)) { Log.w(PLATFORM_DATA_TAG, it) }

/** The bundled data's version from `platforms-version.json`; a build mistake (a test guards it) is logged and counts as version 1. */
internal fun bundledPlatformDataVersion(context: Context): PlatformDataVersion {
    val text = context.assets.open(PlatformDataVersions.BUNDLED_VERSION_ASSET).use { it.bufferedReader(Charsets.UTF_8).readText() }
    return PlatformDataVersions.parse(text) ?: PlatformDataVersion(1, "", "").also {
        Log.e(PLATFORM_DATA_TAG, "assets/${PlatformDataVersions.BUNDLED_VERSION_ASSET} is not a version file")
    }
}

/**
 * The platform data to use: a downloaded copy newer than the bundled one that loads, else the bundled
 * file ([selectPlatformData]). Null when the bundled file is broken; the loader has logged which check failed.
 */
internal fun loadPlatformData(context: Context): LoadedPlatformData? =
    selectPlatformData(
        bundledVersion = bundledPlatformDataVersion(context),
        openBundled = { context.assets.open(CATALOG_ASSET) },
        store = platformDataStore(context),
        info = { Log.i(PLATFORM_DATA_TAG, it) },
        log = { Log.w(PLATFORM_DATA_TAG, it) },
    )

/** Adds which version of the platform data is in use (Settings › About): none first, then the version once the data has loaded. */
internal fun Flow<SettingsContent>.withPlatformData(load: RetryingLazy<LoadedPlatformData>): Flow<SettingsContent> =
    combine(flow { emit(null); emit(load.get()?.version) }) { content, version -> content.copy(platformData = version) }
