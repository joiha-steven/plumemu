package com.plumemu.coreinput

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/**
 * The one door for input. The Activity hands it every key and joystick event; touch helpers hand
 * it taps ([send]); screens collect [actions].
 *
 * Call everything from the main thread. [scope] runs hold-to-repeat and must use the main
 * dispatcher. Repeats land on display frames, timed by the frame clock in [scope]: MainActivity
 * adds the Choreographer's (`AndroidUiFrameClock`), a composition's scope has one, plain unit
 * tests get a 60 Hz timer ([TimerFrameClock]).
 * Nothing runs while nothing is held: a repeat coroutine exists only between a direction's press
 * and its release, and while held it sleeps until about two frames before each repeat.
 *
 * Buffer: up to [BUFFER_CAPACITY] actions wait for a slow collector, so a burst (Back pressed
 * while a frame is busy) is never lost; if a collector stalls past that, each dropped action is
 * reported to [onStalled]. With no collector at all (no screen started, e.g. between two
 * screens) actions are dropped and reported to [onDebug]: a screen that starts later must not
 * replay stale presses. The one exception is Back: the most recent one is kept and handed to a
 * collector that arrives within [PENDING_BACK_MAX_AGE_MS], so a Back pressed while one screen
 * hands over to the next still goes back. No timer runs for it; its age is checked when the
 * collector arrives.
 */
