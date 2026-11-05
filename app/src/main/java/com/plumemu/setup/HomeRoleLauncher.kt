package com.plumemu.setup

import android.content.ActivityNotFoundException
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Opens Android's home-app screens for an Activity and tells the asker what came of it. Built while the Activity is made (it registers for the
 * result). On Android 10+ the role request returns at once, showing nothing, after two refusals: whoever asked is told whether Plumemu became
 * the home app, so a press never seems to do nothing.
 */
internal class HomeRoleLauncher(private val activity: ComponentActivity) {
    /** Told, once Android's home-app screen closes, whether Plumemu is now the home app. */
    private var afterRequest: ((Boolean) -> Unit)? = null

    private val screen =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val isHome = isHomeApp(activity)
            Log.i(TAG, "Home app screen closed (result ${result.resultCode}, home app: $isHome)")
            val told = afterRequest
            afterRequest = null
            told?.invoke(isHome)
        }

    /** Android's own list of home apps, opened plainly (its answer is Android's); false when nothing answers it. */
    fun openList(): Boolean {
        val intent = homeSettingsIntent(activity) ?: return false
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Home app list could not open (${e.javaClass.simpleName})")
            false
        }
    }

    /** The question's Yes: Android's screen for choosing the home app; [onAnswered] gets whether Plumemu is the home app when it closes. False when there is none. */
    fun request(onAnswered: (Boolean) -> Unit): Boolean = launch(homeRoleIntent(activity), onAnswered)

    /**
     * Settings' row: Android's question, or its list when Plumemu already is the home app (to switch back; the request would answer at once and show nothing).
     * When the question closes with Plumemu still not the home app, the list opens straight away (Android 10+; before that the question already is the list)
     * and [onNotHome] is called only when there is none.
     */
    fun openSettings(onNotHome: () -> Unit): Boolean {
        val isHome = isHomeApp(activity)
        val listIsNext = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val intent = (if (isHome) homeSettingsIntent(activity) else homeRoleIntent(activity)) ?: return false
        return launch(intent, if (isHome) null else { becameHome -> if (!becameHome && !(listIsNext && openList())) onNotHome() })
    }

    private fun launch(
        intent: android.content.Intent?,
        onAnswered: ((Boolean) -> Unit)?,
    ): Boolean {
        if (intent == null) return false
        afterRequest = onAnswered
        return try {
            screen.launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            afterRequest = null
            Log.w(TAG, "Home app screen could not open (${e.javaClass.simpleName})")
            false
        }
    }

    private companion object {
        const val TAG = "PlumHomeRole"
    }
}
