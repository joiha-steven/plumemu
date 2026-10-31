package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.plumemu.R
import com.plumemu.coreui.components.InlineKeyboard
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.focusFieldAt
import com.plumemu.game.isTrayUp
import com.plumemu.home.search.hideSystemBars
import com.plumemu.keyboard.CaretLine
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ColorRefusal
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.setup.focusRing

/**
 * The "+" page's Color code row: the swatch and hex of the colour shown, the field that types a code
 * (Android's keyboard or Plumemu's tray), its error line and the "Use this colour" row.
 */
@Composable
internal fun HexRow(
    color: Color,
    hex: String,
    isFocused: Boolean,
    model: SettingsModel,
    touch: SettingsTouch,
    index: Int,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val shape = RoundedCornerShape(PlumDimens.cornerRow)
    val ownKeyboard = model.hexTyping?.takeIf { it.keyboardKind == KeyboardChoice.Plumemu }
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTags.HEX_ROW)
                    .bringIntoViewWhen(isFocused && !model.hexOnUse, ring = PlumDimens.focusRingOutset)
                    // Draws the row's fill; outside the ring, so the ring dips with the row.
                    .pressFeel(isFocused && !model.hexOnUse, colors.sel2, shape, answersButton = ownKeyboard?.isTrayUp != true)
                    .focusRing(isFocused && !model.hexOnUse, PlumDimens.cornerRow, colors.accent, colors.focusGlow)
                    .heightIn(min = PlumTheme.layout.settingsHexRowHeight)
                    .swatchTap(index, touch)
                    .padding(horizontal = PlumDimens.settingsPanelRowPadding),
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(PlumDimens.settingsValueDot).background(color, CircleShape).border(PlumDimens.switchOffRing, colors.chip, CircleShape))
            // May take two lines where the code leaves it little room (Largest in Plum Serif).
            BasicText(stringResource(R.string.settings_hex_label), style = type.meta.copy(color = colors.muted), maxLines = 2, modifier = Modifier.weight(1f))
            when {
                !model.isEditingHex || !isFocused -> BasicText(hex, style = type.meta.copy(color = colors.text), maxLines = 1)
                ownKeyboard != null -> HexOwnKeyboardField(hex, ownKeyboard, model.hexText)
                else -> HexField(hex, model, touch)
            }
        }
        // While Plumemu's tray is up its action key uses the code: "Use this colour" shows with the tray down.
        val showsUse = model.isEditingHex && isFocused && ownKeyboard?.isTrayUp != true
        val showsError = model.hexError && isFocused
        if (ownKeyboard == null) {
            if (showsUse) UseRow(model.hexOnUse, hex = model.hexText, model.hexRefusal, touch)
            if (showsError) HexError()
        } else if (showsUse || showsError) {
            // Above the tray only the height left: what is under the field scrolls, the field stays whole.
            Column(Modifier.verticalScroll(rememberScrollState()).testTag(SettingsTags.HEX_UNDER), verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
                if (showsUse) UseRow(model.hexOnUse, hex = model.hexText, model.hexRefusal, touch)
                if (showsError) HexError()
            }
        }
    }
}

/** The wrong-code line under the hex field. */
@Composable
private fun HexError() {
    BasicText(
        stringResource(R.string.settings_hex_error),
        style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
        modifier = Modifier.onRowTextLine().testTag(SettingsTags.HEX_ERROR),
    )
}

/**
 * The hex code typed with Plumemu's keyboard: a drawn line with a static caret
 * ([CaretLine]), no text field, so Android's keyboard can never come up; the current code shows in
 * its place while it is empty. A tap puts the caret at the tapped character and brings the tray up.
 * The tray itself is drawn by the screen ([SettingsKeyboardTray]).
 */
@Composable
private fun HexOwnKeyboardField(
    current: String,
    typing: HexTyping,
    text: String,
) {
    val onTapAt = remember(typing) { { offset: Int -> typing.focusFieldAt(offset) } }
    val name = stringResource(R.string.settings_hex_hint)
    CaretLine(
        text = text,
        caret = { typing.fieldKeys.caret },
        showsCaret = { typing.inField },
        placeholder = current,
        style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text),
        onTapAt = onTapAt,
        // At least the Android field's width; wider when the current code needs it at the larger text sizes.
        modifier = Modifier.widthIn(min = PlumDimens.settingsPresetSwatch * 2).semantics { contentDescription = name }.testTag(SettingsTags.HEX_FIELD),
        textTag = SettingsTags.HEX_TEXT,
        placeholderTag = SettingsTags.HEX_PLACEHOLDER,
    )
}

/**
 * "Use this colour" under the hex field: the one explicit way to apply the typed code without the
 * keyboard's Done (a hardware keyboard or a gamepad). Same width and corners as the hex row.
 */
@Composable
private fun UseRow(
    isFocused: Boolean,
    hex: String,
    refusal: ColorRefusal?,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val shape = RoundedCornerShape(PlumDimens.cornerRow)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.HEX_USE)
                // Always in view while it shows: with the sliders above, the page is taller than the panel and a tap on it needs it on screen.
                .bringIntoViewWhen(true, ring = PlumDimens.focusRingOutset)
                .pressFeel(isFocused, colors.sel2, shape)
                .focusRing(isFocused, PlumDimens.cornerRow, colors.accent, colors.focusGlow)
                .height(PlumTheme.layout.settingsHexRowHeight)
                .tapTarget { touch.onHexEntered(hex) }
                .padding(horizontal = PlumDimens.settingsPanelRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // After a refusal this row takes the nearest colour that reads instead of the typed one.
        val label = if (refusal != null) stringResource(R.string.settings_color_use_nearest, refusal.nearest.toHex()) else stringResource(R.string.settings_hex_use)
        BasicText(label, style = PlumTheme.type.meta.copy(color = colors.text), maxLines = 1)
    }
}

/** The hex code with the Android keyboard open; Done uses it, Back leaves it. */
@Composable
private fun HexField(
    current: String,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val up = model.isTypingHex
    // Up while the cursor is on the field (again on each ask: the system's Back may have closed it);
    // down, with the system bars put back, while it is on "Use this colour".
    LaunchedEffect(up, model.hexKeyboardAsks) {
        if (up) {
            requester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
            hideSystemBars(view)
        }
    }
    // Done, A or B ends editing and this field leaves the composition: the keyboard goes with it.
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    InlineKeyboard(stringResource(R.string.settings_hex_hint)) {
    BasicTextField(
        value = model.hexText,
        onValueChange = touch.onHexText,
        singleLine = true,
        textStyle = PlumTheme.type.meta.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { touch.onHexEntered(model.hexText) }),
        modifier = Modifier.width(PlumDimens.settingsPresetSwatch * 2).focusProperties { canFocus = up }.focusRequester(requester).testTag(SettingsTags.HEX_FIELD),
        decorationBox = { field ->
            Box {
                // The current code as a hint until the first letter is typed.
                if (model.hexText.isEmpty()) BasicText(current, style = PlumTheme.type.meta.copy(color = colors.muted), maxLines = 1)
                field()
            }
        },
    )
    }
}
