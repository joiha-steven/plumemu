package com.plumemu.coreinput.strip

import android.os.Bundle
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The main screen's focus and the strip's position, for Compose. Feed it actions with
 * [dispatch]; read [focus] for what is focused and [translationX] (px) inside
 * `Modifier.graphicsLayer { translationX = state.translationX }`, which reads it at draw time so
 * a slide never recomposes or re-lays out anything.
 *
 * At rest the strip follows its stop, so a rotation or a new peek width moves it without
 * animation. A focus change slides it with the given spec, or jumps when motion is off. The focus
 * survives configuration changes and process death; the position is derived from it.
 */
@Stable
class StripState internal constructor(
    initialFocus: StripFocus,
    content: StripContent,
    geometry: StripGeometry,
    density: Density,
    internal var isMotionOn: Boolean,
    internal var animationSpec: AnimationSpec<Float>,
    internal var scope: CoroutineScope,
) {
    private var rawFocus by mutableStateOf(initialFocus)
    private var contentState by mutableStateOf(content)

    /**
     * New content first fixes the focus as it stood on the old content (the row found by its
     * game's id), so when the focused game then leaves the list the cursor falls back to the row
     * the person saw, not to the index remembered before earlier re-sorts. This holds for the
     * focused system only: other systems' remembered rows are found again when they are focused,
     * and fall back to their last remembered index if their game is gone by then.
     *
     * A details stop the new content no longer has (its entry lost [SystemEntry.hasDetails]: the
     * app's second screen took the details) moves the cursor to the games column for good, on the
     * same row, and ends a slide still heading for the old stop: the strip rests at the list in the
     * same frame, and when the stop comes back the cursor stays on the list.
     */
    internal var content: StripContent
        get() = contentState
        set(value) {
            if (value === contentState) return
            rawFocus = rawFocus.reconciled(contentState, isNarrow && contentState.hasBoxArt)
            contentState = value
            if (rawFocus.column == Column.Details && rawFocus.reconciled(value, isNarrow && value.hasBoxArt).column == Column.Games) {
                rawFocus = rawFocus.copy(column = Column.Games)
                stopAnimation()
                movingOffset = Float.NaN
            }
        }
    internal var geometry by mutableStateOf(geometry)
    internal var density by mutableStateOf(density)

    /** NaN while resting at the focused column's stop; a value while dragged or animating. */
    private var movingOffset by mutableFloatStateOf(Float.NaN)
    private var animation: Job? = null

    /** Where the running slide ends, in px; NaN when none runs. */
    private var animationTarget = Float.NaN

    internal val draggableState = DraggableState { delta -> dragBy(delta) }

    private val isNarrow: Boolean
        get() = geometry.isNarrow

    /**
     * The focus as last set, before reconciling with the content: what is saved, so a focus
     * restored while the library is still empty is not lost.
     */
    internal val savedFocus: StripFocus
        get() = rawFocus

    /**
     * What is focused, valid for the current content. While the content has no systems yet this
     * is the restored or last focus unchanged; do not index the (empty) system list with it.
     */
    val focus: StripFocus
        get() = rawFocus.reconciled(content, isNarrow && content.hasBoxArt)

    /** The strip's horizontal translation in px (negative when slid left). */
    val translationX: Float
        get() = movingOffset.takeUnless { it.isNaN() } ?: stopOffsetPx(focus.column)

    /** [translationX] in dp. */
    val offset: Dp
        get() = with(density) { translationX.toDp() }

    /** Whether the strip is moving (dragged or sliding); false at rest. */
    val isMoving: Boolean
        get() = !movingOffset.isNaN()

    /**
     * How much of the system names to draw, from where the strip actually is: 1 at the systems
     * stop, 0 at the games stop and past it, in between while it slides or is dragged. Read it
     * while drawing (`graphicsLayer { alpha = ... }`): it follows [translationX] every frame.
     */
    val systemNamesAlpha: Float
        get() = namesAlphaAt(translationX, stopOffsetPx(Column.Systems), stopOffsetPx(Column.Games), focus.column == Column.Systems)

    /**
     * Settings › Controls › Push right to play: the screen copies the stored setting here; read on
     * every [dispatch], so a change applies from the next press. See [StripReducer.reduce].
     */
    var pushRightPlays: Boolean = false

    /** Whether the focused system has the narrow details stop (narrow screen, box art, an entry with details): Right in its list goes there. */
    val hasDetailsStop: Boolean
        get() = isNarrow && contentState.hasBoxArt && contentState.systems.getOrNull(focus.systemIndex)?.hasDetails == true

    /** The game armed by a first Right while [pushRightPlays] is on; see [PushRightArm]. */
    var pushRightArm = PushRightArm()

    /** Whether the focused game is armed: the hint bar then says "Again to play". Compose state. */
    val isPushRightArmed: Boolean
        get() = pushRightArm.armedKey == focusKey()

    /** The focused game by its id (the row only when the entry gives no ids), so a re-sort or a rescan never moves the arming to another game. */
    private fun focusKey(): String {
        val entry = content.systems.getOrNull(focus.systemIndex)
        return "${focus.systemId}/${entry?.itemIds?.getOrNull(focus.row) ?: "row${focus.row}"}"
    }

    /** Applies [action] to the focus and returns what the screen must do. */
    fun dispatch(action: Action): List<StripIntent> {
        val key = focusKey()
        val isArmed = pushRightPlays && pushRightArm.isArmedFor(key)
        val reduction = StripReducer.reduce(focus, action, content, isNarrow, pushRightPlays, isArmed)
        // Any input but Right (a held Right included) puts the arming away; so does a play.
        val isHeldRight = action is Action.Move && action.direction == Direction.Right && action.isRepeat
        when {
            reduction.armsRight -> pushRightArm.arm(key, scope)
            !isHeldRight -> pushRightArm.disarm()
        }
        moveFocus(reduction.focus)
        return reduction.intents
    }

    /** Focus game [index] of the focused system, e.g. after a letter jump ([StripIntent.JumpLetter]). */
    fun focusRow(index: Int) = moveFocus(StripReducer.focusRow(focus, index, content, isNarrow))

    /**
     * Focus game 0 of system [systemId] in the games column (the Home button's landing, the
     * setup's end): the strip slides there with the usual spec, or jumps when motion is off.
     */
    fun landOn(systemId: String) = moveFocus(StripReducer.landOn(focus, systemId, content, isNarrow))

    internal fun stopOffsetPx(column: Column): Float = with(density) { geometry.offsetOf(column).toPx() }

    internal fun stopAnimation() {
        animation?.cancel()
        animation = null
        animationTarget = Float.NaN
    }

    /** A horizontal drag: the strip follows the finger, held between its first and last stop. */
    internal fun dragBy(deltaPx: Float) {
        stopAnimation()
        val stops = geometry.stops(focus, content).map { stopOffsetPx(it) }
        movingOffset = (translationX + deltaPx).coerceIn(stops.min(), stops.max())
    }

    /** The finger lifted at [velocityPx] px/s: focus the stop it settles on and slide there. */
    internal fun settle(velocityPx: Float) {
        val stops = geometry.stops(focus, content).map { it to stopOffsetPx(it) }
        val flingVelocity = FLING_VELOCITY_DP_PER_SECOND * density.density
        val start = translationX
        val column = settleStop(start, velocityPx, stops, flingVelocity)
        rawFocus = StripReducer.focusColumn(focus, column, content, isNarrow)
        slideToStop(start, velocityPx)
    }

    private fun moveFocus(newFocus: StripFocus) {
        val oldColumn = focus.column
        // Read before the focus changes: at rest the position follows the focused column.
        val start = translationX
        rawFocus = newFocus
        if (focus.column == oldColumn && !isMoving) return
        // A move within the column while the strip slides there (a row step right after Right):
        // the slide already heads for this stop, so it runs on rather than starting over.
        if (animation != null && animationTarget == stopOffsetPx(focus.column)) return
        slideToStop(start, velocityPx = 0f)
    }

    private fun slideToStop(
        start: Float,
        velocityPx: Float,
    ) {
        stopAnimation()
        val target = stopOffsetPx(focus.column)
        if (!isMotionOn || start == target) {
            movingOffset = Float.NaN
            return
        }
        movingOffset = start
        animationTarget = target
        animation =
            scope.launch {
                animate(start, target, velocityPx, animationSpec) { value, _ -> movingOffset = value }
                // Reached the stop: rest there (cancelled animations leave the offset to their successor).
                movingOffset = Float.NaN
                animation = null
                animationTarget = Float.NaN
            }
    }

    companion object {
        /** A release this fast goes on to the next stop; the same threshold Compose's pager uses. */
        const val FLING_VELOCITY_DP_PER_SECOND = 400f
    }
}

