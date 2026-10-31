package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb

/*
 * The writes of Background, Text color and Accent color: nothing
 * unreadable is stored by hand. A background keeps the text and accent if they still read and moves
 * them (lightness only) if not; a text or accent pick that does not read is refused. The result
 * says what happened so the screen can tell the person; the stored value is the only thing these
 * functions change. What is drawn for a stored value that fails is settled by core-ui's
 * `PlumThemeSettings`, not here.
 */

/**
 * A pick that does not read on the background: [nearest] is the colour of the same hue that does.
 * [role] null: the pick is a background no text can read on ([Readability.backgroundAllowsText]).
 */
internal data class ColorRefusal(
    val role: ColorRole?,
    val nearest: Rgb,
)

/**
 * The outcome of one colour write. [appearance] is the stored result (unchanged when [refusal] is
 * set); [textAdjusted] / [accentAdjusted] say a background change moved the person's text or accent
 * colour to keep it readable.
 */
internal data class ColorWrite(
    val appearance: AppearanceSettings,
    val refusal: ColorRefusal? = null,
    val textAdjusted: Boolean = false,
    val accentAdjusted: Boolean = false,
) {
    val adjusted: Boolean get() = textAdjusted || accentAdjusted
}

/**
 * [color] as the background; the text and accent follow [Readability.settle]. [baseText] is the text
 * to keep if it still reads (the current one by default; a colour panel passes the one it opened
 * with, so browsing presets does not drift a custom text away step by step); [baseAccent] likewise
 * for the accent, which is put back as it was whenever it reads again.
 */
internal fun AppearanceSettings.writeBackground(
    color: ColorSetting,
    baseText: TextColorSetting = textColor,
    baseAccent: ColorSetting = accent,
): ColorWrite {
    val background = color.toBackgroundChoice().color
    val settled = Readability.settle(background, baseText.toRgb(), baseAccent.toAccentChoice())
    return ColorWrite(
        appearance =
            copy(
                background = color,
                textColor = if (settled.textAdjusted) settled.text.toTextSetting() else baseText,
                accent = if (settled.accentAdjusted) settled.accent.toSetting() else baseAccent,
            ),
        textAdjusted = settled.textAdjusted,
        accentAdjusted = settled.accentAdjusted,
    )
}

/** [pick] as the text, or a refusal when it does not read on the current background. */
internal fun AppearanceSettings.writeText(pick: TextColorSetting): ColorWrite {
    val background = background.toBackgroundChoice().color
    val text = pick.toRgb()
    if (!Readability.passes(text, background, ColorRole.Text)) {
        return ColorWrite(this, ColorRefusal(ColorRole.Text, Readability.nearestPassing(text, background, ColorRole.Text)))
    }
    // A preset accent follows the text's mode, so the new mode's colour must read too.
    val settled = Readability.settle(background, text, accent.toAccentChoice())
    return ColorWrite(
        appearance = copy(textColor = pick, accent = if (settled.accentAdjusted) settled.accent.toSetting() else accent),
        accentAdjusted = settled.accentAdjusted,
    )
}

/** [pick] as the accent, or a refusal when it is below 3:1 on the background (with the text mode drawn now). */
internal fun AppearanceSettings.writeAccent(pick: ColorSetting): ColorWrite {
    // By system is never refused: each system's colour is settled on the background when drawn, and the fallback when the background changes.
    if (pick is ColorSetting.BySystem) return ColorWrite(copy(accent = pick))
    val theme = toColorsAndLook(animationsOn = true)
    val resolved = pick.toAccentChoice().resolve(theme.textMode)
    if (!Readability.passes(resolved, theme.backgroundColor, ColorRole.Accent)) {
        return ColorWrite(this, ColorRefusal(ColorRole.Accent, Readability.nearestPassing(resolved, theme.backgroundColor, ColorRole.Accent)))
    }
    return ColorWrite(copy(accent = pick))
}

/** [pick] as the background, or a refusal when neither Light nor Dark text can read on it (middle lightness). */
internal fun AppearanceSettings.checkBackground(pick: ColorSetting): ColorWrite {
    val color = pick.toBackgroundChoice().color
    if (Readability.backgroundAllowsText(color)) return ColorWrite(this)
    return ColorWrite(this, ColorRefusal(null, Readability.nearestUsableBackground(color)))
}
