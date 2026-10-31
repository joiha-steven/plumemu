package com.plumemu.account

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.FieldRow
import com.plumemu.coreui.components.FieldRowFrame
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.search.hideSystemBars
import com.plumemu.keyboard.CaretLine
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import java.text.DateFormat
import java.util.Date

/**
 * The sign-in form's body, the same in setup and in Settings' panel: the two fields, the message
 * line, then Sign in and [leaveLabel] side by side; once signed in, "Signed in as …" and Continue
 * (setup only: Settings closes its panel instead). [onTap] gets the item's place in
 * [SignInForm.items]; [onAction] gets the keyboard's Done as an Enter on Sign in, like the A button
 * (its Next stays in the form: the password and its keyboard).
 */
@Composable
internal fun SignInBody(
    form: SignInForm,
    @StringRes leaveLabel: Int,
    onTap: (Int) -> Unit,
    onAction: (Action) -> Unit,
    /** Between the fields, the message and the buttons: setup's content gap, or the panel's. */
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag(SignInTags.FORM), verticalArrangement = Arrangement.spacedBy(gap)) {
        val name = form.signedInAs
        if (name != null) {
            Line(stringResource(R.string.account_signed_in_as, name), SignInTags.SIGNED_IN, isQuiet = false)
            Buttons(form, onTap, listOf(SignInItem.Continue to R.string.setup_continue))
            return@Column
        }
        val usernameFocus = remember { FocusRequester() }
        val passwordFocus = remember { FocusRequester() }
        // Android's keyboard only: Plumemu's tray is drawn by the host (setup, Settings), over the screen's bottom.
        if (form.typing == null) FormKeyboard(form, usernameFocus, passwordFocus)
        // The two fields are rows of one list: 2 apart, like every list.
        // Each field keeps its name before the text; both names get the wider one's width, so the
        // two texts start on one line.
        val labels = listOf(stringResource(R.string.account_username), stringResource(R.string.account_password))
        val measurer = rememberTextMeasurer()
        val labelStyle = PlumTheme.type.settingsOption
        val density = LocalDensity.current
        val labelWidth = with(density) { labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
        // What must show under a field typed over Plumemu's tray: the password row under the username;
        // under the password, the message line or Sign in (a row or a button, whichever is taller) with the
        // button's focus ring's reach, which also covers its word where a large font's line is taller than the button.
        val layout = PlumTheme.layout
        val belowUsername = PlumDimens.listRowGap + layout.rowHeight
        val belowPassword = gap + maxOf(layout.rowHeight, layout.setupButtonHeight) + PlumDimens.focusRingOutset
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
            Field(form, SignInItem.Username, usernameFocus, onTap, onAction, labelWidth, belowUsername)
            Field(form, SignInItem.Password, passwordFocus, onTap, onAction, labelWidth, belowPassword)
        }
        when {
            form.isChecking -> Line(stringResource(R.string.account_checking), SignInTags.CHECKING, isQuiet = true)
            form.message != null -> form.message?.let { Line(signInMessage(it), SignInTags.MESSAGE, isQuiet = false) }
        }
        Buttons(form, onTap, listOf(SignInItem.SignIn to R.string.account_sign_in, SignInItem.Leave to leaveLabel))
    }
}

/** What A does on the focused item, for the hint bar ([leaveLabel] on Skip / Cancel); none while the check runs. */
@StringRes
internal fun signInConfirmHint(
    form: SignInForm,
    @StringRes leaveLabel: Int,
): Int? =
    if (form.isChecking) {
        null
    } else {
        when (form.focus) {
            // A on a field opens its keyboard.
            SignInItem.Username, SignInItem.Password -> R.string.account_hint_type
            SignInItem.SignIn -> R.string.account_sign_in
            SignInItem.Leave -> leaveLabel
            SignInItem.Continue -> R.string.setup_hint_continue
        }
    }