/**
 * The system names' alpha with the strip at [x]: 1 at [systemsStop], 0 at [gamesStop] and beyond
 * (the narrow details stop lies further on), linear in between. Where the two stops coincide the
 * focused column decides.
 */
internal fun namesAlphaAt(
    x: Float,
    systemsStop: Float,
    gamesStop: Float,
    isSystemsFocused: Boolean,
): Float {
    if (systemsStop == gamesStop) return if (isSystemsFocused) 1f else 0f
    return ((x - gamesStop) / (systemsStop - gamesStop)).coerceIn(0f, 1f)
}

/**
 * The strip state for a screen. [geometry] comes from the theme's layout, [isMotionOn] and
 * [animationSpec] from `PlumTheme.motion` (180 ms ease-out). Pass new values freely; the state
 * keeps the focus and follows them.
 */
@Composable
fun rememberStripState(
    content: StripContent,
    geometry: StripGeometry,
    isMotionOn: Boolean,
    animationSpec: AnimationSpec<Float>,
): StripState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state =
        rememberSaveable(saver = stripStateSaver(content, geometry, density, isMotionOn, animationSpec, scope)) {
            StripState(StripFocus(), content, geometry, density, isMotionOn, animationSpec, scope)
        }
    // Assigned during composition, like rememberUpdatedState: a screen reading `state.focus` in
    // this same composition must see it reconciled against this content (a rescan that removes
    // systems would otherwise leave an index past the end for one frame).
    state.content = content
    state.geometry = geometry
    state.density = density
    state.isMotionOn = isMotionOn
    state.animationSpec = animationSpec
    state.scope = scope
    return state
}

