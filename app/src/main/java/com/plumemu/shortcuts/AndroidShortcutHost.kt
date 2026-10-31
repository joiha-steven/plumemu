package com.plumemu.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.IntentCompat

/** The real [ShortcutHost] on Android 8.0 and later; [ShortcutHost.None] below. */
fun shortcutHostFor(context: Context): ShortcutHost =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AndroidShortcutHost(context.applicationContext) else ShortcutHost.None

/**
 * `LauncherApps` for pinned shortcuts. No permission is declared for it: Android gives the shortcut
 * host access to the default home app by itself (never declare `ACCESS_SHORTCUTS`). While Plumemu
 * is not the home app the calls throw `SecurityException` (or `IllegalStateException` for a user
 * that is locked or gone): each is logged by class and answered as "nothing", never a crash. Log
 * lines carry packages and classes only, never a label.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class AndroidShortcutHost(private val context: Context) : ShortcutHost {
    private val launcherApps: LauncherApps? get() = context.getSystemService(LauncherApps::class.java)
    private val users: UserManager? get() = context.getSystemService(UserManager::class.java)

    /**
     * The activity is exported, so any app can send `CONFIRM_PIN_SHORTCUT` with anything in the
     * extra. `LauncherApps.getPinItemRequest` casts it unchecked (a Bundle threw ClassCastException
     * on Android 8 to 12L, a foreign Parcelable BadParcelableException), so the extra is read the
     * way Launcher3's `PinRequestHelper` does: only a real `PinItemRequest` counts, and any failure
     * to read it is no request.
     */
    override fun pinRequest(intent: Intent): PinRequest? =
        try {
            IntentCompat.getParcelableExtra(intent, LauncherApps.EXTRA_PIN_ITEM_REQUEST, LauncherApps.PinItemRequest::class.java)
                ?.let { AndroidPinRequest(it, ::factsOf) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read the pin request (${e.javaClass.simpleName})")
            null
        }

    override fun pinnedShortcuts(packageName: String, userSerial: Long, ids: List<String>?): List<ShortcutFacts>? =
        guarded("list the pinned shortcuts of $packageName") {
            val apps = launcherApps ?: return@guarded null
            val user = users?.getUserForSerialNumber(userSerial) ?: return@guarded null
            if (!apps.hasShortcutHostPermission()) return@guarded null
            val query = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            if (ids != null) query.setShortcutIds(ids)
            apps.getShortcuts(query, user).orEmpty().mapNotNull(::factsOf)
        }

    /**
     * Asks for the shortcut first, so a disabled or removed one says [ShortcutStart.Unavailable]
     * instead of the `ActivityNotFoundException` Android throws for both. Log lines name the package
     * and the exception class only, never the shortcut id or label.
     */
    override fun startShortcut(packageName: String, id: String, userSerial: Long): ShortcutStart {
        val apps = launcherApps
        if (apps == null) {
            Log.w(TAG, "Could not start a shortcut of $packageName: no LauncherApps")
            return ShortcutStart.Failed
        }
        return try {
            if (!apps.hasShortcutHostPermission()) {
                Log.i(TAG, "Shortcut of $packageName not started: not the home app")
                return ShortcutStart.NotHomeApp
            }
            val user = users?.getUserForSerialNumber(userSerial)
            if (user == null) {
                Log.i(TAG, "Shortcut of $packageName not started: its user is gone")
                return ShortcutStart.Unavailable
            }
            val query =
                LauncherApps.ShortcutQuery().setPackage(packageName).setShortcutIds(listOf(id))
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val listed = apps.getShortcuts(query, user).orEmpty().firstOrNull { it.id == id }
            if (listed == null || !listed.isEnabled) {
                Log.i(TAG, "Shortcut of $packageName not started: " + if (listed == null) "no longer pinned" else "disabled by its app")
                return ShortcutStart.Unavailable
            }
            apps.startShortcut(packageName, id, null, null, user)
            Log.i(TAG, "Shortcut of $packageName started")
            ShortcutStart.Started
        } catch (e: SecurityException) {
            // Another app became the home app between the check and the start.
            Log.i(TAG, "Shortcut of $packageName not started: not the home app (${e.javaClass.simpleName})")
            ShortcutStart.NotHomeApp
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Shortcut of $packageName not started (${e.javaClass.simpleName})")
            ShortcutStart.Failed
        } catch (e: IllegalStateException) {
            // The user is locked or not running.
            Log.w(TAG, "Shortcut of $packageName not started (${e.javaClass.simpleName})")
            ShortcutStart.Failed
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Shortcut of $packageName not started (${e.javaClass.simpleName})")
            ShortcutStart.Failed
        }
    }

    override fun listen(onChanged: (String, Long, List<ShortcutFacts>) -> Unit): Boolean =
        guarded("listen for shortcut changes") {
            val apps = launcherApps ?: return@guarded false
            apps.registerCallback(Changes(onChanged), Handler(Looper.getMainLooper()))
            true
        } ?: false

    private fun serialOf(user: UserHandle): Long? = users?.getSerialNumberForUser(user)?.takeIf { it >= 0 }

    private fun factsOf(info: ShortcutInfo): ShortcutFacts? {
        val serial = serialOf(info.userHandle) ?: return null
        return ShortcutFacts(
            packageName = info.`package`,
            id = info.id,
            shortLabel = info.shortLabel?.toString().orEmpty(),
            userSerial = serial,
            isPinned = info.isPinned,
            isEnabled = info.isEnabled,
        )
    }

    private inner class Changes(private val onChanged: (String, Long, List<ShortcutFacts>) -> Unit) : LauncherApps.Callback() {
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            val serial = serialOf(user) ?: return
            guarded("read changed shortcuts of $packageName") { onChanged(packageName, serial, shortcuts.mapNotNull(::factsOf)) }
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            val serial = serialOf(user) ?: return
            guarded("note the removal of $packageName") { onChanged(packageName, serial, emptyList()) }
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = Unit

        override fun onPackageChanged(packageName: String, user: UserHandle) = Unit

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
    }

    private fun <T> guarded(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: SecurityException) {
            // Expected while another app is the home app: Android answers shortcut calls only to it.
            Log.i(TAG, "Could not $what: not the home app (${e.javaClass.simpleName})")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not $what (${e.javaClass.simpleName})")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not $what (${e.javaClass.simpleName})")
            null
        }
}

/** [PinRequest] over Android's own; reads its shortcut once. */
@RequiresApi(Build.VERSION_CODES.O)
private class AndroidPinRequest(
    private val request: LauncherApps.PinItemRequest,
    factsOf: (ShortcutInfo) -> ShortcutFacts?,
) : PinRequest {
    override val kind: PinKind =
        if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) PinKind.Shortcut else PinKind.Widget

    override val shortcut: ShortcutFacts? = if (kind == PinKind.Shortcut) request.shortcutInfo?.let(factsOf) else null

    override val isValid: Boolean get() = request.isValid

    override fun accept(): Boolean =
        try {
            request.accept()
        } catch (e: IllegalStateException) {
            // Answered already (a second press that raced the first) or the request went away.
            Log.w(TAG, "Pin request not accepted (${e.javaClass.simpleName})")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Pin request not accepted (${e.javaClass.simpleName})")
            false
        }
}

internal const val TAG = "PlumShortcuts"
