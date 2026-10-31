package com.plumemu.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coredata.settings.SwitchSystemWithSetting

/**
 * Which shoulder pair does what on the main screen (Settings › Controls › Switch system with).
 * By default L2 / R2 switch system and L1 / R1 jump a letter in a list or change Size in a
 * grid; with [bumpersSwitch] the two pairs trade roles. The one place that decides them: the main screen
 * reads every action through [read] and prints its key caps from [switchSystemCap] and [sizeCap].
 */
@Immutable
internal data class ShoulderKeys(
    val bumpersSwitch: Boolean,
) {
    /** The key cap of the pair that switches system. */
    @StringRes val switchSystemCap: Int = if (bumpersSwitch) R.string.home_key_l1_r1 else R.string.home_key_l2_r2

    /** The key cap of the pair that jumps a letter and changes Size. */
    @StringRes val sizeCap: Int = if (bumpersSwitch) R.string.home_key_l2_r2 else R.string.home_key_l1_r1

    /**
     * The action as the main screen reads it: [action] as core-input names it (L1 / R1 are the system
     * actions, L2 / R2, key or trigger, the letter jumps), with the two pairs traded when [bumpersSwitch].
     * Applied after the game panel and Search took their turn, so they keep L1 / R1 as before.
     */
    fun read(action: Action): Action =
        if (!bumpersSwitch) {
            action
        } else {
            when (action) {
                Action.PreviousSystem -> Action.JumpLetterPrevious
                Action.NextSystem -> Action.JumpLetterNext
                Action.JumpLetterPrevious -> Action.PreviousSystem
                Action.JumpLetterNext -> Action.NextSystem
                else -> action
            }
        }

    companion object {
        val Default = ShoulderKeys(bumpersSwitch = false)

        fun of(setting: SwitchSystemWithSetting): ShoulderKeys = ShoulderKeys(bumpersSwitch = setting == SwitchSystemWithSetting.Bumpers)
    }
}

/** The shoulder roles for the screen below; `MainScreen` provides them from the stored setting. */
internal val LocalShoulderKeys = staticCompositionLocalOf { ShoulderKeys.Default }
