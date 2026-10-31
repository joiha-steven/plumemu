package com.plumemu.launcher.detect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.plumemu.launcher.AndroidLauncherLog
import com.plumemu.launcher.LauncherLog
import com.plumemu.platforms.PackageVariant
import com.plumemu.platforms.PlatformCatalog
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What of the platform data is installed and can be started (the package is there and the activity
 * resolves): [packages] by package name, [emulatorIds] the emulators with at least one such build.
 * RetroArch counts as every one of its cores: whether a core is inside RetroArch is known only when
 * a game starts.
 */
data class InstalledEmulators(val packages: Set<String>, val emulatorIds: Set<String>) {
    fun has(packageName: String): Boolean = packageName in packages

    companion object {
        val NONE = InstalledEmulators(emptySet(), emptySet())
    }
}

/**
 * Asks PackageManager which emulators are installed. Runs only when called (after the first frame,
 * on returning to the app, from Settings > Detect): no polling, no service.
 * Below Android 11 every package is visible; from 11 the `<queries>` that tools/platforms/convert.py
 * writes into the platforms module's manifest make the emulator packages visible.
 */
class EmulatorDetector internal constructor(
    private val packages: PackageLookup,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
) {
    constructor(context: Context) : this(AndroidPackageLookup(context.packageManager), AndroidLauncherLog, Dispatchers.IO)

    private val latest = MutableStateFlow<InstalledEmulators?>(null)

    /** Packages already logged as installed-but-not-launchable in this process. */
    private val reportedMissing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** The result of the last [detect], null before the first one. */
    val installed: StateFlow<InstalledEmulators?> = latest.asStateFlow()

    /** Package changes told through [packagesChanged] in this process. */
    private val changesSeen = AtomicInteger()

    /** [changesSeen] when the detection behind [latest] began; -1 before the first one. */
    private var detectedAtChange = -1

    /**
     * Something was installed, removed, updated or turned on or off (the app's `PackageChanges`), or
     * a start found an emulator the last detection listed gone: the next [current] detects again.
     */
    fun packagesChanged() {
        changesSeen.incrementAndGet()
    }

    /**
     * The installed emulators without asking PackageManager again when nothing changed since the last
     * [detect] (about 790 package checks); a fresh [detect] before the first one
     * and after [packagesChanged].
     */
    suspend fun current(catalog: PlatformCatalog): InstalledEmulators {
        val known = synchronized(this) { latest.value?.takeIf { detectedAtChange == changesSeen.get() } }
        return known ?: detect(catalog)
    }

    /** Checks every emulator package of [catalog], off the main thread, and publishes the result. */
    suspend fun detect(catalog: PlatformCatalog): InstalledEmulators = withContext(io) {
        // A change told while this runs makes it stale at once: the next current() detects again.
        val startedAtChange = changesSeen.get()
        val checked = HashMap<PackageVariant, BuildState>()
        val found = LinkedHashSet<String>()
        val emulatorIds = LinkedHashSet<String>()
        for (emulator in catalog.emulators) {
            val missing = ArrayList<PackageVariant>()
            for (variant in emulator.allPackages) {
                val state = checked.getOrPut(variant) { stateOf(variant) }
                if (state == BuildState.Launchable) {
                    found += variant.packageName
                    emulatorIds += emulator.id
                }
                if (state == BuildState.ActivityMissing) missing += variant
            }
            // Otherwise an installed build that is skipped leaves no trace in the log. Only when no build
            // of the emulator starts: Flycast's data also names Reicast's old activity under its package,
            // which today's Flycast lacks. Once per package and process: a borrowed name repeats forever.
            if (emulator.id in emulatorIds) continue
            for (skipped in missing) {
                if (!reportedMissing.add(skipped.packageName)) continue
                log.warn(
                    "emulator detection: ${skipped.packageName} is installed but ${skipped.activity} does not " +
                        "resolve (a different app under that name, or the emulator moved its activity); " +
                        "skipped for ${emulator.id}",
                )
            }
        }
        val result = InstalledEmulators(found, emulatorIds)
        synchronized(this@EmulatorDetector) {
            // Two detections at once: the one begun after the later change wins, an older one never overwrites it.
            if (startedAtChange >= detectedAtChange) {
                latest.value = result
                detectedAtChange = startedAtChange
            }
        }
        log.info("emulator detection: ${found.size} installed package(s): ${found.sorted().joinToString()}")
        result
    }

    /**
     * One build, checked now (blocking; call off the main thread): used right before a launch, so
     * an emulator uninstalled since the last [detect] is noticed.
     */
    internal fun isLaunchable(variant: PackageVariant): Boolean = stateOf(variant) == BuildState.Launchable

    /**
     * The activity must resolve, not only the package be there: ES-DE's find rules list builds that
     * install under another app's package name (Eden as `com.miHoYo.Yuanshen`, GameHub as
     * `com.tencent.ig`), and the real app of that name has no such activity.
     */
    private fun stateOf(variant: PackageVariant): BuildState {
        if (!packages.isInstalled(variant.packageName)) return BuildState.NotInstalled
        val activity = variant.activity ?: return BuildState.Launchable
        return if (packages.hasActivity(variant.packageName, activity)) BuildState.Launchable else BuildState.ActivityMissing
    }

    private enum class BuildState { NotInstalled, Launchable, ActivityMissing }
}

/** The PackageManager questions detection asks; a seam so JVM tests can answer them. */
internal interface PackageLookup {
    fun isInstalled(packageName: String): Boolean

    fun hasActivity(packageName: String, className: String): Boolean
}

internal class AndroidPackageLookup(private val packageManager: PackageManager) : PackageLookup {
    override fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") // The flags overload is API 33; this one is the only choice below.
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun hasActivity(packageName: String, className: String): Boolean =
        resolveActivity(packageManager, Intent().setClassName(packageName, className)) != null
}

internal fun resolveActivity(packageManager: PackageManager, intent: Intent): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") // The flags overload is API 33; this one is the only choice below.
        packageManager.resolveActivity(intent, 0)
    }
