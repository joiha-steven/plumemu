package com.plumemu.graph

import com.plumemu.coredata.db.AppKind

/** An installed launchable app as the classifier sees it: no Android types, so it is tested on the JVM. */
internal data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Android marks the app as a game (`ApplicationInfo.category` / `FLAG_IS_GAME`, launcher's `InstalledApps.isGame`). */
    val isGameFlag: Boolean,
)

/** The two systems installed apps can be listed under. Never change one: stored rows carry them. */
internal object AppGameSystems {
    /** Android games: installed apps that are games. */
    const val ANDROID = "android"

    /** Ports: the `ports` folder's files and installed native port apps. */
    const val PORTS = "ports"

    val all = setOf(ANDROID, PORTS)
}

/**
 * Which installed apps are listed as games, and under which system: package name to
 * [AppGameSystems.ANDROID] or [AppGameSystems.PORTS]; an app left out stays in the Apps entry.
 *
 * Per app, the first that answers wins:
 * 1. the person's choice ([choices]): [AppKind.GAME] is an Android game, [AppKind.PORT] a port,
 *    [AppKind.APP] neither, whatever the data or Android say;
 * 2. the platform data's [knownApps] (package to `android` or `ports`; another system id is ignored);
 * 3. Android's game mark: an Android game.
 *
 * Steps 2 and 3 never mark [emulatorPackages] (RetroArch, GameNative, Winlator… may carry the game
 * mark; they are the launcher's tools, not games): only the person can. [ownPackage], Plumemu itself,
 * is never listed, not even by a choice. A port is never also an Android game (one system per package).
 */
internal fun appGameSystems(
    apps: List<InstalledApp>,
    knownApps: Map<String, String>,
    choices: Map<String, AppKind>,
    emulatorPackages: Set<String>,
    ownPackage: String,
): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    // A package with several launcher activities is one app: Android's mark is the application's.
    val byPackage = apps.groupBy { it.packageName }
    for ((packageName, activities) in byPackage) {
        if (packageName == ownPackage) continue
        val system =
            when (choices[packageName]) {
                AppKind.GAME -> AppGameSystems.ANDROID
                AppKind.PORT -> AppGameSystems.PORTS
                AppKind.APP -> null
                null ->
                    when {
                        packageName in emulatorPackages -> null
                        else -> knownApps[packageName]?.takeIf { it in AppGameSystems.all }
                            ?: AppGameSystems.ANDROID.takeIf { activities.any { it.isGameFlag } }
                    }
            }
        if (system != null) result[packageName] = system
    }
    return result
}
