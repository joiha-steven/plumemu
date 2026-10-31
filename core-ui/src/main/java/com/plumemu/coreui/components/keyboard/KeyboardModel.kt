package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.Immutable
import kotlin.math.abs

/*
 * The keyboard's rules, pure Kotlin: focus, page and shift, and what a move or a press does.
 * KeyboardState holds a KeyboardModel for Compose; the tests drive the model directly.
 */

/** Shift: off; [Once] types one upper-case letter and turns off; [Locked] stays until pressed again. */
enum class ShiftMode {
    Off,
    Once,
    Locked,
    ;

    /** The shift key's cycle: off → once → locked → off. */
    fun next(): ShiftMode =
        when (this) {
            Off -> Once
            Once -> Locked
            Locked -> Off
        }
}

/** A key on the current page: its row (0 at the top) and its place among that row's keys. */
@Immutable
data class KeyId(
    val row: Int,
    val index: Int,
)

/** What pressing a key tells the caller. Page and shift keys tell it nothing. */
@Immutable
sealed interface KeyboardEvent {
    data class Type(
        val char: Char,
    ) : KeyboardEvent

    data object Delete : KeyboardEvent

    data object Space : KeyboardEvent

    /** The field's action key (Search, Next, Sign in, Done). */
    data object Action : KeyboardEvent

    /** The hide key: the caller puts the keyboard away. */
    data object Hide : KeyboardEvent
}

/** A direction on the keyboard (core-ui does not know core-input's actions; the caller maps them). */
enum class KeyboardDirection {
    Up,
    Down,
    Left,
    Right,
}

/** What a move did. Leaving is the caller's to handle: the keyboard's focus does not change. */
enum class KeyboardMove {
    Moved,

    /** A held key reached the end of the row (or the top or bottom row) and stopped there. */
    Stayed,

    /** Up from the top row. */
    LeftAbove,

    /** Down from the bottom row. */
    LeftBelow,
}

/**
 * The keyboard at one moment. [anchor] is the column centre a run of Up and Down presses aims
 * at (the key's centre where the run started), so Up then Down comes back to the same key; any
 * other move, a tap or a page change clears it. [cameFrom] is the key the last Up or Down left, so that
 * the opposite move picks it again when it is one of two as near.
 */