class InputRouter(
    private val scope: CoroutineScope,
    settings: InputSettings = InputSettings(),
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val onDebug: (String) -> Unit = { Log.d(TAG, it) },
    private val onStalled: (Action) -> Unit = { Log.w(TAG, "Input buffer full, collector stalled; dropped $it") },
) {
    /** Controls settings; read on every press, so a change applies from the next press. */
    var settings: InputSettings = settings
        set(value) {
            field = value
            publishHeld() // the held caps are named by the layout: a button held across a change is renamed at once
        }

    /**
     * Set by a screen that takes text while it does (Search with Plumemu's keyboard), back to
     * [TextEntry.None] when it stops; read on every press. Leaving [TextEntry.Keyboard] stops a
     * held B's erasing at once.
     */
    var textEntry: TextEntry = TextEntry.None
        set(value) {
            field = value
            if (value != TextEntry.Keyboard && repeating is Action.Erase) stopRepeat()
        }

    private val mutableActions = MutableSharedFlow<Action>(extraBufferCapacity = BUFFER_CAPACITY)

    /** When the most recent Back was dropped for want of a collector; null when none waits. */
    private var pendingBackAt: Long? = null

    /** Every action, from gamepad and touch alike. */
    val actions: SharedFlow<Action> =
        mutableActions.onSubscription {
            val droppedAt = pendingBackAt ?: return@onSubscription
            pendingBackAt = null
            val age = clock() - droppedAt
            if (age <= PENDING_BACK_MAX_AGE_MS) {
                emit(Action.Back)
            } else {
                onDebug("No collector for ${age}ms; dropped a pending Back")
            }
        }

    /** Which sources hold each control; a control pressed by two sources at once acts once. */
    private val pressed = HashMap<GamepadControl, MutableSet<Source>>()

    private val heldState = mutableStateOf<Set<String>>(emptySet())

    /**
     * The key caps whose button is held right now ("A", "L1", "START", "↑"...), after layout and swap:
     * a hint with that cap looks pressed. Updated on key down and up and on axis edges, emptied by
     * [releaseAll]. Read it in a draw or layer block only, so a press recomposes nothing.
     */
    val held: State<Set<String>> get() = heldState

    /** Per device, so a resting second controller does not release the first one's direction. */
    private val axisEdges = HashMap<Int, AxisEdges>()
    private var repeatingControl: GamepadControl? = null

    /** The action the held control repeats (a move, or an erase while typing). */
    private var repeating: Action? = null
    private var repeatJob: Job? = null

    internal val isRepeating: Boolean
        get() = repeatJob != null

    /**
     * From `Activity.dispatchKeyEvent`. True when the key is one of core-input's (the Activity
     * must then not pass it on: Android would otherwise turn `BUTTON_B` into Back, for one).
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (textEntry != TextEntry.None) {
            val typed = TypedKeys.of(event.keyCode, event.unicodeChar, TypedKeys.isFromGamepad(event), TypedKeys.isAlphabeticKeyboard(event))
            if (typed != null) return onTyped(typed, event.action == KeyEvent.ACTION_DOWN, event.keyCode, event.deviceId)
        }
        return onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.deviceId)
    }

    /**
     * A hardware keyboard's key while a screen takes text. Its own repeats (a held letter or
     * Backspace) are typed too, as on any keyboard. A key-up also releases the control the same
     * key may have been held as before the screen started taking text (an arrow), or it would repeat forever.
     */
    internal fun onTyped(
        key: TypedKey,
        isDown: Boolean,
        keyCode: Int,
        deviceId: Int = 0,
    ): Boolean {
        if (isDown) emit(Action.Typed(key)) else onKey(keyCode, isDown = false, repeatCount = 0, deviceId = deviceId)
        return true
    }

    /**
     * From `Activity.dispatchGenericMotionEvent`. Joystick events are always taken: left
     * unhandled, Android makes D-pad keys of the stick and hat, and every move would arrive twice.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.actionMasked != MotionEvent.ACTION_MOVE) {
            return false
        }
        onAxes(
            JoystickAxes(
                stickX = event.getAxisValue(MotionEvent.AXIS_X),
                stickY = event.getAxisValue(MotionEvent.AXIS_Y),
                hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
                hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                leftTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                rightTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
            ),
            event.deviceId,
        )
        return true
    }

    /** An action from touch: a tap on an item or a hint, a long press, the system back gesture. */
    fun send(action: Action) = emit(action)

    /**
     * Forget everything held and stop repeating: on window focus loss and when the Activity
     * stops, because the matching key-up may never arrive.
     */
    fun releaseAll() {
        pressed.clear()
        publishHeld()
        axisEdges.clear()
        stopRepeat()
    }

    internal fun onKey(
        keyCode: Int,
        isDown: Boolean,
        repeatCount: Int,
        deviceId: Int = 0,
    ): Boolean {
        val control = GamepadMapping.controlFor(keyCode) ?: return false
        val source = Source(SourceKind.Key, deviceId)
        if (!isDown) {
            release(control, source)
        } else if (repeatCount == 0) {
            // Android's own repeats (repeatCount > 0) are taken but ignored: the rate is ours.
            press(control, source)
        }
        return true
    }

    internal fun onAxes(
        axes: JoystickAxes,
        deviceId: Int = 0,
    ) {
        val held = axisEdges.getOrPut(deviceId) { AxisEdges() }.read(axes)
        holdOnly(Source(SourceKind.Stick, deviceId), setOfNotNull(held.stick))
        holdOnly(Source(SourceKind.Hat, deviceId), held.hat)
        holdOnly(Source(SourceKind.Trigger, deviceId), held.triggers)
    }

    private fun holdOnly(
        source: Source,
        controls: Set<GamepadControl>,
    ) {
        val released = pressed.filter { (control, sources) -> source in sources && control !in controls }.keys
        released.forEach { release(it, source) }
        controls.forEach { press(it, source) }
    }

    private fun press(
        control: GamepadControl,
        source: Source,
    ) {
        val sources = pressed.getOrPut(control) { mutableSetOf() }
        val wasHeld = sources.isNotEmpty()
        if (!sources.add(source) || wasHeld) return
        publishHeld()
        val action = GamepadMapping.actionFor(control, settings, erasesWithB = textEntry == TextEntry.Keyboard) ?: return
        emit(action)
        when (action) {
            is Action.Move -> startRepeat(control, Action.Move(action.direction, isRepeat = true))
            // A held B erases on the same frames as a held direction moves.
            is Action.Erase -> startRepeat(control, Action.Erase(isRepeat = true))
            else -> Unit
        }
    }

    private fun release(
        control: GamepadControl,
        source: Source,
    ) {
        val sources = pressed[control] ?: return
        if (!sources.remove(source) || sources.isNotEmpty()) return
        pressed.remove(control)
        publishHeld()
        if (repeatingControl == control) stopRepeat()
    }

    private fun publishHeld() {
        val names = pressed.keys.mapTo(mutableSetOf()) { GamepadMapping.capNameOf(it, settings) }
        if (names != heldState.value) heldState.value = names
    }

    /** The newest held direction (or erasing B) repeats [repeated]; pressing another one takes over. */
    private fun startRepeat(
        control: GamepadControl,
        repeated: Action,
    ) {
        stopRepeat()
        repeatingControl = control
        repeating = repeated
        val pacer = RepeatPacer(HoldRepeat.intervals(settings.holdToScrollFaster))
        repeatJob =
            scope.launch {
                val frames = coroutineContext[MonotonicFrameClock] ?: TimerFrameClock()
                while (true) {
                    if (frames.withFrameNanos(pacer::onFrame)) emit(repeated)
                    // Asleep until two frames before the next repeat, and at least one message long:
                    // no frame is asked for in between, and the main thread is handed back each frame.
                    if (frames is TimerFrameClock) {
                        frames.rest(pacer.restNanos().coerceAtLeast(NANOS_PER_MS))
                    } else {
                        // Display frame times are System.nanoTime(); the delay counts from now.
                        val rest = pacer.restNanos(sinceFrameNanos = System.nanoTime() - pacer.lastFrameNanos)
                        delay((rest / NANOS_PER_MS).coerceAtLeast(1L))
                    }
                }
            }
    }

    private fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        repeatingControl = null
        repeating = null
    }

    private fun emit(action: Action) {
        if (mutableActions.subscriptionCount.value == 0) {
            // tryEmit would succeed and the action vanish: say so, and keep the latest Back.
            if (action == Action.Back) pendingBackAt = clock()
            onDebug("No collector; dropped $action")
            return
        }
        if (!mutableActions.tryEmit(action)) onStalled(action)
    }

    private enum class SourceKind { Key, Stick, Hat, Trigger }

    private data class Source(
        val kind: SourceKind,
        val deviceId: Int,
    )

    companion object {
        /** Far more than a person can press while one frame is busy; moves repeat at most every 32 ms. */
        const val BUFFER_CAPACITY = 64

        /** A Back older than this is not handed to a collector that arrives late: about a screen change. */
        const val PENDING_BACK_MAX_AGE_MS = 1_000L
        private const val TAG = "PlumInput"
        private const val NANOS_PER_MS = 1_000_000L
    }
}
