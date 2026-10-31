package com.plumemu.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.plumemu.launcher.ActivityStarter
import com.plumemu.launcher.AndroidLauncherLog
import com.plumemu.launcher.GameStartObserver
import com.plumemu.launcher.LauncherLog
import com.plumemu.launcher.kind
import com.plumemu.launcher.observedBy
import com.plumemu.launcher.play.PlayBattery
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app the Apps entry lists: what it is called and how to start it. [isGame] is
 * Android's own game mark of the app ([isGameMarked]); the app layer uses it to list Android games.
 */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isGame: Boolean = false,
)

sealed interface AppLaunchOutcome {
    data object Started : AppLaunchOutcome

    /** The app was uninstalled or disabled since the list was read. */
    data object Missing : AppLaunchOutcome

    /** Android refused the start, or `startActivity` threw anything else; nothing was started. */
    data class Failed(val error: Throwable) : AppLaunchOutcome
}

/** Whether Android still has a package, in any state (an app game's row is deleted only on [NotInstalled]). */
enum class PackagePresence {
    /** Installed, maybe disabled or on storage that is not mounted now. */
    Installed,

    /** Android does not know the package any more: uninstalled. */
    NotInstalled,

    /** The question failed (a system error): treat as still installed. */
    Unknown,
}

/** How App info or Uninstall ended; a failure has been logged (ids only). */
enum class AppToolOutcome { Started, Failed }

/**
 * The launchable apps for the Apps entry: every MAIN/LAUNCHER activity Android shows Plumemu
 * (visible from Android 11 through the `<queries>` intent in the platforms module's manifest),
 * without Plumemu itself, by label in the person's language, except that Android's own Settings
 * comes first (on a handheld it is the app opened most from here). A start is told to the observer given
 * to [Launching.create][com.plumemu.launcher.Launching.create] as an emulator's is ([GameStartObserver]),
 * so the app's second screen treats an app opened from Plumemu like a game. Its battery is measured
 * like a game's too: [battery] is told a play starts, with no usual pace, so the
 * in-game part never shows the previous game's figures. Nothing is stored per app: play sessions
 * are kept for games only (`play_session.game_id`).
 */
