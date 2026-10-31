package com.plumemu.shortcuts

import android.content.Intent

/** One shortcut as Android hands it to the home app: in a pin request, a listing or a change. */
data class ShortcutFacts(
    val packageName: String,
    val id: String,
    /** `ShortcutInfo.getShortLabel`; empty when the app gave none. */
    val shortLabel: String,
    /** `UserManager.getSerialNumberForUser` of the shortcut's user. */
    val userSerial: Long,
    val isPinned: Boolean = true,
    val isEnabled: Boolean = true,
)

/** What a pin request asks to put on the home screen. */
enum class PinKind {
    Shortcut,

    /** A widget (`REQUEST_TYPE_APPWIDGET`): Plumemu holds none and says so. */
    Widget,
}

/** Android's `LauncherApps.PinItemRequest`, behind an interface so tests use a fake. */
interface PinRequest {
    val kind: PinKind

    /** The shortcut of a [PinKind.Shortcut] request; null for a widget. */
    val shortcut: ShortcutFacts?

    /** False once the request was answered, or the app that asked went away. */
    val isValid: Boolean

    /** Pins the shortcut for Plumemu; false when Android refused (the request is no longer valid). */
    fun accept(): Boolean
}

/** How Android answered [ShortcutHost.startShortcut]. */
enum class ShortcutStart {
    Started,

    /** Android starts other apps' shortcuts only for the default home app (`SecurityException`, or no host access). */
    NotHomeApp,

    /** Not listed as pinned any more, disabled by its app, or its app or user is gone. */
    Unavailable,

    /** Listed and enabled, yet not started (`ActivityNotFoundException`, a locked user, a system error); logged. */
    Failed,
}

/**
 * Every `LauncherApps` call Plumemu makes for pinned shortcuts, so tests use a fake
 * ([AndroidShortcutHost] is the real one). Below Android 8.0 (API 26) there are no pin requests and
 * every call answers "nothing". Android answers shortcut calls only while Plumemu is the home app;
 * otherwise they throw `SecurityException`, which the real host logs and turns into null or false.
 */
interface ShortcutHost {
    /** The pin request an `CONFIRM_PIN_SHORTCUT` intent carries; null below API 26, or when it carries none. */
    fun pinRequest(intent: Intent): PinRequest?

    /**
     * The shortcuts of [packageName] pinned by Plumemu for the user [userSerial] (only [ids] when
     * given); null when Android would not answer (not the home app, the user is gone, below API
     * 26): nothing can be told then.
     */
    fun pinnedShortcuts(packageName: String, userSerial: Long, ids: List<String>? = null): List<ShortcutFacts>?

    /**
     * Calls [onChanged] with an app's current shortcuts whenever they change, and with an empty list
     * when the app is removed, for as long as the process lives. False when it could not listen.
     */
    fun listen(onChanged: (packageName: String, userSerial: Long, shortcuts: List<ShortcutFacts>) -> Unit): Boolean

    /**
     * Starts the pinned shortcut [id] of [packageName] for the user [userSerial]
     * (`LauncherApps.startShortcut`, a new task like a home screen icon). Never throws: every refusal
     * is one of [ShortcutStart] and a log line.
     */
    fun startShortcut(packageName: String, id: String, userSerial: Long): ShortcutStart

    /** Android 6.0 to 7.1, or a host that never answers. */
    object None : ShortcutHost {
        // No pin requests below 8.0, so a shortcut game here came from a restored backup.
        override fun startShortcut(packageName: String, id: String, userSerial: Long): ShortcutStart = ShortcutStart.Failed

        override fun pinRequest(intent: Intent): PinRequest? = null

        override fun pinnedShortcuts(packageName: String, userSerial: Long, ids: List<String>?): List<ShortcutFacts>? = null

        override fun listen(onChanged: (String, Long, List<ShortcutFacts>) -> Unit): Boolean = false
    }
}
