package com.plumemu.report

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.FieldRow
import com.plumemu.coreui.components.FieldRowFrame
import com.plumemu.coreui.components.OptionRow
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.search.hideSystemBars
import com.plumemu.keyboard.CaretLine
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.telemetry.ReportResult

/**
 * The Report a problem form's body (Settings › About): the two fields, the switch, one line saying
 * exactly what Send will send, a line for the last answer or mistake, then Send over Cancel; after
 * a report was sent or saved, that line and Done. [onTap] gets the item's place in
 * [ProblemReportForm.items]; [onAction] gets the Android keyboard's Done as an Enter.
 * [statisticsOn] decides whether the anonymous install id goes with the report.
 */
@Composable
internal fun ProblemReportBody(
    form: ProblemReportForm,
    statisticsOn: Boolean,
    onTap: (Int) -> Unit,
    onAction: (Action) -> Unit,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag(ReportTags.FORM), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (form.isFinished) {
            Line(reportResultText(form.result), ReportTags.RESULT, isQuiet = false)
            Buttons(form, onTap, listOf(ReportItem.Done to R.string.settings_hint_done))
            return@Column
        }
        val textFocus = remember { FocusRequester() }
        val emailFocus = remember { FocusRequester() }
        // Android's keyboard only: Plumemu's tray is drawn by the host, over the screen's bottom.
        if (form.typing == null) FormKeyboard(form, textFocus, emailFocus)
        val labels = listOf(stringResource(R.string.report_text_label), stringResource(R.string.report_email_label))
        val measurer = rememberTextMeasurer()
        val labelStyle = PlumTheme.type.settingsOption
        val labelWidth = with(LocalDensity.current) { labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
        val layout = PlumTheme.layout
        // What must show under a field typed over Plumemu's tray: the next row.
        val belowField = PlumDimens.listRowGap + layout.rowHeight
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
            Field(form, ReportItem.Text, textFocus, onTap, onAction, labelWidth, belowField)
            Field(form, ReportItem.Email, emailFocus, onTap, onAction, labelWidth, belowField)
            DetailsRow(form, onTap)
        }
        Line(stringResource(sendsLine(form.includeDetails, statisticsOn)), ReportTags.SENDS, isQuiet = true)
        when {
            form.isSending -> Line(stringResource(R.string.report_sending), ReportTags.STATUS, isQuiet = true)
            form.mistake != null -> Line(reportMistakeText(form.mistake), ReportTags.STATUS, isQuiet = false)
            form.result != null -> Line(reportResultText(form.result), ReportTags.STATUS, isQuiet = false)
            form.isAtLimit -> Line(stringResource(R.string.report_at_limit), ReportTags.STATUS, isQuiet = false)
        }
        Buttons(form, onTap, listOf(ReportItem.Send to R.string.report_send, ReportItem.Leave to R.string.settings_cancel))
    }
}

/** The one line before Send: what goes, in the four cases of the switch and the statistics. */
@StringRes
internal fun sendsLine(
    includeDetails: Boolean,
    statisticsOn: Boolean,
): Int =
    when {
        includeDetails && statisticsOn -> R.string.report_sends_all
        includeDetails -> R.string.report_sends_details
        statisticsOn -> R.string.report_sends_id
        else -> R.string.report_sends_text
    }

/** What A does on the focused item, for the hint bar; none while the report is being sent. */
@StringRes
internal fun reportConfirmHint(form: ProblemReportForm): Int? =
    if (form.isSending) {
        null
    } else {
        when (form.focus) {
            ReportItem.Text, ReportItem.Email -> R.string.account_hint_type
            ReportItem.Details -> R.string.settings_hint_change
            ReportItem.Send -> R.string.report_send
            ReportItem.Leave -> R.string.settings_cancel
            ReportItem.Done -> R.string.settings_hint_done
        }
    }

@Composable
internal fun reportMistakeText(mistake: ReportMistake?): String =
    when (mistake) {
        ReportMistake.NoText -> stringResource(R.string.report_error_no_text)
        ReportMistake.BadEmail -> stringResource(R.string.report_error_email)
        ReportMistake.EmailTooLong -> stringResource(R.string.report_error_email_long)
        null -> ""
    }

