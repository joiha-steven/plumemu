package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * A one-line text field row, the board's Fields in a list: an optional [label] column, the board gap ([FieldDimens.gap]),
 * then the field, a sunken well in its collar ([fieldCollar]) that lies wholly inside the row. The row has no pill:
 * the cursor on it lights the field's groove, so focus shows once. [placeholder] shows while [value] is empty;
 * [visualTransformation] masks a password; [keyboardHint], when set, keeps the Android keyboard beside the screen and
 * names the field in it (see [InlineKeyboard]). The caller owns the keyboard and focus through [fieldModifier]
 * (a `FocusRequester`, `focusProperties`) and passes taps in through [modifier], as for every core-ui component.
 * A field typed with Plumemu's own keyboard wears the same frame ([FieldRowFrame]).
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
    /** A name that stays before the field ("Username"), [labelWidth] wide so a column of fields lines up; none by default. */
    label: String? = null,
    labelWidth: Dp = 0.dp,
    /** What the keyboard calls the field ("ScreenScraper username"); set, the keyboard never takes the whole screen. */
    keyboardHint: String? = null,
    /** The field's collar groove in the danger colour; the message under the field is the caller's. */
    error: Boolean = false,
) {
    val colors = PlumTheme.colors
    // Kept as a TextFieldValue so a pre-filled name opens with the caret at its end; a value
    // changed from outside (a reset) also puts the caret at the end.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var hasFocus by remember { mutableStateOf(false) }
    FieldRowFrame(look, modifier, label, labelWidth, error, lit = hasFocus) { style ->
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
                modifier = fieldModifier.fillMaxWidth().onFocusChanged { hasFocus = it.isFocused },
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
 * The frame every field row shares (the board's Fields in a list): the [label] column [labelWidth] wide, the board gap,
 * then the field: a box at least [FieldDimens.wellHeight] (grown with the text size) plus the collar high, with the
 * collar and well drawn inside it ([fieldCollar]), no pill and no focus fill on the row. The groove lights when [look]
 * is focused (the cursor is on the row) or [lit] (the text field has the input focus), [error] draws it in the danger
 * colour. [content] gets the text style of the field's look (Settings option type, `strong` focused, `text2`
 * otherwise) inside the well, centred at the start; a field typed with Plumemu's own keyboard draws its own line in it.
 */
@Composable
fun FieldRowFrame(
    look: OptionRowLook,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelWidth: Dp = 0.dp,
    error: Boolean = false,
    lit: Boolean = false,
    content: @Composable BoxScope.(TextStyle) -> Unit,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == OptionRowLook.Focused
    val style = (if (focused) type.settingsOptionFocused else type.settingsOption).copy(color = if (focused) colors.strong else colors.text2)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            // The label sits on the row-text line, like the text of every list row.
            Spacer(Modifier.width(PlumDimens.gameRowPadding))
            BasicText(label, style = type.settingsOption.copy(color = colors.muted), maxLines = 1, modifier = Modifier.width(labelWidth).testTag(FieldTags.LABEL))
            Spacer(Modifier.width(FieldDimens.gap))
        }
        val box = fieldBoxHeight()
        Box(
            modifier = Modifier.weight(1f).testTag(FieldTags.BOX).heightIn(min = box).fieldCollar(focused = focused || lit, error = error).padding(fieldContentPadding()),
            contentAlignment = Alignment.CenterStart,
        ) { content(style) }
    }
}

/** A field's content line: as wide as the well and as high as its least height, so a tap anywhere in the well lands on the line. */
@Composable
fun Modifier.fieldLine(): Modifier = fillMaxWidth().heightIn(min = FieldDimens.wellHeightAt(PlumTheme.layout.textScale))

/** The least height of a field's box at the current text size: the well (grown with the text) plus the collar. */
@Composable
fun fieldBoxHeight(): Dp = FieldDimens.wellHeightAt(PlumTheme.layout.textScale) + CollarSize.Field.topPadding + CollarSize.Field.bottomPadding

/** Test tag of a field's box (collar included). */
object FieldTags {
    const val BOX = "field_box"
    const val LABEL = "field_label"
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
