package com.plumemu

import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
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
