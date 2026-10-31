package com.plumemu.setup

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * The intent that lets the person make Plumemu the home app: Android 10+ asks its own "default
 * home app" question (`RoleManager.ROLE_HOME`); Android 6–9 open the Settings page that lists home
 * apps (`Settings.ACTION_HOME_SETTINGS`). Null when this device offers neither (the role is not
 * available, or nothing answers Home settings): the prompt then says where to find it instead.
 */
internal fun homeRoleIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        roleRequestIntent(context)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS).takeIf { resolves(context, it) }
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun roleRequestIntent(context: Context): Intent? {
    val roles = context.getSystemService(RoleManager::class.java) ?: return null
    return if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
}

/** True when Plumemu already is the home app: there is nothing to ask. */
internal fun isHomeApp(context: Context): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
        }
    } catch (e: RuntimeException) {
        // A system service that fails here must not crash the home screen; asking is harmless.
        Log.w(TAG, "Could not tell whether Plumemu is the home app (${e.javaClass.simpleName})")
        false
    }

private fun resolves(
    context: Context,
    intent: Intent,
): Boolean = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

private const val TAG = "PlumHomeRole"

/** Android's list of home apps (to switch away from Plumemu); null where nothing answers it. */
internal fun homeSettingsIntent(context: Context): Intent? = Intent(Settings.ACTION_HOME_SETTINGS).takeIf { resolves(context, it) }