class InstalledApps internal constructor(
    private val packageManager: PackageManager,
    private val ownPackage: String,
    private val starter: (Intent) -> Unit,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
    private val battery: PlayBattery = PlayBattery.None,
    /** Starts App info and Uninstall: Android's own screens, not an app opened from Plumemu, so no start observer. */
    private val plainStarter: (Intent) -> Unit = {},
) {
    constructor(
        context: Context,
        startObserver: GameStartObserver = GameStartObserver.None,
        battery: PlayBattery = PlayBattery.None,
    ) : this(
        context.packageManager,
        context.packageName,
        ActivityStarter { intent -> context.applicationContext.startActivity(intent) }.observedBy(startObserver)::start,
        AndroidLauncherLog,
        Dispatchers.IO,
        battery,
        { intent -> context.applicationContext.startActivity(intent) },
    )

    /** Reads the list off the main thread; labels are loaded here, icons are not. */
    suspend fun list(): List<LaunchableApp> = withContext(io) {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        queryLauncherActivities()
            .map { it.activityInfo }
            .filter { it.packageName != ownPackage }
            .map { LaunchableApp(it.loadLabel(packageManager).toString(), it.packageName, it.name, isGame(it.applicationInfo)) }
            .distinctBy { it.packageName to it.activityName }
            .sortedWith(
                compareBy<LaunchableApp> { it.packageName != settingsPackage() }
                    .thenBy(collator) { it.label }
                    .thenBy { it.packageName },
            )
    }

    fun launch(app: LaunchableApp): AppLaunchOutcome = start(app, tellBattery = true)

    /**
     * Starts the installed app [packageName] listed as a game (Android games, Ports apps),
     * like a tap on its home screen icon: its first launcher activity. [AppLaunchOutcome.Missing] when
     * Android no longer lists one (uninstalled, disabled). The battery is not told here: the app notes
     * the play with `PlayTimeTracker.onGameLaunched` first, which tells it with the game's usual pace.
     * Call off the main thread (it asks PackageManager).
     */
    fun startGameApp(packageName: String): AppLaunchOutcome {
        val activity = try {
            queryLauncherActivities(launcherIntent().setPackage(packageName)).firstOrNull()?.activityInfo
        } catch (e: RuntimeException) {
            log.warn("apps: launcher activity of $packageName not read (${e.kind()})")
            return AppLaunchOutcome.Failed(e)
        }
        if (activity == null || isDisabled(packageName)) {
            log.warn("apps: $packageName has no launcher activity (uninstalled or disabled)")
            return AppLaunchOutcome.Missing
        }
        return start(LaunchableApp(packageName, packageName, activity.name), tellBattery = false)
    }

    /** The person turned the app off (a disabled app's activity may still resolve on some systems). */
    private fun isDisabled(packageName: String): Boolean =
        try {
            packageManager.getApplicationEnabledSetting(packageName) in DISABLED_STATES
        } catch (e: IllegalArgumentException) {
            true // Android no longer knows the package.
        }

    private fun start(app: LaunchableApp, tellBattery: Boolean): AppLaunchOutcome = try {
        // Before the start, as a game's (PlayTimeTracker.onGameLaunched): the second screen's
        // in-game card may read as soon as the app is in front. Quick and never throws.
        if (tellBattery) battery.onPlayStarting(usualDrawMicroamps = null)
        starter(intentFor(app))
        log.info("apps: started ${app.packageName}")
        AppLaunchOutcome.Started
    } catch (e: ActivityNotFoundException) {
        log.warn("apps: ${app.packageName} is no longer installed (${e.kind()})")
        AppLaunchOutcome.Missing
    } catch (e: SecurityException) {
        log.warn("apps: ${app.packageName} refused to start (${e.kind()})")
        AppLaunchOutcome.Failed(e)
    } catch (e: RuntimeException) {
        // As for games (GameLauncher.start): startActivity can throw anything from a bad Intent to
        // a dead system service, and none of it may reach the home screen as a crash.
        log.warn("apps: ${app.packageName} could not start (${e.kind()})")
        AppLaunchOutcome.Failed(e)
    }

    /**
     * Whether Android still has [packageName] installed in any state: enabled, disabled, disabled until used, or on
     * storage that is not mounted (an app on an unmounted SD card or adopted storage is unloaded from the live package
     * list and is found only with the uninstalled-packages flag). The Apps list omits all of those, so an app game
     * missing from it is deleted only on [PackagePresence.NotInstalled]. A package found that way without
     * `FLAG_INSTALLED` was uninstalled with its data kept (`pm uninstall -k`): not installed.
     */
    fun presenceOf(packageName: String): PackagePresence =
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    packageManager.getApplicationInfo(packageName, ANY_STATE_FLAGS)
                } else {
                    @Suppress("DEPRECATION") // The MATCH_ flags are API 24; these are their older names.
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_DISABLED_COMPONENTS or PackageManager.GET_UNINSTALLED_PACKAGES,
                    )
                }
            presenceFromFlags(info.flags)
        } catch (e: PackageManager.NameNotFoundException) {
            PackagePresence.NotInstalled
        } catch (e: RuntimeException) {
            log.warn("apps: whether $packageName is installed could not be asked (${e.kind()})")
            PackagePresence.Unknown
        }

    /**
     * Whether Uninstall is offered for [packageName]: not Plumemu itself, not a system app that has no update to
     * remove (`FLAG_SYSTEM` without `FLAG_UPDATED_SYSTEM_APP`), and false for a package Android no longer knows.
     */
    fun canUninstall(packageName: String): Boolean {
        if (packageName == ownPackage) return false
        val info = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            log.warn("apps: $packageName is no longer installed (${e.kind()})")
            return false
        }
        return isUninstallable(info.flags)
    }

    /**
     * Whether the app menu offers "Show as game" / "Show as port" for [packageName]: not Plumemu
     * itself and not Android's Settings, which stay in Apps.
     */
    fun canShowAsGame(packageName: String): Boolean = packageName != ownPackage && packageName != settingsPackage()

    /** Opens Android's App info page for [app]. */
    fun showInfo(app: LaunchableApp): AppToolOutcome = startTool(app, "info", appInfoIntent(app.packageName))

    /** Asks Android to uninstall [app]; Android asks the person to confirm. Never for Plumemu itself. */
    fun uninstall(app: LaunchableApp): AppToolOutcome =
        if (app.packageName == ownPackage) {
            log.warn("apps: uninstall refused for Plumemu itself")
            AppToolOutcome.Failed
        } else {
            startTool(app, "uninstall", uninstallIntent(app.packageName))
        }

    private fun startTool(app: LaunchableApp, what: String, intent: Intent): AppToolOutcome = try {
        plainStarter(intent)
        log.info("apps: $what ${app.packageName}")
        AppToolOutcome.Started
    } catch (e: ActivityNotFoundException) {
        log.warn("apps: $what ${app.packageName} has no screen to open (${e.kind()})")
        AppToolOutcome.Failed
    } catch (e: RuntimeException) {
        log.warn("apps: $what ${app.packageName} could not start (${e.kind()})")
        AppToolOutcome.Failed
    }

    /** The package that opens Android's Settings; some devices rename it, so ask before guessing. */
    private fun settingsPackage(): String =
        packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName ?: ANDROID_SETTINGS

    private fun queryLauncherActivities(intent: Intent = launcherIntent()): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") // The flags overload is API 33; this one is the only choice below.
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    internal companion object {
        const val ANDROID_SETTINGS = "com.android.settings"

        fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        /** App info: Android's details page of the package. */
        fun appInfoIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Uninstall: Android's own confirmation (needs REQUEST_DELETE_PACKAGES from API 28). */
        fun uninstallIntent(packageName: String): Intent =
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Android's game mark of [info]: its category from Android 8.0, the older flag before it (no permission needed). */
        fun isGame(info: ApplicationInfo?): Boolean =
            info != null && isGameMarked(Build.VERSION.SDK_INT, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) info.category else NO_CATEGORY, info.flags)

        /**
         * Whether Android marks an app as a game: `category == CATEGORY_GAME` on API 26+, `FLAG_IS_GAME`
         * below (the flag is deprecated from 26, where apps declare `android:appCategory="game"` instead).
         */
        @Suppress("DEPRECATION") // FLAG_IS_GAME is the only mark below API 26.
        fun isGameMarked(sdk: Int, category: Int, flags: Int): Boolean =
            if (sdk >= Build.VERSION_CODES.O) category == ApplicationInfo.CATEGORY_GAME else flags and ApplicationInfo.FLAG_IS_GAME != 0

        /** Installed only with `FLAG_INSTALLED`: the uninstalled-packages flag also returns packages removed with their data kept. */
        fun presenceFromFlags(flags: Int): PackagePresence =
            if (flags and ApplicationInfo.FLAG_INSTALLED != 0) PackagePresence.Installed else PackagePresence.NotInstalled

        private val DISABLED_STATES = setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        )

        /** API 24+: a package that is installed, enabled or not, mounted or not (FLAG_INSTALLED is checked after). */
        @android.annotation.SuppressLint("InlinedApi") // Read only from API 24 (presenceOf).
        private const val ANY_STATE_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
            PackageManager.MATCH_UNINSTALLED_PACKAGES

        /** `ApplicationInfo.CATEGORY_UNDEFINED`, which exists only from API 26. */
        private const val NO_CATEGORY = -1

        /** A system app can be uninstalled only when it is an update over a system one (which Android then rolls back). */
        fun isUninstallable(flags: Int): Boolean =
            flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

        /** Like a tap on a home screen icon: brings the app's task forward if it is running. */
        fun intentFor(app: LaunchableApp): Intent = launcherIntent()
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
