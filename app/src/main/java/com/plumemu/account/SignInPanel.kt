package com.plumemu.account

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.FieldRow
import com.plumemu.coreui.components.FieldRowFrame
import com.plumemu.coreui.components.fieldLine
import com.plumemu.coreui.components.fieldBoxHeight
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.ButtonDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.paste
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
    /** Lines under the buttons (RetroAchievements' help and privacy lines); not drawn once signed in. */
    below: @Composable () -> Unit = {},
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
        val labels = listOf(stringResource(R.string.account_username), stringResource(passwordLabel(form)))
        val measurer = rememberTextMeasurer()
        val labelStyle = PlumTheme.type.settingsOption
        val density = LocalDensity.current
        val labelWidth = with(density) { labels.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }.toDp() }
        // What must show under a field typed over Plumemu's tray: the password row under the username;
        // under the password, the message line or Sign in (a row or a button, whichever is taller) with the
        // button's focus ring's reach, which also covers its word where a large font's line is taller than the button.
        val layout = PlumTheme.layout
        val belowUsername = FieldDimens.gap + fieldBoxHeight()
        val belowPassword = gap + maxOf(layout.rowHeight, layout.setupButtonHeight) + PlumDimens.focusRingOutset
        Column(verticalArrangement = Arrangement.spacedBy(FieldDimens.gap)) {
            Field(form, SignInItem.Username, usernameFocus, onTap, onAction, labelWidth, belowUsername)
            Field(form, SignInItem.Password, passwordFocus, onTap, onAction, labelWidth, belowPassword)
        }
        when {
            form.isChecking -> Line(stringResource(if (form.kind == SignInKind.RetroAchievements) R.string.account_ra_checking else R.string.account_checking), SignInTags.CHECKING, isQuiet = true)
            form.message != null -> form.message?.let { Line(signInMessage(it, form.kind), SignInTags.MESSAGE, isQuiet = false) }
        }
        // Tray up over the username: the scroll shows the two fields, so the buttons under them would show as strips above the tray.
        // They keep their room, unseen. Over the password the scroll brings Sign in whole into view; the buttons stacked
        // under it would show their top as a strip above the tray, so they stay unseen too.
        val overUsername = form.isTrayUp && form.focus == SignInItem.Username
        val underSignIn = form.isTrayUp && form.stackedButtons
        Buttons(form, onTap, listOf(SignInItem.SignIn to R.string.account_sign_in, SignInItem.Leave to leaveLabel), gap = gap, unseen = overUsername, unseenAfterFirst = underSignIn)
        if (SignInItem.OpenKeyPage in form.items) Buttons(form, onTap, listOf(SignInItem.OpenKeyPage to R.string.settings_ra_open_key_page), isPrimary = false, unseen = overUsername || form.isTrayUp)
        below()
    }
}

/** The second field's name: a password for ScreenScraper, the Web API key for RetroAchievements. */
@StringRes
private fun passwordLabel(form: SignInForm): Int = if (form.kind == SignInKind.RetroAchievements) R.string.account_ra_key else R.string.account_password

@StringRes
private fun usernameHint(form: SignInForm): Int = if (form.kind == SignInKind.RetroAchievements) R.string.account_ra_username_hint else R.string.account_username_hint

@StringRes
private fun passwordHint(form: SignInForm): Int = if (form.kind == SignInKind.RetroAchievements) R.string.account_ra_key_hint else R.string.account_password_hint

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
            SignInItem.OpenKeyPage -> R.string.settings_ra_open_key_page
            SignInItem.Continue -> R.string.setup_hint_continue
        }
    }

