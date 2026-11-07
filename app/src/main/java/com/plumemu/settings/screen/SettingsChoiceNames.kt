package com.plumemu.settings.screen

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.components.focusPill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.secondscreen.isSecondScreenOn
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.language.AppLanguages
import com.plumemu.language.languageName
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.game.THUMBNAIL_SLOT
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.colorSet
import com.plumemu.settings.toThemeSettings
import androidx.compose.ui.res.pluralStringResource
import com.plumemu.setup.SetupSwitch

// The names of the values a Settings row can show (strings by option and choice).

/** The name of a ‹ › option's value [index]. */
internal fun choiceName(
    option: SettingsOption,
    index: Int,
): Int =
    when (option) {
        // The sets in order, then Custom (SettingsOption.choiceIndex counts the same way).
        SettingsOption.Colors -> ColorSet.entries.getOrNull(index)?.let(::colorSetName) ?: R.string.settings_color_custom
        SettingsOption.Font -> fontName(FontSetting.entries[index])
        SettingsOption.TextSize ->
            when (TextSizeSetting.entries[index]) {
                TextSizeSetting.Small -> R.string.settings_size_small
                TextSizeSetting.Medium -> R.string.settings_size_medium
                TextSizeSetting.Large -> R.string.settings_size_large
                TextSizeSetting.Larger -> R.string.settings_size_larger
                TextSizeSetting.Huge -> R.string.settings_size_huge
                TextSizeSetting.Largest -> R.string.settings_size_largest
            }
        SettingsOption.TextWeight ->
            when (TextWeightSetting.entries[index]) {
                TextWeightSetting.Thin -> R.string.settings_weight_thin
                TextWeightSetting.Medium -> R.string.settings_weight_medium
                TextWeightSetting.Bold -> R.string.settings_weight_bold
            }
        SettingsOption.LeftEdge -> leftEdgeNameRes(index)
        SettingsOption.GridSize ->
            when (GridSizeSetting.entries[index]) {
                GridSizeSetting.Small -> R.string.settings_size_small
                GridSizeSetting.Medium -> R.string.settings_size_medium
                GridSizeSetting.Large -> R.string.settings_size_large
            }
        SettingsOption.SortGames ->
            when (SortGamesSetting.entries[index]) {
                SortGamesSetting.Name -> R.string.settings_sort_by_name
                SortGamesSetting.LastPlayed -> R.string.settings_sort_by_last_played
                SortGamesSetting.MostPlayed -> R.string.settings_sort_by_most_played
                SortGamesSetting.RecentlyAdded -> R.string.settings_sort_by_recently_added
                SortGamesSetting.Year -> R.string.settings_sort_by_year
                SortGamesSetting.Rating -> R.string.settings_sort_by_rating
            }
        SettingsOption.SystemOrder -> systemOrderName(index)
        SettingsOption.SwitchSystemWith -> if (index == SwitchSystemWithSetting.Triggers.ordinal) R.string.settings_switch_system_triggers else R.string.settings_switch_system_bumpers
        SettingsOption.Keyboard -> if (index == KeyboardSetting.Plumemu.ordinal) R.string.settings_keyboard_plumemu else R.string.settings_keyboard_android
        SettingsOption.Sounds ->
            when (SoundLevel.entries[index]) {
                SoundLevel.Off -> R.string.settings_sounds_off
                SoundLevel.Quiet -> R.string.settings_sounds_quiet
                SoundLevel.Loud -> R.string.settings_sounds_loud
            }
        SettingsOption.SecondScreenInGame ->
            when (InGameSetting.entries[index]) {
                InGameSetting.Clock -> R.string.settings_second_screen_clock
                InGameSetting.Black -> R.string.settings_second_screen_black
            }
        SettingsOption.BoxArtRegion -> when (BoxArtRegionSetting.entries[index]) {
            BoxArtRegionSetting.Usa -> R.string.settings_region_usa
            BoxArtRegionSetting.Europe -> R.string.settings_region_europe
            BoxArtRegionSetting.Japan -> R.string.settings_region_japan
            BoxArtRegionSetting.World -> R.string.settings_region_world
        }
        // A row that is not a ‹ › choice: show "?" and log it, so a missing name is seen without a crash.
        else -> {
            Log.w(CHOICE_TAG, "$option has no choice names")
            R.string.settings_choice_missing
        }
    }

private const val CHOICE_TAG = "SettingsChoice"

/**
 * The name the Text color row and panel show: Light and Dark by name, a tinted preset offered on [background]
 * by its own name (Cream, Navy…), any other colour "Custom" (also when no background is given).
 */
internal fun textColorName(
    text: TextColorSetting,
    background: Rgb? = null,
): Int =
    when (text) {
        TextColorSetting.Light -> R.string.settings_text_light
        TextColorSetting.Dark -> R.string.settings_text_dark
        is TextColorSetting.Custom -> background?.let { ColorPages.textPresetNameOn(it, text) } ?: R.string.settings_color_custom
    }

