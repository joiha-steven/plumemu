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
 * "Box art": the same three choices as Settings › Accounts › Box art, one above the other (two in a build
 * without ScreenScraper), each with its one line. "Box art and game info" without a usable account shows
 * the sign-in form (Sign in and Skip; Skip goes back to the choices). Taps reach the model through the
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
        val source = sourceOf(control) ?: return@forEach
        SetupChoice(
            label = stringResource(boxArtChoiceName(source)),
            sub = stringResource(boxArtChoiceLine(source)),
            subMaxLines = 4,
            look =
                when {
                    state.focus == control -> ChoiceLook.Focused
                    state.boxArtSource == source -> ChoiceLook.Current
                    else -> ChoiceLook.Plain
                },
            modifier = Modifier.setupControl(state, control, onAction),
        )
    }
    if (state.message != null) StepNote(state.message, R.string.setup_folder_note)
}

private fun sourceOf(control: SetupControl): BoxArtSourceSetting? =
    when (control) {
        SetupControl.UseBoxArt -> BoxArtSourceSetting.ScreenScraper
        SetupControl.BoxArtLibretro -> BoxArtSourceSetting.LibretroOnly
        SetupControl.BoxArtOwnCovers -> BoxArtSourceSetting.OwnCovers
        else -> null
    }

// The words of Settings › Accounts › Box art: one source of truth for what each choice says.
private fun boxArtChoiceName(source: BoxArtSourceSetting): Int =
    when (source) {
        BoxArtSourceSetting.ScreenScraper -> R.string.settings_box_art_source_screenscraper
        BoxArtSourceSetting.LibretroOnly -> R.string.settings_box_art_source_libretro
        BoxArtSourceSetting.OwnCovers -> R.string.settings_box_art_source_own
    }

private fun boxArtChoiceLine(source: BoxArtSourceSetting): Int =
    when (source) {
        BoxArtSourceSetting.ScreenScraper -> R.string.settings_box_art_source_screenscraper_line
        BoxArtSourceSetting.LibretroOnly -> R.string.settings_box_art_source_libretro_line
        BoxArtSourceSetting.OwnCovers -> R.string.settings_box_art_source_own_line
    }
