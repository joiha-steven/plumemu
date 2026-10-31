package com.plumemu.settings

import android.util.Log
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.ButtonLayout
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.TextWeight
import com.plumemu.keyboard.KeyboardChoice

/*
 * Stored settings (core-data, plain values) to what core-ui and core-input take. Presets are
 * stored by core-ui's enum names; a name this version does not know falls back to the default
 * preset, so a newer version's preset never breaks an older install.
 */

/** Appearance plus Advanced › Animations: everything [PlumThemeSettings] holds. */
fun PlumSettings.toThemeSettings(): PlumThemeSettings = appearance.toThemeSettings(animationsOn = advanced.animations)

fun AppearanceSettings.toThemeSettings(animationsOn: Boolean): PlumThemeSettings = toColorsAndLook(animationsOn).copy(buttonSet = effectiveButtonSet())

/** Everything but the glyph colours, which need the current set ([effectiveButtonSet]) and so this. */
internal fun AppearanceSettings.toColorsAndLook(animationsOn: Boolean): PlumThemeSettings =
    // A set's accent from an earlier version still reads and draws as that set (ColorSet.previousAccents); stored data is not touched.
    ColorSet.settled(rawColorsAndLook(animationsOn))

private fun AppearanceSettings.rawColorsAndLook(animationsOn: Boolean): PlumThemeSettings =
    PlumThemeSettings(
        background = background.toBackgroundChoice(),
        text = textColor.toRgb(),
        accent = accent.toAccentChoice(),
        font = when (font) {
            FontSetting.Sans -> PlumFont.Sans
            FontSetting.Serif -> PlumFont.Serif
            FontSetting.Mono -> PlumFont.Mono
        },
        // Same names on both sides; null stays null, which the theme reads as TextSize.Default (Large).
        textSize = textSize?.let { picked -> TextSize.valueOf(picked.name) },
        // Same names on both sides; not picked is the default (Medium).
        textWeight = textWeight?.let { picked -> TextWeight.valueOf(picked.name) } ?: TextWeight.Default,
        animationsOn = animationsOn,
        gridSize = when (gridSize) {
            GridSizeSetting.Small -> GridSize.Small
            GridSizeSetting.Medium -> GridSize.Medium
            GridSizeSetting.Large -> GridSize.Large
        },
    )

/** The grid size L1 / R1 picked on the main screen, as it is stored. */
fun GridSize.toSetting(): GridSizeSetting =
    when (this) {
        GridSize.Small -> GridSizeSetting.Small
        GridSize.Medium -> GridSizeSetting.Medium
        GridSize.Large -> GridSizeSetting.Large
    }

/**
 * The confirm button onto core-input: always the Xbox layout, which takes Android's key names as
 * the printed letters (BUTTON_A = A, BUTTON_X = X ...), because the AYN Thor sends keys by letter.
 * Right (the default; the stored name is kept) has no swap:
 * BUTTON_A = Enter, BUTTON_B = Back. Bottom (the "Confirm with B" option) swaps them for a person who
 * prefers B. Only A and B swap; X (search) and Y (favorite) never move.
 */
fun ControlsSettings.toInputSettings(): InputSettings =
    InputSettings(
        buttonLayout = ButtonLayout.Xbox,
        swapAB = confirmButton == ConfirmButtonSetting.Bottom,
        holdToScrollFaster = holdToScrollFaster,
    )

/** Settings › Controls › Keyboard onto the keyboard Search and the game panel's fields type with. */
internal fun ControlsSettings.toKeyboardChoice(): KeyboardChoice =
    when (keyboard) {
        KeyboardSetting.Plumemu -> KeyboardChoice.Plumemu
        KeyboardSetting.Android -> KeyboardChoice.Android
    }

private const val RGB_MASK = 0xFFFFFF

/** The text colour as stored: Light and Dark are the two presets' colours, a custom one is used exactly as picked. */
internal fun TextColorSetting.toRgb(): Rgb =
    when (this) {
        TextColorSetting.Light -> TextMode.Light.color
        TextColorSetting.Dark -> TextMode.Dark.color
        is TextColorSetting.Custom -> Rgb(rgb and RGB_MASK)
    }

/** A text colour as it is stored: one of the two presets when it is exactly that colour, else a custom one. */
internal fun Rgb.toTextSetting(): TextColorSetting =
    when (this) {
        TextMode.Light.color -> TextColorSetting.Light
        TextMode.Dark.color -> TextColorSetting.Dark
        else -> TextColorSetting.Custom(value)
    }

internal fun ColorSetting.toBackgroundChoice(): BackgroundChoice =
    when (this) {
        is ColorSetting.Custom -> BackgroundChoice.Custom(Rgb(rgb and RGB_MASK))
        is ColorSetting.Preset ->
            BackgroundChoice.Preset(BackgroundPreset.entries.firstOrNull { it.name == name } ?: BackgroundPreset.Black)
        // Only the accent has this choice; a stored background never holds it (the codec reads it as unknown).
        // Logged by class, no value: if it ever shows, a write path let it through.
        is ColorSetting.BySystem -> {
            Log.w(TAG, "Background holds ${ColorSetting.BySystem::class.simpleName}; drawn as Black")
            BackgroundChoice.Preset(BackgroundPreset.Black)
        }
    }

internal fun ColorSetting.toAccentChoice(): AccentChoice =
    when (this) {
        is ColorSetting.Custom -> AccentChoice.Custom(Rgb(rgb and RGB_MASK))
        is ColorSetting.Preset -> AccentChoice.Preset(AccentPreset.entries.firstOrNull { it.name == name } ?: AccentPreset.Default)
        is ColorSetting.BySystem -> AccentChoice.BySystem(fallback.toAccentChoice().let { if (it is AccentChoice.BySystem) it.fallback else it })
    }

/**
 * SELECT on the main screen: the stored settings with [systemId] (a system's id, or
 * Recent's, Favorites' or Apps' entry id, `HomeContent.RECENT_ID` …) shown as a grid ([isGrid]) or a list. Every other system's choice is kept as stored, ids of systems no longer in
 * the library included (a folder that comes back keeps its grid).
 */
internal fun PlumSettings.withGridView(
    systemId: String,
    isGrid: Boolean,
): PlumSettings {
    val ids = appearance.gridSystems
    return copy(appearance = appearance.copy(gridSystems = if (isGrid) ids + systemId else ids - systemId))
}

private const val TAG = "PlumSettingsMapping"
