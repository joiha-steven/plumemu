package com.plumemu.coreinput

import android.view.KeyEvent
import androidx.compose.runtime.Immutable

/**
 * Settings › Controls › Button layout. Android names gamepad keys by **position** (the standard
 * gamepad: `BUTTON_A` south, `BUTTON_B` east, `BUTTON_X` west, `BUTTON_Y` north), so the layout
 * says which position carries which letter, and the button lettered A always confirms:
 *
 * - [Xbox]: south is A and confirms (what Android itself does: its key maps fall back from
 *   `BUTTON_A` to D-pad centre and from `BUTTON_B` to Back).
 * - [Nintendo]: east is A and confirms; A/B and X/Y trade places.
 *
 * Because the confirm button is lettered A in both, hints always say "A" and "B" and nothing
 * outside core-input needs to know the layout. Sources and device notes: core-input/README.md.
 */
enum class ButtonLayout { Xbox, Nintendo }

/**
 * The Controls settings core-input needs. [swapAB] trades A and B after the layout (for a device
 * whose firmware already swaps them, or for a person who prefers it); X and Y stay.
 * [holdToScrollFaster] is "Hold to scroll faster": held moves speed up.
 */
@Immutable
data class InputSettings(
    val buttonLayout: ButtonLayout = ButtonLayout.Xbox,
    val swapAB: Boolean = false,
    val holdToScrollFaster: Boolean = true,
)

/** A logical control: one physical button or direction, whichever key, axis or hat sends it. */
internal enum class GamepadControl {
    Up,
    Down,
    Left,
    Right,

    /** Face buttons by Android key name (position), before layout and swap. */
    ButtonA,
    ButtonB,
    ButtonX,
    ButtonY,
    L1,
    R1,
    L2,
    R2,
    Start,
    Select,

    /** D-pad centre and Enter: always confirm, whatever the layout. */
    Confirm,

    /** Back and Escape: always back, whatever the layout. */
    Cancel,
    ;

    val direction: Direction?
        get() =
            when (this) {
                Up -> Direction.Up
                Down -> Direction.Down
                Left -> Direction.Left
                Right -> Direction.Right
                else -> null
            }
}

/** The letter printed on a face button, after layout and swap. */
internal enum class FaceLetter { A, B, X, Y }

internal object GamepadMapping {
    /**
     * Every key core-input takes as a gamepad control; any other key (volume, home, keyboard letters)
     * is left to Android, except while a screen takes text ([TypedKeys]).
     */
    fun controlFor(keyCode: Int): GamepadControl? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> GamepadControl.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadControl.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadControl.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadControl.Right
            KeyEvent.KEYCODE_BUTTON_A -> GamepadControl.ButtonA
            KeyEvent.KEYCODE_BUTTON_B -> GamepadControl.ButtonB
            KeyEvent.KEYCODE_BUTTON_X -> GamepadControl.ButtonX
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadControl.ButtonY
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadControl.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadControl.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadControl.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadControl.R2
            KeyEvent.KEYCODE_BUTTON_START -> GamepadControl.Start
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadControl.Select
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> GamepadControl.Confirm
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> GamepadControl.Cancel
            else -> null
        }

    fun letterOf(
        control: GamepadControl,
        settings: InputSettings,
    ): FaceLetter? {
        val byLayout =
            when (settings.buttonLayout) {
                ButtonLayout.Xbox ->
                    when (control) {
                        GamepadControl.ButtonA -> FaceLetter.A
                        GamepadControl.ButtonB -> FaceLetter.B
                        GamepadControl.ButtonX -> FaceLetter.X
                        GamepadControl.ButtonY -> FaceLetter.Y
                        else -> return null
                    }
                ButtonLayout.Nintendo ->
                    when (control) {
                        GamepadControl.ButtonA -> FaceLetter.B
                        GamepadControl.ButtonB -> FaceLetter.A
                        GamepadControl.ButtonX -> FaceLetter.Y
                        GamepadControl.ButtonY -> FaceLetter.X
                        else -> return null
                    }
            }
        if (!settings.swapAB) return byLayout
        return when (byLayout) {
            FaceLetter.A -> FaceLetter.B
            FaceLetter.B -> FaceLetter.A
            else -> byLayout
        }
    }

    /**
     * The name of the key cap that stands for [control] in a hint ("A", "L1", "START", "→"): the letter
     * printed on the physical button after the layout but **not** the swap, because hints print the
     * physical letter (`ButtonLetters`: under Confirm with B the confirm hint says "B", and that
     * button, the one printed B, lights it). Confirm lights A and
     * Cancel lights B, as they act. The names are what a hint's key cap text says, so core-ui can match
     * them without knowing core-input.
     */
    fun capNameOf(
        control: GamepadControl,
        settings: InputSettings,
    ): String =
        when (control) {
            GamepadControl.Up -> "↑"
            GamepadControl.Down -> "↓"
            GamepadControl.Left -> "←"
            GamepadControl.Right -> "→"
            GamepadControl.ButtonA, GamepadControl.ButtonB, GamepadControl.ButtonX, GamepadControl.ButtonY ->
                letterOf(control, settings.copy(swapAB = false))?.name ?: control.name
            GamepadControl.L1 -> "L1"
            GamepadControl.R1 -> "R1"
            GamepadControl.L2 -> "L2"
            GamepadControl.R2 -> "R2"
            GamepadControl.Start -> "START"
            GamepadControl.Select -> "SELECT"
            // The confirm hint prints "B" under Confirm with B (swapAB), so D-pad centre and Enter light that cap.
            GamepadControl.Confirm -> (if (settings.swapAB) FaceLetter.B else FaceLetter.A).name
            GamepadControl.Cancel -> (if (settings.swapAB) FaceLetter.A else FaceLetter.B).name
        }

    /**
     * The action a control sends when pressed, or null for a control that is taken (so Android
     * does not turn it into something else) but has no action of its own (the D-pad's directions
     * arrive as moves). With [erasesWithB] (Plumemu's keyboard is up, [TextEntry.Keyboard]) the
     * letter B erases instead of going back; Back and Escape ([GamepadControl.Cancel]) still go back.
     */
    fun actionFor(
        control: GamepadControl,
        settings: InputSettings,
        erasesWithB: Boolean = false,
    ): Action? {
        control.direction?.let { return Action.Move(it) }
        return when (control) {
            GamepadControl.ButtonA, GamepadControl.ButtonB, GamepadControl.ButtonX, GamepadControl.ButtonY ->
                when (letterOf(control, settings)) {
                    FaceLetter.A -> Action.Enter
                    FaceLetter.B -> if (erasesWithB) Action.Erase() else Action.Back
                    FaceLetter.X -> Action.Search
                    FaceLetter.Y -> Action.ToggleFavorite
                    null -> null
                }
            GamepadControl.L1 -> Action.PreviousSystem
            GamepadControl.R1 -> Action.NextSystem
            GamepadControl.L2 -> Action.JumpLetterPrevious
            GamepadControl.R2 -> Action.JumpLetterNext
            GamepadControl.Start -> Action.Menu
            GamepadControl.Select -> Action.ToggleView
            GamepadControl.Confirm -> Action.Enter
            GamepadControl.Cancel -> Action.Back
            GamepadControl.Up, GamepadControl.Down, GamepadControl.Left, GamepadControl.Right -> null
        }
    }
}