internal fun fontName(font: FontSetting): Int =
    when (font) {
        FontSetting.Jost -> R.string.settings_font_jost
        FontSetting.Sans -> R.string.settings_font_sans
        FontSetting.Serif -> R.string.settings_font_serif
        FontSetting.Mono -> R.string.settings_font_mono
    }

/**
 * The name the Background or Accent row shows for [setting]. An own colour is "Custom", unless it is
 * the colour of a ready-made set (the current one first) or of a vivid preset: then that name ("Pocket"), not a code.
 */
@Composable
internal fun colorName(
    setting: ColorSetting,
    isBackground: Boolean,
    set: ColorSet?,
): String =
    when (setting) {
        is ColorSetting.Custom -> {
            // Each row is named by its own colour: the set or vivid preset whose colour it is, even when another row has moved away from that set.
            val rgb = setting.rgb and RGB_MASK
            fun ColorSet.previous() = if (isBackground) previousBackgrounds else previousAccents
            val ofPrevious = set?.takeIf { Rgb(rgb) in it.previous() }
            val named = ofPrevious ?: set?.takeIf { (if (isBackground) it.backgroundColor else it.accentColor).value == rgb } ?: ColorSet.entries.firstOrNull { (if (isBackground) it.backgroundColor else it.accentColor).value == rgb }
                ?: ColorSet.entries.firstOrNull { Rgb(rgb) in it.previous() }
            val vivid = if (isBackground) ColorPages.vividBackgrounds.firstOrNull { it.color.value == rgb } else null
            when {
                vivid != null -> stringResource(vivid.name)
                named != null -> stringResource(colorSetName(named))
                else -> stringResource(R.string.settings_color_custom)
            }
        }
        is ColorSetting.BySystem -> stringResource(R.string.settings_accent_by_system)
        is ColorSetting.Preset ->
            if (isBackground) {
                stringResource(backgroundName(BackgroundPreset.entries.firstOrNull { it.name == setting.name } ?: BackgroundPreset.Black))
            } else {
                stringResource(accentName(AccentPreset.entries.firstOrNull { it.name == setting.name } ?: AccentPreset.Default))
            }
    }

/** Settings › Appearance › Colors: a ready-made set's name (never a console's). */
internal fun colorSetName(set: ColorSet): Int =
    when (set) {
        ColorSet.Plumemu -> R.string.settings_colors_plumemu
        ColorSet.Paper -> R.string.settings_colors_paper
        ColorSet.Pocket -> R.string.settings_colors_pocket
        ColorSet.DotMatrix -> R.string.settings_colors_dot_matrix
        ColorSet.Super -> R.string.settings_colors_super
        ColorSet.Indigo -> R.string.settings_colors_indigo
        ColorSet.Charcoal -> R.string.settings_colors_charcoal
        ColorSet.Ivory -> R.string.settings_colors_ivory
        ColorSet.Banana -> R.string.settings_colors_banana
        ColorSet.Cream -> R.string.settings_colors_cream
    }

private const val RGB_MASK = 0xFFFFFF

internal fun backgroundName(preset: BackgroundPreset): Int =
    when (preset) {
        BackgroundPreset.Graphite -> R.string.settings_bg_graphite
        BackgroundPreset.Black -> R.string.settings_bg_black
        BackgroundPreset.Midnight -> R.string.settings_bg_midnight
        BackgroundPreset.Plum -> R.string.settings_bg_plum
        BackgroundPreset.Forest -> R.string.settings_bg_forest
        BackgroundPreset.Paper -> R.string.settings_bg_paper
        BackgroundPreset.Sand -> R.string.settings_bg_sand
        BackgroundPreset.Mist -> R.string.settings_bg_mist
    }

internal fun accentName(preset: AccentPreset): Int =
    when (preset) {
        AccentPreset.Red -> R.string.settings_accent_red
        AccentPreset.Amber -> R.string.settings_accent_amber
        AccentPreset.Lilac -> R.string.settings_accent_lilac
        AccentPreset.Blue -> R.string.settings_accent_blue
        AccentPreset.Green -> R.string.settings_accent_green
        AccentPreset.Mono -> R.string.settings_accent_mono
    }

/** The name of Left edge's value [index] (`LeftEdgeSetting.entries` order). */
internal fun leftEdgeNameRes(index: Int): Int =
    when (LeftEdgeSetting.entries[index]) {
        LeftEdgeSetting.Counts -> R.string.settings_left_edge_counts
        LeftEdgeSetting.Codes -> R.string.settings_left_edge_codes
        LeftEdgeSetting.Letters -> R.string.settings_left_edge_letters
    }