private fun stripStateSaver(
    content: StripContent,
    geometry: StripGeometry,
    density: Density,
    isMotionOn: Boolean,
    animationSpec: AnimationSpec<Float>,
    scope: CoroutineScope,
): Saver<StripState, Bundle> =
    Saver(
        save = { StripFocusBundle.save(it.savedFocus) },
        restore = { StripState(StripFocusBundle.restore(it), content, geometry, density, isMotionOn, animationSpec, scope) },
    )

/** The focus as a Bundle, which survives process death in the saved instance state. */
internal object StripFocusBundle {
    private const val COLUMN = "column"
    private const val SYSTEM_INDEX = "systemIndex"
    private const val SYSTEM_ID = "systemId"
    private const val ROW_IDS = "rowIds"
    private const val ROW_VALUES = "rowValues"
    private const val ROW_ID_SYSTEMS = "rowIdSystems"
    private const val ROW_ID_VALUES = "rowIdValues"

    fun save(focus: StripFocus): Bundle =
        Bundle().apply {
            putString(COLUMN, focus.column.name)
            putInt(SYSTEM_INDEX, focus.systemIndex)
            putString(SYSTEM_ID, focus.systemId)
            val entries = focus.rows.entries.toList()
            putStringArray(ROW_IDS, entries.map { it.key }.toTypedArray())
            putIntArray(ROW_VALUES, entries.map { it.value }.toIntArray())
            val ids = focus.rowIds.entries.toList()
            putStringArray(ROW_ID_SYSTEMS, ids.map { it.key }.toTypedArray())
            putStringArray(ROW_ID_VALUES, ids.map { it.value }.toTypedArray())
        }

    fun restore(bundle: Bundle): StripFocus {
        val ids = bundle.getStringArray(ROW_IDS).orEmpty()
        val values = bundle.getIntArray(ROW_VALUES) ?: IntArray(0)
        val idSystems = bundle.getStringArray(ROW_ID_SYSTEMS).orEmpty()
        val idValues = bundle.getStringArray(ROW_ID_VALUES).orEmpty()
        return StripFocus(
            column = Column.entries.firstOrNull { it.name == bundle.getString(COLUMN) } ?: Column.Systems,
            systemIndex = bundle.getInt(SYSTEM_INDEX),
            systemId = bundle.getString(SYSTEM_ID),
            rows = ids.zip(values.toList()).toMap(),
            rowIds = idSystems.zip(idValues).toMap(),
        )
    }
}
