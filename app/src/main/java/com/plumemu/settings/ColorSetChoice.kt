package com.plumemu.settings

import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.TextMode
import kotlinx.coroutines.CancellationException

/*
 * Settings › Appearance › Colors on the stored settings. The current set is derived from
 * the three colour settings, never stored; null stands for "Custom" everywhere here.
 */

/** The ready-made set these colours draw, or null (Custom) when they match none. */
internal fun AppearanceSettings.colorSet(): ColorSet? = ColorSet.matching(toColorsAndLook(animationsOn = true))

/**
 * The set whose A/B/X/Y glyph colours are in use: the stored pick; a file from before
 * the setting existed (null) reads as the set its colours match exactly, so nobody loses the
 * buttons on update. [settleButtonSet] writes that once at startup.
 */
internal fun AppearanceSettings.effectiveButtonSet(): ColorSet? =
    if (buttonSet == null) {
        colorSet()?.takeIf { it.buttons != null }
    } else {
        ColorSet.entries.firstOrNull { it.name == buttonSet && it.buttons != null }
    }

/** The stored value for "these buttons": the set's name, or None for a set without any. */
private fun ColorSet?.buttonSetName(): String = this?.takeIf { it.buttons != null }?.name ?: AppearanceSettings.BUTTONS_NONE

/** These settings with the undecided button set settled from the colours now (once, at startup). */
internal fun AppearanceSettings.settleButtonSet(): AppearanceSettings =
    if (buttonSet == null) copy(buttonSet = effectiveButtonSet().buttonSetName()) else this

/**
 * Writes the settled button set of a file from before the setting, once at startup,
 * before the person can change a colour by hand and lose the buttons. A failure is logged; until
 * it works the buttons read from the colours ([effectiveButtonSet]).
 */
internal suspend fun SettingsRepository.settleButtonSet(log: (String) -> Unit) = settleButtonSet(::update, log)

/** [settleButtonSet] over any writer, so a failing write can be tried. */
internal suspend fun settleButtonSet(
    update: suspend ((PlumSettings) -> PlumSettings) -> Unit,
    log: (String) -> Unit,
) {
    try {
        update { it.copy(appearance = it.appearance.settleButtonSet()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Settling the button colours failed (${e.javaClass.simpleName})")
    }
}

/** The set whose buttons Custom brings: the current ones while the colours are custom, else the ones kept with the own colours. */
internal fun AppearanceSettings.customButtonSet(): ColorSet? =
    if (colorSet() == null) {
        effectiveButtonSet()
    } else {
        ownColors?.buttonSet?.let { name -> ColorSet.entries.firstOrNull { it.name == name && it.buttons != null } }
    }

/**
 * The Colors row's values in order: every set, then Custom (null) while there is a custom look to
 * be on or go back to: the current colours (they match no set) or kept own colours that match none.
 */
internal fun AppearanceSettings.colorsCycle(): List<ColorSet?> {
    val hasCustom = customColors() != null
    return buildList {
        addAll(ColorSet.entries)
        if (hasCustom) add(null)
    }
}

/**
 * These settings with [entry] picked on the Colors row. A set moves Background, Text color and
 * Accent color to its values; when the current colours match no set they are kept as the person's
 * own colours first, so Custom (null) brings them back exactly. Picking what is already current
 * changes nothing; Custom without own colours kept changes nothing either.
 */
internal fun AppearanceSettings.withColors(entry: ColorSet?): AppearanceSettings {
    val current = colorSet()
    if (entry == current) {
        // Same three colours, maybe other buttons (the person typed a set's colours by hand): the pick still stores its buttons.
        return if (entry == null || effectiveButtonSet() == entry.takeIf { it.buttons != null }) this else copy(buttonSet = entry.buttonSetName())
    }
    // The own colours keep the buttons in use with them, so Custom brings back the same look.
    val own = if (current == null) OwnColors(background, textColor, accent, effectiveButtonSet().buttonSetName()) else ownColors
    if (entry == null) return own?.let { withColorsOf(it).copy(ownColors = it, buttonSet = it.buttonSet ?: AppearanceSettings.BUTTONS_NONE) } ?: this
    return copy(
        buttonSet = entry.buttonSetName(),
        background = entry.background.toSetting(),
        textColor = entry.textMode.toSetting(),
        accent = entry.accent.toSetting(),
        ownColors = own,
    )
}

/**
 * The colours Custom stands for: the current ones when they match no set, else the kept own colours
 * when they match none; null when the Colors row has no Custom ([colorsCycle]).
 */
internal fun AppearanceSettings.customColors(): AppearanceSettings? {
    if (colorSet() == null) return this
    val kept = ownColors ?: return null
    return withColorsOf(kept).takeIf { it.colorSet() == null }
}

private fun AppearanceSettings.withColorsOf(own: OwnColors): AppearanceSettings =
    copy(background = own.background, textColor = own.textColor, accent = own.accent)

/** A preset by its core-ui name (as the colour panels store it), anything else as its `#RRGGBB`. */
internal fun BackgroundChoice.toSetting(): ColorSetting =
    when (this) {
        is BackgroundChoice.Preset -> ColorSetting.Preset(preset.name)
        is BackgroundChoice.Custom -> ColorSetting.Custom(color.value)
    }

internal fun AccentChoice.toSetting(): ColorSetting =
    when (this) {
        is AccentChoice.Preset -> ColorSetting.Preset(preset.name)
        is AccentChoice.Custom -> ColorSetting.Custom(color.value)
        is AccentChoice.BySystem -> ColorSetting.BySystem(fallback.toSetting())
    }

internal fun TextMode.toSetting(): TextColorSetting =
    when (this) {
        TextMode.Light -> TextColorSetting.Light
        TextMode.Dark -> TextColorSetting.Dark
    }
