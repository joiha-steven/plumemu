package com.plumemu.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** What the Updates panel says about the "Plumemu updated" notice ([ReopenNotice]) after the person pressed Update. */
enum class NoticeLine {
    None,

    /** Android's notification question is about to show (or is open): why Plumemu asks. */
    Reason,

    /** Notifications are off: after the update Plumemu may not open by itself; Home brings it back. */
    NotificationsOff,
}

/**
 * Asking for notifications at the person's Update press, and the line the panel shows meanwhile and after.
 * The download never waits for the question, only the install commit does ([awaitAnswer]). Below Android 13 nothing is asked.
 * [allowed] is [ReopenNotice.areAllowed] (a binder call; asked only at a press and when Plumemu is back in front).
 */
internal class NoticeAsk(
    private val deviceSdk: Int,
    private val allowed: () -> Boolean,
    private val log: (String) -> Unit,
    private val publish: (NoticeLine) -> Unit,
) {
    private val lock = Any()
    private var pressed = false
    private val asking = MutableStateFlow(false)

    /**
     * The person pressed Update (not the yes to mobile data: that is the same press going on). [request] shows Android's
     * question and calls its argument when the question closes; it returns false when Android has none (then there is
     * nothing to wait for). At most one question per press; Android itself answers at once after two refusals.
     */
    fun ask(request: (onAnswered: () -> Unit) -> Boolean) {
        val askNow =
            synchronized(lock) {
                pressed = true
                asking.value = deviceSdk >= PERMISSION_SDK && !allowed()
                asking.value
            }
        refresh()
        if (!askNow) return
        val shown =
            try {
                request(::answered)
            } catch (e: RuntimeException) {
                log("Update: notification question failed (${e.javaClass.simpleName})")
                false
            }
        if (!shown) answered()
    }

    /**
     * Plumemu is back in front (also a new Activity after a recreation, which never hears the old one's answer): an open
     * question no longer holds the line, and notifications may have been turned on in Android's settings.
     */
    fun onFront() {
        synchronized(lock) { asking.value = false }
        refresh()
    }

    /**
     * Suspends while Android's question is open: the install waits, or its confirm screen would stack on the question and
     * the process would be replaced before it is answered. Ends on the answer or on [onFront] (a question nobody answers).
     */
    suspend fun awaitAnswer() {
        asking.first { !it }
    }

    fun refresh() {
        val (hasPressed, isAsking) = synchronized(lock) { pressed to asking.value }
        publish(
            when {
                !hasPressed -> NoticeLine.None
                isAsking -> NoticeLine.Reason
                allowed() -> NoticeLine.None
                else -> NoticeLine.NotificationsOff
            },
        )
    }

    /** Android's question closed, whichever Activity is up: [NoticePermissionLauncher] tells it with or without an asker waiting. */
    fun answered() {
        synchronized(lock) { asking.value = false }
        refresh()
    }

    private companion object {
        /** Android 13: notifications need the person's yes (POST_NOTIFICATIONS). */
        const val PERMISSION_SDK = 33
    }
}