/** The plain line for a failed sign-in: one place, so setup and Settings say the same. */
@Composable
internal fun signInMessage(
    result: SignInResult,
    kind: SignInKind = SignInKind.ScreenScraper,
): String =
    when (result) {
        SignInResult.RaWrongKey -> stringResource(R.string.account_ra_error_wrong)
        SignInResult.RaUnknownUser -> stringResource(R.string.account_ra_error_unknown_user)
        SignInResult.RaBusy -> stringResource(R.string.account_ra_error_busy)
        SignInResult.RaRateLimited -> stringResource(R.string.account_ra_error_rate_limited)
        is SignInResult.SignedIn -> stringResource(R.string.account_signed_in_as, result.username)
        SignInResult.Missing -> stringResource(if (kind == SignInKind.RetroAchievements) R.string.account_ra_error_missing else R.string.account_error_missing)
        SignInResult.WrongCredentials -> stringResource(R.string.account_error_wrong)
        SignInResult.Offline -> stringResource(R.string.account_error_offline)
        SignInResult.ServerClosed -> stringResource(R.string.account_error_closed)
        is SignInResult.DailyLimit -> stringResource(R.string.account_error_daily_limit, clockTime(result.resumeAtMillis))
        is SignInResult.ServerError -> stringResource(R.string.account_error_server, result.code)
        SignInResult.AppRefused -> stringResource(R.string.account_error_app_refused)
        SignInResult.NotSaved -> stringResource(R.string.account_error_not_saved)
        SignInResult.NotAvailable -> stringResource(R.string.account_error_not_available)
        SignInResult.NoBrowser -> stringResource(R.string.settings_ra_no_browser)
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
        label = stringResource(if (isPassword) passwordLabel(form) else R.string.account_username),
        labelWidth = labelWidth,
        keyboardHint = stringResource(if (isPassword) passwordHint(form) else usernameHint(form)),
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
    val hint = stringResource(if (isPassword) passwordHint(form) else usernameHint(form))
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
        label = stringResource(if (isPassword) passwordLabel(form) else R.string.account_username),
        labelWidth = labelWidth,
        modifier =
            Modifier
                // With the tray up: the field and the row or line under it (the scroll ends at the tray).
                .bringIntoViewWhen(focused, above = trayUp, below = if (trayUp) belowWhileTyping else 0.dp)
                .pointerInput(index, onTap) { detectTapGestures { onTap(index) } }
                .semantics { contentDescription = hint }
                .testTag(if (isPassword) SignInTags.PASSWORD else SignInTags.USERNAME),
    ) { style ->
        CaretLine(
            text = text,
            caret = { keys.caret },
            showsCaret = { form.focus == item && form.isOnField },
            placeholder = "",
            style = style,
            onTapAt = onTapAt,
            modifier = Modifier.fieldLine(),
            textTag = SignInTags.typedText(item),
            placeholderTag = SignInTags.placeholder(item),
            onPaste = form.typing?.let { typing -> { typing.paste() } },
        )
    }
}

/** What a masked password shows for each character, as Android's [PasswordVisualTransformation]. */
private const val PASSWORD_DOT = '\u2022'

/**
 * The keyboard follows [SignInForm.inField]: up when A or a tap asked for it on a field (again on
 * each ask, [SignInForm.keyboardAsks]: the system's Back may have closed it); hidden, with the
 * system bars hidden again, when the cursor leaves the field, a check starts or the form goes away.
 * Moving onto a field never brings it up; opening the step does, through the form's own ask
 * ([SignInForm.startTyping]).
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
    /** The first button is the main one; false for a lone secondary button. */
    isPrimary: Boolean = true,
    /** The form's gap above the buttons: with Plumemu's tray up the collar hangs into it, down to `signInCollarFloor` from the field. */
    gap: Dp = 0.dp,
    /** Measured, never placed: no pixel drawn, no tap taken (see [unplaced]). */
    unseen: Boolean = false,
    /** Every button but the first is unseen (they are stacked under it). */
    unseenAfterFirst: Boolean = false,
) {
    // On the column's left line: the host's scroll leaves room for the focus ring (setup's `bleed`).
    val place: @Composable (@Composable () -> Unit) -> Unit =
        if (form.stackedButtons) {
            // As wide as the wider of the two, both of them.
            { Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) { it() } }
        } else {
            { Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) { it() } }
        }
    Box(if (unseen) Modifier.unplaced() else Modifier) { place {
        buttons.forEachIndexed { at, (item, label) ->
            val index = form.items.indexOf(item)
            SetupButton(
                text = stringResource(label),
                isPrimary = isPrimary && at == 0,
                isFocused = form.focus == item && !form.isChecking,
                maxLines = if (item == SignInItem.OpenKeyPage) 2 else 1,
                hangAbove = if (form.isTrayUp) signInCollarHang(gap) else 0.dp,
                collarSize = if (form.isTrayUp) CollarSize.Key else CollarSize.Control,
                modifier =
                    Modifier
                        .then(if (unseenAfterFirst && at > 0) Modifier.unplaced() else Modifier)
                        .then(if (form.stackedButtons) Modifier.fillMaxWidth() else Modifier)
                        .bringIntoViewWhen(form.focus == item)
                        .signInTap(index, onTap)
                        .testTag(SignInTags.item(item)),
            )
        }
    } }
}

/** Keeps the room of the content and places none of it. */
internal fun Modifier.unplaced(): Modifier = layout { measurable, constraints -> measurable.measure(constraints).let { layout(it.width, it.height) {} } }

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
        modifier = Modifier.padding(start = PlumDimens.gameRowPadding, end = PlumDimens.panelTextEnd).testTag(tag),
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

/** A status or error line's most lines: 5 since the text start moved in, the daily-limit line takes 5 at Largest in Mono. */
private const val LINE_LINES = 5

/** How much of the small collar's top (tray up) hangs over the gap above the buttons: all of it, so the face stays where it sat, unless that leaves less than the floor. */
internal fun signInCollarHang(gap: Dp): Dp = (gap - ButtonDimens.signInCollarFloor).coerceIn(0.dp, CollarSize.Key.topPadding)
