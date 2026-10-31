package com.plumemu.secondscreen

import android.app.Dialog
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import android.view.Choreographer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.home.drainMainLooper
import com.plumemu.launcher.play.battery.BatteryReads
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import java.time.Duration

/**
 * What the second screen's window tests share: a started Activity, a Robolectric display at the
 * Thor's bottom screen, the host's inputs as flows, a window recomposer on each test's own
 * Choreographer, and one teardown that leaves the JVM clean.
 */
abstract class SecondScreenHostTestBase {
    internal lateinit var controller: ActivityController<ComponentActivity>
    internal lateinit var activity: ComponentActivity
    internal var displayId = 0
    internal val screens = MutableStateFlow<SecondScreen?>(null)
    internal val settings = MutableStateFlow<PlumSettings?>(PlumSettings())
    internal lateinit var feed: SecondScreenFeed
    internal val presence = MutableStateFlow(Presence.InPlumemu)

    /** The in-game clock's time: 22 October 2026, 21:47 UTC. */
    internal var now = 1_792_705_620_000L
    internal val logs = mutableListOf<String>()
    internal var host: SecondScreenHost? = null

    /**
     * Compose's window recomposer runs on `AndroidUiDispatcher.Main`, made once per JVM on the first
     * test's Choreographer: from the second test on, a window outside a
     * compose rule never recomposed. Each test gets one on its own Choreographer; it is still
     * lifecycle-aware, so it pauses when the window's lifecycle owner stops, as on a device.
     */
    @OptIn(InternalComposeUiApi::class)
    @Before
    fun recomposeOnThisTestsChoreographer() {
        WindowRecomposerPolicy.setFactory { view ->
            view.createLifecycleAwareWindowRecomposer(Dispatchers.Main + AndroidUiFrameClock(Choreographer.getInstance()))
        }
    }


    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller.get()
        feed = SecondScreenFeed(activity.lifecycleScope)
        displayId = ShadowDisplayManager.addDisplay(THOR_BOTTOM)
    }

    /** More hosts made by a test; all are stopped, so the process-wide window holder is free for the next test. */
    internal val others = mutableListOf<SecondScreenHost>()

    /**
     * One teardown, in order (JUnit runs several `@After` methods in no set order): the windows go,
     * the Activity is destroyed, then the main looper is drained. Disposing a window's composition
     * queues work on Compose's process-wide `AndroidUiDispatcher.Main`; left in the looper, which
     * Robolectric clears before the next test, the dispatcher stays marked as scheduled and every
     * later compose test in the JVM gets no frames (Settings shots lost their side panel). Then the
     * recomposer factory goes back to Compose's own.
     */
    @OptIn(InternalComposeUiApi::class)
    @After
    fun tearDown() {
        host?.stop()
        others.forEach { it.stop() }
        // Some tests have already stopped it: only the steps it still needs.
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.pause()
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.stop()
        controller.destroy()
        drainMainLooper()
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
    }

    internal fun startHost(
        kind: ScreenKind = ScreenKind.BuiltIn,
        forcedOn: Boolean = false,
        showWindow: (Dialog) -> Unit = Dialog::show,
        battery: BatteryReads = BatteryReads.None,
        onDriftNoteShown: () -> Unit = {},
        onStrayFocus: () -> Unit = {},
    ): SecondScreenHost {
        screens.value = SecondScreen(displayId, "Overlay #1", kind)
        val made =
            SecondScreenHost(
                context = activity,
                scope = activity.lifecycleScope,
                screens = screens,
                settings = settings,
                feed = feed,
                presence = presence,
                backgroundArgb = 0xFF000000.toInt(),
                forcedOn = forcedOn,
                showWindow = showWindow,
                clock = { now },
                battery = battery,
                onDriftNoteShown = onDriftNoteShown,
                onStrayFocus = onStrayFocus,
                info = { logs += it },
                warn = { logs += "W: $it" },
            )
        host = made
        made.start()
        idle()
        return made
    }

    /**
     * Runs the main looper for [ms]. Compose's global snapshot writes (a state the window collects)
     * are announced by a manager also started once per JVM on that first dispatcher, so the test
     * announces them itself between steps.
     */
    internal fun idle(ms: Long = 50) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(IDLE_STEPS) {
            Snapshot.sendApplyNotifications()
            looper.idleFor(Duration.ofMillis(ms / IDLE_STEPS + 1))
        }
    }

    internal fun on(enabled: Boolean?) = PlumSettings(secondScreen = SecondScreenSettings(enabled = enabled))

    /** The brightest red channel (0 to 1) the window draws: the card's text on the Black background. */
    internal fun brightest(window: Dialog): Float {
        val view = window.window!!.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        var best = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) best = maxOf(best, android.graphics.Color.red(bitmap.getPixel(x, y)))
        return best / 255f
    }

    internal fun pixelsOf(window: Dialog): IntArray {
        val view = window.window!!.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    internal fun lifecycleOf(window: Dialog): Lifecycle.State? = window.window?.decorView?.findViewTreeLifecycleOwner()?.lifecycle?.currentState

    internal fun composeViewOf(window: Dialog): ComposeView = findView(window.window!!.decorView) { it is ComposeView } as ComposeView

    internal fun findView(
        view: View,
        test: (View) -> Boolean,
    ): View? {
        if (test(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findView(view.getChildAt(i), test)?.let { return it }
        return null
    }

    internal fun nodesOf(window: Dialog): List<SemanticsNode> {
        val root = (composeViewOf(window).getChildAt(0) as ViewRootForTest).semanticsOwner.unmergedRootSemanticsNode
        val all = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            all += node
            node.children.forEach(::walk)
        }
        walk(root)
        return all
    }

    internal fun textsOf(window: Dialog): List<String> = nodesOf(window).flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    companion object {
        /** The Thor's Screen-2: 1240x1080 px at 369 dpi. */
        const val THOR_BOTTOM = "w538dp-h468dp-369dpi"

        const val IDLE_STEPS = 5

        /** Well past the settle: an offer has reached the window. */
        const val SETTLED_MS = SecondScreenFeed.SETTLE_MS + 150L
    }
}
