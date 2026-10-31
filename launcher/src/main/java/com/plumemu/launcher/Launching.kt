package com.plumemu.launcher

import android.content.Context
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.apps.InstalledApps
import com.plumemu.launcher.choice.ConfirmedCores
import com.plumemu.launcher.choice.EmulatorChoices
import com.plumemu.launcher.choice.SharedPreferencesConfirmedCores
import com.plumemu.launcher.detect.EmulatorDetector
import com.plumemu.launcher.play.PlayBattery
import com.plumemu.launcher.play.PlayTimeTracker
import com.plumemu.library.storage.AndroidStorageRoots
import com.plumemu.platforms.PlatformCatalog
import kotlinx.coroutines.Dispatchers

/**
 * Everything the app needs from this module, built once per process and shared: one [choices]
 * serves both the launcher and the screens that change choices, so they never disagree.
 *
 * The Activity calls [playTime]`.onResume()` from `onResume`, and [detector]`.detect(catalog)` after
 * the first frame, on returning to the app and from Settings > Detect, followed by
 * [choices]`.refreshDefaults(systems with games, result)`. Play with… reads [detector]`.current(catalog)`
 * (the last result until a package change is told through `packagesChanged`). [confirmedCores] holds the RetroArch cores
 * the person said started (asked on return from a launch with an unconfirmed core).
 */
class Launching internal constructor(
    val launcher: GameLauncher,
    val choices: EmulatorChoices,
    val detector: EmulatorDetector,
    val playTime: PlayTimeTracker,
    val apps: InstalledApps,
    val confirmedCores: ConfirmedCores = ConfirmedCores.None,
) {
    companion object {
        /** [startObserver] hears of each emulator or app start just before it happens ([GameStartObserver]). */
        fun create(
            context: Context,
            database: PlumDatabase,
            catalog: PlatformCatalog,
            startObserver: GameStartObserver = GameStartObserver.None,
            playBattery: PlayBattery = PlayBattery.None,
        ): Launching {
            val app = context.applicationContext
            val detector = EmulatorDetector(app)
            val choices = EmulatorChoices(
                database.emulatorChoiceDao(), catalog, detector::isLaunchable, AndroidLauncherLog, Dispatchers.IO,
            )
            val confirmedCores = SharedPreferencesConfirmedCores(app)
            val playTime = PlayTimeTracker(app, database.playDao(), database.gameDao(), playBattery)
            val launcher = GameLauncher(
                catalog,
                database.gameDao(),
                choices,
                ContentResolverDocumentProbe(app.contentResolver),
                ContentResolverSmallFileReader(app.contentResolver),
                ActivityStarter { app.startActivity(it) }.observedBy(startObserver),
                playTime,
                AndroidLauncherLog,
                Dispatchers.IO,
                AndroidStorageVolumes(),
                confirmedCores,
                AndroidStorageRoots(app),
            )
            return Launching(launcher, choices, detector, playTime, InstalledApps(app, startObserver, playBattery), confirmedCores)
        }
    }
}
