package com.plumemu.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle

/**
 * The real host on Robolectric's Android, where Plumemu is not the home app: every call answers
 * "nothing" instead of throwing, and Android 6.0 to 7.1 get no host at all.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidShortcutHostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [23])
    fun android6GetsNoHost() {
        assertEquals(ShortcutHost.None, shortcutHostFor(context))
    }

    @Test
    @Config(sdk = [25])
    fun android71GetsNoHost() {
        assertEquals(ShortcutHost.None, shortcutHostFor(context))
    }

    @Test
    @Config(sdk = [28])
    fun anIntentWithoutARequestAndNoHostAccessAnswerNothing() {
        val host = shortcutHostFor(context)
        assertTrue(host is AndroidShortcutHost)
        assertNull(host.pinRequest(Intent("android.content.pm.action.CONFIRM_PIN_SHORTCUT")))
        assertNull("not the home app: no list", host.pinnedShortcuts("app.gamenative", 0))
        assertEquals("not the home app: no start", ShortcutStart.NotHomeApp, host.startShortcut("app.gamenative", "game_1", 0))
    }

    /** Host access, but Android refuses the list (another app became the home app meanwhile). */
    @Test
    @Config(sdk = [28], shadows = [RefusingLauncherApps::class])
    fun aSecurityExceptionFromLauncherAppsIsLoggedAndAnswersNothing() {
        val apps = context.getSystemService(LauncherApps::class.java)
        (Shadow.extract<ShadowLauncherApps>(apps)).setHasShortcutHostPermission(true)
        RefusingLauncherApps.calls = 0

        assertNull(shortcutHostFor(context).pinnedShortcuts("app.gamenative", 0, listOf("game_1")))
        assertEquals("Android was asked and refused", 1, RefusingLauncherApps.calls)
        assertEquals("a refusal while starting is \"not the home app\"", ShortcutStart.NotHomeApp, shortcutHostFor(context).startShortcut("app.gamenative", "game_1", 0))
    }

    /** Any app can send this explicit intent: a Bundle where Android puts its request must not crash the home app. */
    @Test
    @Config(sdk = [28])
    fun aForgedRequestExtraIsNoRequest() {
        val host = shortcutHostFor(context)
        val bundle = Intent(CONFIRM).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, Bundle().apply { putString("x", "y") })
        assertNull(host.pinRequest(bundle))
        val text = Intent(CONFIRM).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, "app.gamenative")
        assertNull(host.pinRequest(text))
    }
}

private const val CONFIRM = "android.content.pm.action.CONFIRM_PIN_SHORTCUT"

/** `LauncherApps.getShortcuts` as Android answers it to an app that is not the home app. */
@Implements(LauncherApps::class)
class RefusingLauncherApps : ShadowLauncherApps() {
    @Implementation
    override fun getShortcuts(
        query: LauncherApps.ShortcutQuery,
        user: UserHandle,
    ): List<ShortcutInfo> {
        calls++
        throw SecurityException("Caller can't access shortcut information")
    }

    companion object {
        var calls = 0
    }
}
