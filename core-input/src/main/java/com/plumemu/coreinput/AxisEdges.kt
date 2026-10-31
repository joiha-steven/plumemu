package com.plumemu.coreinput

import kotlin.math.abs

/** One reading of the joystick axes core-input uses (Android: Y and hat Y are positive downwards). */
internal data class JoystickAxes(
    val stickX: Float = 0f,
    val stickY: Float = 0f,
    val hatX: Float = 0f,
    val hatY: Float = 0f,
    /** The larger of `AXIS_LTRIGGER` and `AXIS_BRAKE` (devices report one or the other). */
    val leftTrigger: Float = 0f,
    /** The larger of `AXIS_RTRIGGER` and `AXIS_GAS`. */
    val rightTrigger: Float = 0f,
)

/**
 * Turns one device's axis readings into held controls, so a stick, hat or analogue trigger behaves like a
 * button: one press when it crosses [PRESS], released when it falls under [RELEASE]. The gap
 * between the two keeps a stick resting near the threshold from chattering presses.
 */
internal class AxisEdges {
    private var stick: GamepadControl? = null
    private var hatHorizontal: GamepadControl? = null
    private var hatVertical: GamepadControl? = null
    private var leftTrigger = false
    private var rightTrigger = false

    /** What each axis source holds after this reading. */
    data class Held(
        val stick: GamepadControl?,
        val hat: Set<GamepadControl>,
        val triggers: Set<GamepadControl>,
    )

    fun read(axes: JoystickAxes): Held {
        stick = stickControl(axes.stickX, axes.stickY, stick)
        hatHorizontal = hatControl(axes.hatX, hatHorizontal, GamepadControl.Left, GamepadControl.Right)
        hatVertical = hatControl(axes.hatY, hatVertical, GamepadControl.Up, GamepadControl.Down)
        leftTrigger = triggerHeld(axes.leftTrigger, leftTrigger)
        rightTrigger = triggerHeld(axes.rightTrigger, rightTrigger)
        return Held(
            stick = stick,
            hat = setOfNotNull(hatHorizontal, hatVertical),
            triggers =
                buildSet {
                    if (leftTrigger) add(GamepadControl.L2)
                    if (rightTrigger) add(GamepadControl.R2)
                },
        )
    }

    fun reset() {
        stick = null
        hatHorizontal = null
        hatVertical = null
        leftTrigger = false
        rightTrigger = false
    }

    private fun stickControl(
        x: Float,
        y: Float,
        current: GamepadControl?,
    ): GamepadControl? {
        val ax = abs(x)
        val ay = abs(y)
        val candidate =
            if (ax > ay) {
                if (x > 0f) GamepadControl.Right else GamepadControl.Left
            } else {
                if (y > 0f) GamepadControl.Down else GamepadControl.Up
            }
        val strongest = maxOf(ax, ay)
        return when {
            // Angular hysteresis: near 45° the larger axis flips with every bit of jitter; the
            // held direction stays until the other axis leads it clearly.
            strongest >= PRESS && current != null && candidate != current && along(current, x, y) >= RELEASE &&
                strongest < along(current, x, y) + ANGLE_MARGIN -> current
            strongest >= PRESS -> candidate
            current == null -> null
            // Under the press threshold: keep holding while the held direction stays over release.
            along(current, x, y) >= RELEASE -> current
            else -> null
        }
    }

    private fun along(
        control: GamepadControl,
        x: Float,
        y: Float,
    ): Float =
        when (control) {
            GamepadControl.Right -> x
            GamepadControl.Left -> -x
            GamepadControl.Down -> y
            GamepadControl.Up -> -y
            else -> 0f
        }

    private fun hatControl(
        value: Float,
        current: GamepadControl?,
        negative: GamepadControl,
        positive: GamepadControl,
    ): GamepadControl? =
        when {
            value >= PRESS -> positive
            value <= -PRESS -> negative
            current == positive && value >= RELEASE -> current
            current == negative && value <= -RELEASE -> current
            else -> null
        }

    private fun triggerHeld(
        value: Float,
        held: Boolean,
    ): Boolean = value >= (if (held) RELEASE else PRESS)

    companion object {
        /** Half way: well past the drift of a worn stick, comfortably reached by a deliberate push. */
        const val PRESS = 0.5f

        /** Released under 30 %: enough gap below [PRESS] that a stick held at the edge does not chatter. */
        const val RELEASE = 0.3f

        /**
         * How far the other axis must lead the held one before the direction turns: about 8° off
         * the diagonal at full tilt, well above a stick's jitter, well below a deliberate turn.
         */
        const val ANGLE_MARGIN = 0.15f
    }
}