@Composable
internal fun reportResultText(result: ReportResult?): String =
    when (result) {
        ReportResult.Sent -> stringResource(R.string.report_sent)
        ReportResult.Saved -> stringResource(R.string.report_saved)
        ReportResult.Refused -> stringResource(R.string.report_error_refused)
        ReportResult.NotSaved -> stringResource(R.string.report_error_not_saved)
        ReportResult.Unavailable -> stringResource(R.string.report_error_unavailable)
        ReportResult.Empty -> stringResource(R.string.report_error_no_text)
        null -> ""
    }

@Composable
private fun DetailsRow(
    form: ProblemReportForm,
    onTap: (Int) -> Unit,
) {
    val index = form.items.indexOf(ReportItem.Details)
    val focused = form.focus == ReportItem.Details && !form.isSending
    OptionRow(
        label = stringResource(R.string.report_details),
        look = if (focused) OptionRowLook.Focused else OptionRowLook.Normal,
        value = stringResource(if (form.includeDetails) R.string.settings_switch_on else R.string.settings_switch_off),
        modifier =
            Modifier
                .bringIntoViewWhen(focused)
                .semantics { role = Role.Switch }
                .pointerInput(index, onTap) { detectTapGestures { onTap(index) } }
                .testTag(ReportTags.DETAILS),
    )
}

@Composable
private fun Field(
    form: ProblemReportForm,
    item: ReportItem,
    requester: FocusRequester,
    onTap: (Int) -> Unit,
    onAction: (Action) -> Unit,
    labelWidth: Dp,
    belowWhileTyping: Dp,
) {
    if (form.typing != null) return OwnKeyboardField(form, item, onTap, labelWidth, belowWhileTyping)
    val isEmail = item == ReportItem.Email
    val focused = form.focus == item && !form.isSending
    // Only the field being typed in can take the input focus, or Android's window focus would bring its keyboard up.
    val typing = focused && form.inField
    val index = form.items.indexOf(item)
    FieldRow(
        value = if (isEmail) form.email else form.text,
        onValueChange = if (isEmail) form::onEmailChange else form::onTextChange,
        look = if (focused) OptionRowLook.Focused else OptionRowLook.Normal,
        placeholder = "",
        label = stringResource(if (isEmail) R.string.report_email_label else R.string.report_text_label),
        labelWidth = labelWidth,
        keyboardHint = stringResource(if (isEmail) R.string.report_email_hint else R.string.report_text_hint),
        modifier = Modifier.bringIntoViewWhen(focused).pointerInput(index) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial).also { onTap(index) } } }
            .testTag(if (isEmail) ReportTags.EMAIL else ReportTags.TEXT),
        fieldModifier = Modifier.focusProperties { canFocus = typing }.focusRequester(requester),
        keyboardOptions =
            KeyboardOptions(
                capitalization = if (isEmail) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = !isEmail,
                keyboardType = if (isEmail) KeyboardType.Email else KeyboardType.Text,
                imeAction = if (isEmail) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { form.onKeyboardNext() },
                onDone = {
                    form.onKeyboardDone()
                    onAction(Action.Enter)
                },
            ),
    )
}

/** A field typed with Plumemu's keyboard: [FieldRowFrame]'s look and a drawn line with a static caret ([CaretLine]), no text field. */
@Composable
private fun OwnKeyboardField(
    form: ProblemReportForm,
    item: ReportItem,
    onTap: (Int) -> Unit,
    labelWidth: Dp,
    belowWhileTyping: Dp,
) {
    val isEmail = item == ReportItem.Email
    val focused = form.focus == item && !form.isSending
    val index = form.items.indexOf(item)
    val keys = form.keysOf(item)
    val hint = stringResource(if (isEmail) R.string.report_email_hint else R.string.report_text_hint)
    val trayUp = focused && form.isTrayUp
    val onTapAt =
        remember(form, item, onTap) {
            { offset: Int ->
                form.placeCaret(item, offset)
                onTap(index)
            }
        }
    FieldRowFrame(
        look = if (focused) OptionRowLook.Focused else OptionRowLook.Normal,
        modifier =
            Modifier
                .bringIntoViewWhen(focused, above = trayUp, below = if (trayUp) belowWhileTyping else 0.dp)
                .pointerInput(index, onTap) { detectTapGestures { onTap(index) } }
                .semantics { contentDescription = hint }
                .testTag(if (isEmail) ReportTags.EMAIL else ReportTags.TEXT),
        answersButton = !trayUp,
    ) { style ->
        BasicText(
            stringResource(if (isEmail) R.string.report_email_label else R.string.report_text_label),
            style = PlumTheme.type.settingsOption.copy(color = PlumTheme.colors.muted),
            maxLines = 1,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(PlumDimens.rowTrailingGap))
        CaretLine(
            text = if (isEmail) form.email else form.text,
            caret = { keys.caret },
            showsCaret = { form.focus == item && form.isOnField },
            placeholder = "",
            style = style,
            onTapAt = onTapAt,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            textTag = ReportTags.typedText(item),
            placeholderTag = ReportTags.placeholder(item),
        )
    }
}

