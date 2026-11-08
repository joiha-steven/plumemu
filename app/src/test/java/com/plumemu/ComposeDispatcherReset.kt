package com.plumemu

import android.os.Build
import android.os.Looper
import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.plumemu.home.isLooperEmpty
import org.robolectric.Shadows.shadowOf
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Lets Compose's process-wide `AndroidUiDispatcher.Main` finish what an earlier test left in it.
 *
 * Robolectric clears the main looper between tests without running it. When a teardown had queued
 * work on the dispatcher (a window's recomposer being cancelled, a focus change being applied), the
 * dispatcher stays marked as scheduled for a looper message that no longer exists, so it never
 * schedules again: every later test in the JVM gets no frames (Settings panes drawn empty), the
 * dead recomposers are never cancelled, and each earlier test's whole composition stays reachable
 * through them (the full app suite ran out of its 2 GB heap, ComposeDispatcherResetTest).
 * [resetComposeDispatcher] runs the pending work, which clears both marks; the dispatcher is
 * Compose's, so its private state is reached by name and a missing name fails loudly.
 */
internal fun resetComposeDispatcher() {
    val main = mainDispatcherContext() ?: return
    val dispatcher = checkNotNull(main[ContinuationInterceptor]) { "AndroidUiDispatcher.Main has no dispatcher" }
    val callback = dispatcher.field("dispatchCallback") as Choreographer.FrameCallback
    callback.doFrame(System.nanoTime())
}

/**
 * Clears a frame the main Choreographer still holds as scheduled while nothing waits on the main looper.
 *
 * Robolectric resets the Choreographer and clears the looper between tests, but work left from an
 * earlier test (a coroutine resuming on `AndroidUiDispatcher.Main` from another thread) can ask for
 * a frame in the middle of that: the frame stays marked as scheduled while the message that would
 * bring it went with the old queue. No frame runs again, the first layout never happens, and its
 * sync barrier holds back every plain message on the main looper, so a `postDelayed` never runs
 * whatever the clock says. Seen 1 in about 100 runs under load (MainActivity's stray-focus catcher
 * never started, its decor view had no insets; StuckChoreographerTest). [TestPlumApplication] calls
 * it at every test's start; never makes the main Choreographer.
 */
internal fun unstickMainChoreographer() {
    // Android 7.1 and older have no main-instance field; their tests (23, 25) never failed this way.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    // At a test's start the queue is empty, so a frame marked as scheduled can never arrive.
    if (!shadowOf(Looper.getMainLooper()).isLooperEmpty()) return
    val main = Choreographer::class.java.getDeclaredField("mMainInstance").apply { isAccessible = true }.get(null) as Choreographer? ?: return
    val scheduled = Choreographer::class.java.getDeclaredField("mFrameScheduled").apply { isAccessible = true }
    if (!scheduled.getBoolean(main)) return
    System.err.println("unstickMainChoreographer: a frame was marked as scheduled with an empty main looper; cleared")
    scheduled.setBoolean(main, false)
}

/** Whether the dispatcher still holds work or a mark from before ([resetComposeDispatcher] clears them). */
internal fun composeDispatcherIsStuck(): Boolean {
    val dispatcher = mainDispatcherContext()?.get(ContinuationInterceptor) ?: return false
    val queued = (dispatcher.field("toRunTrampolined") as Collection<*>).isNotEmpty()
    return queued || dispatcher.field("scheduledFrameDispatch") == true || dispatcher.field("scheduledTrampolineDispatch") == true
}

/** `AndroidUiDispatcher.Main` when something has already made it; never makes it (a test without Compose would pin a Choreographer). */
private fun mainDispatcherContext(): CoroutineContext? {
    val delegate = AndroidUiDispatcher::class.java.getDeclaredField("Main\$delegate").apply { isAccessible = true }.get(null) as Lazy<*>
    return if (delegate.isInitialized()) delegate.value as CoroutineContext else null
}

private fun Any.field(name: String): Any? = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