/** The plain line for a failed sign-in: one place, so setup and Settings say the same. */
@Composable
internal fun signInMessage(result: SignInResult): String =
    when (result) {
        is SignInResult.SignedIn -> stringResource(R.string.account_signed_in_as, result.username)
        SignInResult.Missing -> stringResource(R.string.account_error_missing)
        SignInResult.WrongCredentials -> stringResource(R.string.account_error_wrong)
        SignInResult.Offline -> stringResource(R.string.account_error_offline)
        SignInResult.ServerClosed -> stringResource(R.string.account_error_closed)
        is SignInResult.DailyLimit -> stringResource(R.string.account_error_daily_limit, clockTime(result.resumeAtMillis))
        is SignInResult.ServerError -> stringResource(R.string.account_error_server, result.code)
        SignInResult.AppRefused -> stringResource(R.string.account_error_app_refused)
        SignInResult.NotSaved -> stringResource(R.string.account_error_not_saved)
        SignInResult.NotAvailable -> stringResource(R.string.account_error_not_available)
    }

/** [millis] as the device shows a time of day (its 12 or 24 hour choice), in the device's time zone. */
@Composable
internal fun clockTime(millis: Long): String {
    val context = LocalContext.current
    val format: DateFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    return format.format(Date(millis))
}

@Composable
private fun Field(
    form: SignInForm,
    item: SignInItem,
    requester: FocusRequester,
    onTap: (Int) -> Unit,
    onAction: (Action) -> Unit,
    labelWidth: Dp,
    /** With Plumemu's tray up: how much under the field must show too (the next row or line). */
    belowWhileTyping: Dp,
) {
    if (form.typing != null) return OwnKeyboardField(form, item, onTap, labelWidth, belowWhileTyping)
    val isPassword = item == SignInItem.Password
    val focused = form.focus == item && !form.isChecking
    // Only the field being typed in can take the input focus: the cursor resting on a row is not
    // enough, or Android's window focus would hand it to the text field and its keyboard would come up.
    val typing = focused && form.inField
    val index = form.items.indexOf(item)
    FieldRow(
        value = if (isPassword) form.password else form.username,
        onValueChange = if (isPassword) form::onPasswordChange else form::onUsernameChange,
        look = if (focused) OptionRowLook.Focused else OptionRowLook.Normal,
        // The label names the field; an empty one needs no placeholder (it would not fit beside the label at the larger sizes).
        placeholder = "",
        label = stringResource(if (isPassword) R.string.account_password else R.string.account_username),
        labelWidth = labelWidth,
        keyboardHint = stringResource(if (isPassword) R.string.account_password_hint else R.string.account_username_hint),
        // A touch on the field means typing there; seen before the field handles it.
        modifier = Modifier.bringIntoViewWhen(focused).pointerInput(index) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial).also { onTap(index) } } }
            .testTag(if (isPassword) SignInTags.PASSWORD else SignInTags.USERNAME),
        fieldModifier = Modifier.focusProperties { canFocus = typing }.focusRequester(requester),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (isPassword) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { form.onKeyboardNext() },
                onDone = {
                    form.onKeyboardDone()
                    onAction(Action.Enter)
                },
            ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

/**
 * A field typed with Plumemu's keyboard: [FieldRow]'s look with its label
 * column (the dot's room in it, so the two texts start on one line, as Android's path draws them)
 * and a drawn line with a static caret ([CaretLine]) where the cursor is, no text field, so
 * Android's keyboard can never come up. The password shows one dot per character, the same as
 * Android's masked field, and nothing else: no semantics text, no log. A tap on the line puts the
 * caret at the tapped character ([SignInForm.placeCaret]) and, like a tap anywhere on the row,
 * reaches the host as a tap on the item, which focuses it and brings the tray up. While the tray is
 * up the focused field asks its scroll to show it and the line under it (the host lays its scroll
 * out above the tray).
 */