/** Android's keyboard follows [ProblemReportForm.inField], as the sign-in form's does: opening the form never brings it up. */
@Composable
private fun FormKeyboard(
    form: ProblemReportForm,
    textFocus: FocusRequester,
    emailFocus: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val owner = remember { KeyboardOwner() }
    val target = if (form.inField) form.focus else null
    LaunchedEffect(target, form.keyboardAsks) {
        when (target) {
            ReportItem.Text -> textFocus.requestFocusSafely()
            ReportItem.Email -> emailFocus.requestFocusSafely()
            else -> Unit
        }
        if (target != null) {
            keyboard?.show()
            owner.hasShown = true
        } else if (owner.hasShown) {
            keyboard?.hide()
            focusManager.clearFocus()
            hideSystemBars(view)
            owner.hasShown = false
        }
    }
    DisposableEffect(view) {
        onDispose {
            if (owner.hasShown) {
                keyboard?.hide()
                hideSystemBars(view)
            }
        }
    }
}

private class KeyboardOwner {
    var hasShown = false
}

/** A requester not attached yet (the field is still being laid out) throws; the next frame asks again. */
private fun FocusRequester.requestFocusSafely() {
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
        android.util.Log.w("PlumReport", "report field not ready for focus (${e.javaClass.simpleName})")
    }
}

@Composable
private fun Buttons(
    form: ProblemReportForm,
    onTap: (Int) -> Unit,
    buttons: List<Pair<ReportItem, Int>>,
) {
    // As wide as the widest, one over the other: the panel is narrow.
    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
        buttons.forEachIndexed { at, (item, label) ->
            val index = form.items.indexOf(item)
            SetupButton(
                text = stringResource(label),
                isPrimary = at == 0,
                isFocused = form.focus == item && !form.isSending,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewWhen(form.focus == item)
                        .reportTap(index, onTap)
                        .testTag(ReportTags.item(item)),
            )
        }
    }
}

/** A button tap that accessibility services can press too. */
private fun Modifier.reportTap(
    index: Int,
    onTap: (Int) -> Unit,
): Modifier =
    semantics {
        role = Role.Button
        onClick {
            onTap(index)
            true
        }
    }.pointerInput(index, onTap) { detectTapGestures(onTap = { onTap(index) }) }

@Composable
private fun Line(
    text: String,
    tag: String,
    isQuiet: Boolean,
) {
    val colors = PlumTheme.colors
    BasicText(
        text,
        style = PlumTheme.type.setupNote.copy(color = if (isQuiet) colors.muted else colors.text),
        maxLines = LINE_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(tag),
    )
}

internal object ReportTags {
    const val FORM = "report_form"
    const val TEXT = "report_text"
    const val EMAIL = "report_email"
    const val DETAILS = "report_details"
    const val SENDS = "report_sends"
    const val STATUS = "report_status"
    const val RESULT = "report_result"

    fun item(item: ReportItem): String = "report_item_${item.name}"

    fun typedText(item: ReportItem): String = "report_typed_${item.name}"

    fun placeholder(item: ReportItem): String = "report_placeholder_${item.name}"
}

/** The "sends" line takes up to 4 lines at the larger text sizes; a status line 4, as the sign-in form's. */
private const val LINE_LINES = 4
