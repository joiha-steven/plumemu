package com.plumemu.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting

/**
 * The letters hints print for confirm and back: "A" confirms and "B" goes back out
 * of the box (the button printed A sends BUTTON_A on the AYN Thor); with Settings › Advanced ›
 * "Confirm with B" the button printed B confirms, so the letters swap.
 * X (search) and Y (favorite) never swap: they follow the printed letters whatever confirms.
 * The one place that decides them; screens take their key caps from here.
 */
@Immutable
internal data class ButtonLetters(
    @StringRes val confirm: Int,
    @StringRes val back: Int,
) {
    @StringRes val favorite: Int = R.string.home_key_y

    @StringRes val search: Int = R.string.home_key_x

    companion object {
        fun of(setting: ConfirmButtonSetting): ButtonLetters =
            when (setting) {
                ConfirmButtonSetting.Right -> ButtonLetters(confirm = R.string.home_key_a, back = R.string.home_key_b)
                ConfirmButtonSetting.Bottom -> ButtonLetters(confirm = R.string.home_key_b, back = R.string.home_key_a)
            }

        val Default: ButtonLetters = of(ConfirmButtonSetting.Right)
    }
}

/** The letters for the screen below; `MainScreen` provides them from the stored setting. */
internal val LocalButtonLetters = staticCompositionLocalOf { ButtonLetters.Default }