@Composable
private fun OwnKeyboardField(
    form: SignInForm,
    item: SignInItem,
    onTap: (Int) -> Unit,
    labelWidth: Dp,
    belowWhileTyping: Dp,
) {
    val isPassword = item == SignInItem.Password
    val focused = form.focus == item && !form.isChecking
    val index = form.items.indexOf(item)
    val keys = form.keysOf(item)
    val text = if (isPassword) PASSWORD_DOT.toString().repeat(form.password.length) else form.username
    val hint = stringResource(if (isPassword) R.string.account_password_hint else R.string.account_username_hint)
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
                // With the tray up: the field and the row or line under it (the scroll ends at the tray).
                .bringIntoViewWhen(focused, above = trayUp, below = if (trayUp) belowWhileTyping else 0.dp)
                .pointerInput(index, onTap) { detectTapGestures { onTap(index) } }
                .semantics { contentDescription = hint }
                .testTag(if (isPassword) SignInTags.PASSWORD else SignInTags.USERNAME),
        answersButton = !trayUp,
    ) { style ->
        // The dot (drawn by the frame while focused) belongs to the label column: unfocused, its room follows the label.
        BasicText(
            stringResource(if (isPassword) R.string.account_password else R.string.account_username),
            style = PlumTheme.type.settingsOption.copy(color = PlumTheme.colors.muted),
            maxLines = 1,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(PlumDimens.rowTrailingGap))
        CaretLine(
            text = text,
            caret = { keys.caret },
            showsCaret = { form.focus == item && form.isOnField },
            placeholder = "",
            style = style,
            onTapAt = onTapAt,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            textTag = SignInTags.typedText(item),
            placeholderTag = SignInTags.placeholder(item),
        )
    }
}

/** What a masked password shows for each character, as Android's [PasswordVisualTransformation]. */
private const val PASSWORD_DOT = '\u2022'

/**
 * The keyboard follows [SignInForm.inField]: up when A or a tap asked for it on a field (again on
 * each ask, [SignInForm.keyboardAsks]: the system's Back may have closed it); hidden, with the
 * system bars hidden again, when the cursor leaves the field, a check starts or the form goes away.
 * Opening the form or moving onto a field never brings it up.
 *
 * Only after this form put the keyboard up: a form that opens on a button (a failed sign-in, a
 * check running) leaves the keyboard, the focus and the system bars as they are.
 */
@Composable
private fun FormKeyboard(
    form: SignInForm,
    usernameFocus: FocusRequester,
    passwordFocus: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val owner = remember { KeyboardOwner() }
    val target = if (form.inField) form.focus else null
    LaunchedEffect(target, form.keyboardAsks) {
        when (target) {
            SignInItem.Username -> usernameFocus.requestFocusSafely()
            SignInItem.Password -> passwordFocus.requestFocusSafely()
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

/** Whether this form has the keyboard up (plain, not state: nothing draws from it). */
private class KeyboardOwner {
    var hasShown = false
}

/** A requester not attached yet (the field is still being laid out) throws; the next frame asks again. */
private fun FocusRequester.requestFocusSafely() {
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
        android.util.Log.w("PlumAccount", "sign-in field not ready for focus (${e.javaClass.simpleName})")
    }
}

@Composable
private fun Buttons(
    form: SignInForm,
    onTap: (Int) -> Unit,
    buttons: List<Pair<SignInItem, Int>>,
) {
    // On the column's left line: the host's scroll leaves room for the focus ring (setup's `bleed`).
    val place: @Composable (@Composable () -> Unit) -> Unit =
        if (form.stackedButtons) {
            // As wide as the wider of the two, both of them.
            { Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) { it() } }
        } else {
            { Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) { it() } }
        }
    place {
        buttons.forEachIndexed { at, (item, label) ->
            val index = form.items.indexOf(item)
            SetupButton(
                text = stringResource(label),
                isPrimary = at == 0,
                isFocused = form.focus == item && !form.isChecking,
                modifier =
                    Modifier
                        .then(if (form.stackedButtons) Modifier.fillMaxWidth() else Modifier)
                        .bringIntoViewWhen(form.focus == item)
                        .signInTap(index, onTap)
                        .testTag(SignInTags.item(item)),
            )
        }
    }
}

/** A button tap that accessibility services can press too. */
private fun Modifier.signInTap(
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

internal object SignInTags {
    const val FORM = "sign_in_form"
    const val USERNAME = "sign_in_username"
    const val PASSWORD = "sign_in_password"
    const val MESSAGE = "sign_in_message"
    const val CHECKING = "sign_in_checking"
    const val SIGNED_IN = "sign_in_signed_in"

    fun item(item: SignInItem): String = "sign_in_item_${item.name}"

    /** The drawn line of a field typed with Plumemu's keyboard (the password's: its dots). */
    fun typedText(item: SignInItem): String = "sign_in_typed_${item.name}"

    fun placeholder(item: SignInItem): String = "sign_in_placeholder_${item.name}"
}

/** A status or error line's most lines: 4 since the 13 sp text size makes the daily-limit line take 4 at Largest in Mono. */
private const val LINE_LINES = 4
