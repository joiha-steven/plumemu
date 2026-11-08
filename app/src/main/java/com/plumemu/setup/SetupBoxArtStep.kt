package com.plumemu.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.SignInBody
import com.plumemu.coreinput.Action
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreinput.Column as StripColumn

/**
 * "Box art": page 1 asks "Download box art?" (No, Yes), page 2 (a build with ScreenScraper, after Yes) asks
 * for the source (Default source, ScreenScraper account), each choice with its one line. "ScreenScraper
 * account" without a usable account shows the sign-in form (Sign in and Not now; Not now goes on, B goes
 * back to page 2). Taps reach the model through the
 * router like setup's other controls (`TapItem` with the control's or the form's item place).
 */
@Composable
internal fun ColumnScope.BoxArtStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    val form = state.boxArt
    if (form == null) {
        BoxArtChoices(state, onAction)
        return
    }
    SignInBody(
        form = form,
        leaveLabel = R.string.setup_box_art_skip,
        onTap = { index -> onAction(Action.TapItem(StripColumn.Games, index)) },
        onAction = onAction,
        gap = PlumDimens.setupContentGap,
        modifier = Modifier.testTag(SetupTags.BOX_ART),
    )
}

@Composable
private fun ColumnScope.BoxArtChoices(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    state.controls.forEach { control ->
        val name = choiceName(control) ?: return@forEach
        SetupChoice(
            label = stringResource(name),
            sub = stringResource(choiceLine(control, state.hasScreenScraper)),
            subMaxLines = 4,
            look =
                when {
                    state.focus == control -> ChoiceLook.Focused
                    isInForce(control, state.boxArtSource) -> ChoiceLook.Current
                    else -> ChoiceLook.Plain
                },
            modifier = Modifier.setupControl(state, control, onAction),
        )
    }
    if (state.message != null) StepNote(state.message, R.string.setup_folder_note)
}

/** The source a control stands for, to mark the choice in force; "Yes" stands for every source that downloads. */
private fun isInForce(
    control: SetupControl,
    source: BoxArtSourceSetting?,
): Boolean =
    when (control) {
        SetupControl.BoxArtOwnCovers -> source == BoxArtSourceSetting.OwnCovers
        SetupControl.BoxArtYes -> source == BoxArtSourceSetting.LibretroOnly || source == BoxArtSourceSetting.ScreenScraper
        SetupControl.BoxArtLibretro -> source == BoxArtSourceSetting.LibretroOnly
        SetupControl.UseBoxArt -> source == BoxArtSourceSetting.ScreenScraper
        else -> false
    }

private fun choiceName(control: SetupControl): Int? =
    when (control) {
        SetupControl.BoxArtOwnCovers -> R.string.setup_box_art_no
        SetupControl.BoxArtYes -> R.string.setup_box_art_yes
        SetupControl.BoxArtLibretro -> R.string.setup_box_art_default
        SetupControl.UseBoxArt -> R.string.setup_box_art_account
        else -> null
    }

private fun choiceLine(
    control: SetupControl,
    hasScreenScraper: Boolean,
): Int =
    when (control) {
        SetupControl.BoxArtOwnCovers -> R.string.setup_box_art_no_line
        SetupControl.BoxArtYes -> if (hasScreenScraper) R.string.setup_box_art_yes_line else R.string.setup_box_art_yes_line_libretro
        SetupControl.BoxArtLibretro -> R.string.setup_box_art_default_line
        else -> R.string.setup_box_art_account_line
    }
