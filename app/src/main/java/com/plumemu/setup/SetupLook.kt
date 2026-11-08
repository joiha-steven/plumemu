package com.plumemu.setup

import androidx.compose.runtime.Immutable
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet

/*
 * Setup's "Pick a look": a few of Settings' ready-made colour sets, one chosen beforehand for the
 * device. Everything stays editable in Settings › Appearance; setup writes only what the person picks.
 */

internal object SetupLooks {
    /** The sets offered: two dark, one dark with colour, three light; two rows of [COLUMNS]. */
    val sets: List<ColorSet> = listOf(ColorSet.Plumemu, ColorSet.ColdGrey, ColorSet.SpiceCube, ColorSet.Daylight, ColorSet.Ivory, ColorSet.Pocket)

    const val COLUMNS = 3
}

/** What Plumemu can tell of the device the look is chosen for. */
@Immutable
internal data class DeviceFacts(
    val manufacturer: String = "",
    val model: String = "",
    /** The main screen's size in dp, either way round. */
    val widthDp: Float = 0f,
    val heightDp: Float = 0f,
    /** `Build.BRAND`; a real Retroid Pocket Nova says "qti" here. */
    val brand: String = "",
)

/**
 * What the device suggests: [colorSet] the tile the step opens on (null: no suggestion, the current look),
 * [textSize] a size to apply with the pick (null: keep what is stored), [blackPage] true on an OLED screen, where the
 * default set's tile draws and applies true black as its page, keeping its accent and tones.
 */
@Immutable
internal data class DeviceLook(
    val colorSet: ColorSet? = null,
    val textSize: TextSizeSetting? = null,
    val blackPage: Boolean = false,
) {
    /** The page [set]'s tile draws and applies: Black for the default set on an OLED screen, else the set's own. */
    fun pageOf(set: ColorSet): BackgroundChoice = if (blackPage && set == ColorSet.Plumemu) BLACK else set.background

    companion object {
        val None = DeviceLook()
        private val BLACK = BackgroundChoice.Preset(BackgroundPreset.Black)
    }
}

/**
 * The dark default on true black on an OLED screen (black pixels are off: power, and no glow round the focus on a black panel); a larger text size on a small 4:3 screen.
 * Android does not say what a panel is made of, so OLED is a short list of known handhelds by name.
 */
internal fun deviceLookOf(facts: DeviceFacts): DeviceLook =
    DeviceLook(
        colorSet = ColorSet.Plumemu.takeIf { isOledHandheld(facts) },
        blackPage = isOledHandheld(facts),
        textSize = TextSizeSetting.Larger.takeIf { isSmallFourThree(facts) },
    )

// Only the Retroid Pocket Nova's strings were read from a real device (manufacturer "Moorechip", model
// "Retroid Pocket Nova", brand "qti"): Retroids name themselves in the model, not the manufacturer.
// The Thor's and the other Retroids' strings are assumed.
private fun isOledHandheld(facts: DeviceFacts): Boolean {
    val model = facts.model.lowercase()
    val isAyn = facts.manufacturer.lowercase().contains("ayn") || facts.brand.lowercase().contains("ayn")
    val isThor = (isAyn && model.contains("thor")) || model.contains("ayn thor")
    return isThor || listOf("retroid pocket 5", "retroid pocket 6", "retroid pocket mini").any { model.contains(it) }
}

/** 4:3 (within a tenth either way) and under 700 dp wide, like the Retroid Pocket Nova's 576 x 432. */
internal fun isSmallFourThree(facts: DeviceFacts): Boolean {
    val long = maxOf(facts.widthDp, facts.heightDp)
    val short = minOf(facts.widthDp, facts.heightDp)
    if (short <= 0f) return false
    return long / short in FOUR_THREE_MIN..FOUR_THREE_MAX && long < SMALL_MAX_DP
}

private const val FOUR_THREE_MIN = 1.23f
private const val FOUR_THREE_MAX = 1.43f
private const val SMALL_MAX_DP = 700f
