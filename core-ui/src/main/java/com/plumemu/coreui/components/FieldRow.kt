package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * A one-line text field drawn as an [OptionRow] (40 high, Settings option type): focused, it has
 * the focus pill and the caret; otherwise it is a plain row, so a list under it
 * keeps the one focus language (never two filled rows). [placeholder] shows while [value] is
 * empty; [visualTransformation] masks a password; [label] names the field before its text;
 * [keyboardHint], when set, keeps the Android keyboard beside the screen and names the field in it
 * (see [InlineKeyboard]). The caller owns the keyboard and focus through
 * [fieldModifier] (a `FocusRequester`, `focusProperties`) and passes taps in through [modifier],
 * as for every core-ui component.
 */
@Composable
fun FieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    look: OptionRowLook,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** A name that stays before the text ("Username"), [labelWidth] wide so a column of fields lines up; none by default. */
    label: String? = null,
    labelWidth: Dp = 0.dp,
    /** What the keyboard calls the field ("ScreenScraper username"); set, the keyboard never takes the whole screen. */
    keyboardHint: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    FieldRowFrame(look, modifier) { style ->
        if (label != null) {
            BasicText(label, style = type.settingsOption.copy(color = colors.muted), maxLines = 1, modifier = Modifier.width(labelWidth))
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
        }
        // Kept as a TextFieldValue so a pre-filled name opens with the caret at its end; a value
        // changed from outside (a reset) also puts the caret at the end.
        var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
        InlineKeyboard(keyboardHint) {
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != value) onValueChange(it.text)
                },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                modifier = fieldModifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            BasicText(placeholder, style = style.copy(color = colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/**
 * The frame every field row shares: [rowHeight][com.plumemu.coreui.theme.PlumLayout.rowHeight] high, the
 * focus pill ([focusPill]) when [look] is focused,
 * the row padding. [content] gets the text style of the field's look (Settings option type, `strong`
 * focused, `text2` otherwise). [FieldRow] draws a text field in it; a field typed with Plumemu's own
 * keyboard, which has no text field, draws its own line ([FieldRow] look).
 */
@Composable
fun FieldRowFrame(
    look: OptionRowLook,
    modifier: Modifier = Modifier,
    /** False while Plumemu's own keyboard is up under the row: the row keeps its fill but the confirm button belongs to the key, so the row does not dip. */
    answersButton: Boolean = true,
    content: @Composable RowScope.(TextStyle) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == OptionRowLook.Focused
    val style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = if (focused) colors.strong else colors.text2)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlumTheme.layout.rowHeight)
                .focusPill(focused, answersButton)
                .padding(horizontal = PlumDimens.gameRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(style)
    }
}

/**
 * The text field in [content] asks the Android keyboard for no full-screen editor and gives it
 * [hint] as the field's name; with no [hint] nothing changes. Landscape keyboards otherwise open
 * their extract editor over the whole screen: one blank box, no label, the form hidden (seen on
 * the AYN Thor). Compose already sets `IME_FLAG_NO_FULLSCREEN`; `IME_FLAG_NO_EXTRACT_UI`
 * and `hintText` are added to the `EditorInfo` Compose fills, the one the keyboard reads, for a
 * keyboard that goes full screen anyway.
 */
@Composable
fun InlineKeyboard(
    hint: String?,
    content: @Composable () -> Unit,
) {
    if (hint == null) content() else InterceptPlatformTextInput(remember(hint) { InlineKeyboardInterceptor(hint) }, content)
}

private class InlineKeyboardInterceptor(
    private val hint: String,
) : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing =
        nextHandler.startInputMethod(
            object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    // Compose writes its own options first; ours go on top of them.
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    outAttributes.hintText = hint
                    return connection
                }
            },
        )
}
