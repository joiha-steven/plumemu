package com.plumemu

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/**
 * Runs [block] off the main thread while turning the main looper, as `LibraryHomeArtTest` does:
 * the Activity's `onResume` builds the graph's launcher from the main thread (play time), and
 * holds its lock until the main thread takes the answer back, so a `runBlocking` there could
 * wait for ever (it did once).
 */
internal fun <T> onMainLoop(block: suspend () -> T): T {
    val result = CompletableDeferred<T>()
    CoroutineScope(Dispatchers.Default).launch { runCatching { block() }.fold(result::complete, result::completeExceptionally) }
    val deadline = System.nanoTime() + MAIN_LOOP_TIMEOUT_MS * 1_000_000
    while (!result.isCompleted && System.nanoTime() < deadline) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(MAIN_LOOP_STEP_MS)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    return result.getCompleted()
}

/** An installed app with a MAIN/LAUNCHER activity, as the Apps entry lists it. */
internal fun installLauncherApp(
    app: Application,
    packageName: String,
    activityName: String,
    label: String,
) {
    val shadow = shadowOf(app.packageManager)
    val application = ApplicationInfo().apply {
        this.packageName = packageName
        nonLocalizedLabel = label
    }
    shadow.installPackage(PackageInfo().apply {
        this.packageName = packageName
        applicationInfo = application
    })
    shadow.addOrUpdateActivity(
        ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
            exported = true
            applicationInfo = application
            nonLocalizedLabel = label
        },
    )
    val filter = IntentFilter("android.intent.action.MAIN").apply { addCategory("android.intent.category.LAUNCHER") }
    shadow.addIntentFilterForActivity(ComponentName(packageName, activityName), filter)
}

private const val MAIN_LOOP_TIMEOUT_MS = 10_000L
private const val MAIN_LOOP_STEP_MS = 5L
