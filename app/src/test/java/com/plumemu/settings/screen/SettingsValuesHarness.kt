package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** The kinds of value at the right of a Settings row (decision 0011 point 4), one row each, tagged by [ValueRow]. */
internal enum class ValueRow(val option: SettingsOption) {
    CycleFocused(SettingsOption.LeftEdge),
    CycleResting(SettingsOption.TextSize),
    SubPage(SettingsOption.RomFolders),
    SubPageBare(SettingsOption.Licenses),
    SignedIn(SettingsOption.ScraperAccount),
    SignedInNoName(SettingsOption.RetroAchievementsAccount),
    SignedInLongName(SettingsOption.Backup),
    CycleLong(SettingsOption.Keyboard),
    Toggle(SettingsOption.Animations),
    ;

    val valueTag: String get() = "values_${name}_value"
    val rowTag: String get() = "values_${name}_row"
}

internal const val LONG_NAME = "a_user_name_that_goes_on_and_on_and_will_never_fit_one_line"

/** [focusedLook] is the look of the two cycler rows that the cursor may be on; the rest rest. */
@Composable
internal fun ValueRows(
    focusedLook: OptionLook = OptionLook.Focused,
    cycleText: String = "Serif",
) {
    val colors = PlumTheme.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.padding(PlumDimens.gameRowPadding).width(PlumTheme.layout.settingsOptionsWidth), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ValueRow.entries.forEach { row ->
                val value: OptionValue =
                    when (row) {
                        ValueRow.CycleFocused -> OptionValue.Choice(cycleText)
                        ValueRow.CycleResting -> OptionValue.Choice("Large")
                        ValueRow.SubPage -> OptionValue.Opens("2 folders")
                        ValueRow.SubPageBare -> OptionValue.Opens("")
                        ValueRow.SignedIn -> signedInValue("plumtester")
                        ValueRow.SignedInNoName -> signedInValue("")
                        ValueRow.SignedInLongName -> signedInValue(LONG_NAME)
                        ValueRow.CycleLong -> OptionValue.Choice("Plumemu keyboard with a very long name indeed")
                        ValueRow.Toggle -> OptionValue.Switch(true)
                    }
                val look = if (row == ValueRow.CycleFocused || row == ValueRow.CycleLong) focusedLook else OptionLook.Normal
                OptionRow(row.option, value, look, tag = row.rowTag, valueTag = row.valueTag)
            }
        }
    }
}