@Immutable
data class KeyboardModel(
    val hasShift: Boolean,
    val page: KeyboardPage,
    val focus: KeyId = KeyId(0, 0),
    val shift: ShiftMode = ShiftMode.Off,
    val anchor: Float? = null,
    val cameFrom: KeyId? = null,
) {
    val rows: List<List<KeySlot>> get() = KeyboardLayout.rows(page, hasShift)

    val focusedSlot: KeySlot get() = rows[focus.row][focus.index]

    /**
     * Moves the focus. Left and Right step one key; at the end of a row a fresh press wraps to
     * the other end and a held one ([isRepeat]) stops. Up and Down go to the key of the next row
     * whose centre is nearest the anchor (of two as near, the key the last Up or Down just left, then the one nearer the key the move starts on, then Down takes the right one and Up the left one, as on a
     * physical keyboard where each row sits half a key to the right of the one above it); past the top or bottom
     * row a fresh press leaves the keyboard and a held one stops, so holding a direction never
     * carries the cursor out of it.
     */
    fun move(
        direction: KeyboardDirection,
        isRepeat: Boolean,
    ): Pair<KeyboardModel, KeyboardMove> =
        when (direction) {
            KeyboardDirection.Left -> step(-1, isRepeat)
            KeyboardDirection.Right -> step(1, isRepeat)
            KeyboardDirection.Up -> vertical(-1, isRepeat)
            KeyboardDirection.Down -> vertical(1, isRepeat)
        }

    /** Presses the focused key: the event for the caller (null for page and shift keys) and the new model. */
    fun press(): Pair<KeyboardModel, KeyboardEvent?> =
        when (val key = focusedSlot.key) {
            is KeyboardKey.Character -> typed(key.char)
            KeyboardKey.Shift -> copy(shift = shift.next()) to null
            is KeyboardKey.Page -> toPage(key.target) to null
            KeyboardKey.Delete -> this to KeyboardEvent.Delete
            KeyboardKey.Space -> this to KeyboardEvent.Space
            KeyboardKey.Action -> this to KeyboardEvent.Action
            KeyboardKey.Hide -> this to KeyboardEvent.Hide
        }

    /** A tap: focus [key] and press it at once (one tap types). */
    fun tap(key: KeyId): Pair<KeyboardModel, KeyboardEvent?> = focusOn(key).press()

    /** Focus [key] on this page (a tap, or a test placing the cursor). */
    fun focusOn(key: KeyId): KeyboardModel {
        require(key.row in rows.indices && key.index in rows[key.row].indices) { "no key $key on $page" }
        return copy(focus = key, anchor = null, cameFrom = null)
    }

    /** The shift key's cycle without moving the focus (a button such as SELECT). No-op without shift. */
    fun toggleShift(): KeyboardModel = if (hasShift) copy(shift = shift.next()) else this

    /** Letters ↔ symbols without moving to the page key (a button such as Y); from Symbols 2, letters. */
    fun togglePage(): KeyboardModel = toPage(if (page == KeyboardPage.Letters) KeyboardPage.Symbols else KeyboardPage.Letters)

    /** [target], the focus on the same row at the key nearest the current key's centre. */
    fun toPage(target: KeyboardPage): KeyboardModel {
        val centre = focusedSlot.centre
        val next = copy(page = target, anchor = null, cameFrom = null)
        return next.copy(focus = KeyId(focus.row, nearestIn(next.rows[focus.row], centre)))
    }

    private fun typed(char: Char): Pair<KeyboardModel, KeyboardEvent?> {
        if (!char.isLetter() || shift == ShiftMode.Off) return this to KeyboardEvent.Type(char)
        val after = if (shift == ShiftMode.Once) ShiftMode.Off else shift
        return copy(shift = after) to KeyboardEvent.Type(char.uppercaseChar())
    }

    private fun step(
        by: Int,
        isRepeat: Boolean,
    ): Pair<KeyboardModel, KeyboardMove> {
        val size = rows[focus.row].size
        val target = focus.index + by
        val index =
            when {
                target in 0 until size -> target
                isRepeat -> return this to KeyboardMove.Stayed
                else -> (target + size) % size
            }
        return copy(focus = focus.copy(index = index), anchor = null, cameFrom = null) to KeyboardMove.Moved
    }

    private fun vertical(
        by: Int,
        isRepeat: Boolean,
    ): Pair<KeyboardModel, KeyboardMove> {
        val row = focus.row + by
        if (row !in rows.indices) {
            return this to
                when {
                    isRepeat -> KeyboardMove.Stayed
                    by < 0 -> KeyboardMove.LeftAbove
                    else -> KeyboardMove.LeftBelow
                }
        }
        val aim = anchor ?: focusedSlot.centre
        return copy(focus = KeyId(row, nearestIn(rows[row], aim, focusedSlot.centre, tieToRight = by > 0, prefer = cameFrom?.takeIf { it.row == row }?.index)), anchor = aim, cameFrom = focus) to KeyboardMove.Moved
    }

    private companion object {
        /**
         * The index of the key in [row] whose centre is nearest [aim]. Of a tie, [prefer] (the key this move's opposite came from), then the one nearest [from] (the key
         * the move starts on, so a move and its opposite retrace the same keys even with a remembered anchor);
         * if still tied, the right one when [tieToRight] (going down), else the left one.
         */
        fun nearestIn(
            row: List<KeySlot>,
            aim: Float,
            from: Float = aim,
            tieToRight: Boolean = false,
            prefer: Int? = null,
        ): Int {
            val order = if (tieToRight) row.indices.reversed() else row.indices
            return order.minWith(compareBy<Int>({ abs(row[it].centre - aim) }, { if (it == prefer) 0 else 1 }, { abs(row[it].centre - from) }))
        }
    }
}

/** The model a field's keyboard starts with: its start page, focus on the first key, shift off. */
fun KeyboardSpec.startModel(): KeyboardModel = KeyboardModel(hasShift = hasShift, page = startPage)
