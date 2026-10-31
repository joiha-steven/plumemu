package com.plumemu.account

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.game.FieldKeys
import com.plumemu.game.KeyboardAsk
import com.plumemu.game.TypingOutcome
import com.plumemu.game.onKeyboardEvent
import com.plumemu.game.textEntry
import com.plumemu.keyboard.KeyboardChoice

/** What the sign-in form can focus, top to bottom; Sign in and [Leave] sit side by side. */
internal enum class SignInItem {
    Username,
    Password,
    SignIn,

    /** "Skip" in setup, "Cancel" in Settings. */
    Leave,

    /** Signed in: the only item, "Continue" in setup. */
    Continue,
}

/** What the host must do after the form handled an action. */
internal sealed interface SignInStep {
    /** Handled inside the form; [moved]: the focus went to another item (for its sound). */
    data class Stay(val moved: Boolean = false) : SignInStep

    /** The person asked to sign in with what the fields hold; the form now shows "Checking…". */
    data class Submit(val username: String, val password: String) : SignInStep {
        override fun toString(): String = "Submit(username=***, password=***)"
    }

    /** Skip (setup) or Cancel (Settings): leave without an account. */
    data object Leave : SignInStep

    /** Continue after a sign-in that worked. */
    data object Done : SignInStep

    /** Not the form's (Back, START): the host's own handling applies. */
    data object NotHandled : SignInStep
}

/**
 * The ScreenScraper sign-in shared by setup and Settings: a username field, a masked password
 * field, Sign in and Skip / Cancel side by side, and one message line. For both inputs:
 * Up / Down move between the fields and the buttons, Left / Right between the two buttons.
 * The keyboard never comes up by itself: the cursor can rest on a field with the
 * keyboard down; A or a tap on a field opens its keyboard, moving off a field puts it away. The
 * keyboard's Next goes to the password with its keyboard, its Done ("Sign in") presses Sign in; A
 * on Sign in submits; a tap on a button presses it. While the check runs only Back is the host's;
 * everything else waits. The password lives only here, in memory, until the host hands it to the
 * graph. Main thread only.
 *
 * [keyboardKind] is Settings › Controls › Keyboard when the host made the form (keyboard step
 * 2b-2): with Plumemu's the fields are typed through [typing] (the game panel's button map, the
 * host draws the tray), with Android's through its text fields. Android's by default, so a host
 * without a tray never gets a field nobody can type into.
 */
@Stable
internal class SignInForm(
    initialUsername: String = "",
    signedInAs: String? = null,
    /** Sign in over [SignInItem.Leave] (a narrow panel) instead of beside it: Down and Up move between them too. */
    val stackedButtons: Boolean = false,
    val keyboardKind: KeyboardChoice = KeyboardChoice.Android,
) {
    var username: String by mutableStateOf(initialUsername)
        private set

    var password: String by mutableStateOf("")
        private set

    /** The name ScreenScraper accepted in this form, or the stored one; the form then shows only Continue. */
    var signedInAs: String? by mutableStateOf(signedInAs)
        private set

    var focus: SignInItem by mutableStateOf(if (signedInAs != null) SignInItem.Continue else SignInItem.Username)
        private set

    /** A check is running: the fields and buttons wait for it. */
    var isChecking: Boolean by mutableStateOf(false)
        private set

    /** What the last attempt ended in; null before one, while one runs, and after a success. */
    var message: SignInResult? by mutableStateOf(null)
        private set

    /** The items in screen order; a tap on the n-th is item n. */
    val items: List<SignInItem>
        get() = if (signedInAs != null) listOf(SignInItem.Continue) else listOf(SignInItem.Username, SignInItem.Password, SignInItem.SignIn, SignInItem.Leave)

    /**
     * Whether the person asked to type (A or a tap on a field; moving off the field ends it) and
     * how many times ([KeyboardAsk.asks]: an ask brings Android's keyboard back even when the
     * system's Back closed it while the cursor stayed on the field). Plumemu's tray puts it away
     * itself (its hide key, B on an empty field, Back).
     */
    val keyboard = KeyboardAsk()

    /** A or a tap asked for the focused field's keyboard; moving off the field ends it. */
    val isTyping: Boolean get() = keyboard.isTyping

    /** Counts the asks for a keyboard (A or a tap on a field), see [keyboard]. */
    val keyboardAsks: Int get() = keyboard.asks

    /** The cursor is on a text field and the person asked to type there: its keyboard is up. */
    val inField: Boolean get() = !isChecking && isTyping && focus.isField

    /** The cursor is on a text field (typing or not) and no check runs. */
    val isOnField: Boolean get() = !isChecking && focus.isField

    /** Where Plumemu's keyboard types in each field: the caret (at the end of a pre-filled name) and the keys. */
    val usernameKeys = FieldKeys(initialUsername, capitalizesFirst = false)

    val passwordKeys = FieldKeys("", capitalizesFirst = false)

    /** The fields as Plumemu's keyboard types into them; null with Android's keyboard. */
    val typing: SignInTyping? = if (keyboardKind == KeyboardChoice.Plumemu) SignInTyping(this) else null

    /** Plumemu's tray is up over the form (never with Android's keyboard). */
    val isTrayUp: Boolean get() = typing != null && inField

    /**
     * What the host's input router must send now: with Plumemu's
     * keyboard, B erasing and typed keys while the tray is up, typed keys with the cursor on a field
     * and the tray down; nothing else (a button, a check running, Android's keyboard).
     */
    val textEntry: TextEntry get() = typing?.textEntry ?: TextEntry.None

    private val SignInItem.isField: Boolean get() = this == SignInItem.Username || this == SignInItem.Password

    /** The keys and caret of [item]'s field (the username's for an item that is not a field). */
    fun keysOf(item: SignInItem): FieldKeys = if (item == SignInItem.Password) passwordKeys else usernameKeys

    /** What the action key made of the last key (Sign in's submit); read once by [ownKeyboardStep]. */
    private var keyStep: SignInStep? = null

    fun onUsernameChange(text: String) {
        if (isChecking) return
        username = text
        // A change from outside Plumemu's keyboard (Android's field, a test) leaves its caret at the end; its own edits set it after.
        usernameKeys.caret = text.length
        message = null
    }

    fun onPasswordChange(text: String) {
        if (isChecking) return
        password = text
        passwordKeys.caret = text.length
        message = null
    }

    fun onAction(action: Action): SignInStep {
        // Plumemu's keyboard first: while its tray is up it takes every action (Back puts it away).
        typing?.let { keys -> ownKeyboardStep { keys.onAction(action) }?.let { return it } }
        if (action == Action.Back) return SignInStep.NotHandled
        if (isChecking) return SignInStep.Stay()
        return when (action) {
            is Action.Move -> SignInStep.Stay(move(action.direction))
            Action.Enter -> activate(focus)
            is Action.TapItem -> tap(action.index)
            else -> SignInStep.NotHandled
        }
    }

    /** A tap on item [index] (fields: start typing there; buttons: press). */
    fun tap(index: Int): SignInStep {
        if (isChecking) return SignInStep.Stay()
        val item = items.getOrNull(index) ?: return SignInStep.Stay()
        val moved = item != focus
        focus = item
        if (!item.isField) keyboard.stop()
        return if (item.isField) {
            askForKeyboard()
            SignInStep.Stay(moved = moved)
        } else {
            activate(item)
        }
    }

    /** A tap on a key of Plumemu's tray: what A on that key would do (nothing while the check runs). */
    fun onKeyboardKey(event: KeyboardEvent): SignInStep {
        val keys = typing ?: return SignInStep.Stay()
        if (!isOnField) return SignInStep.Stay()
        return ownKeyboardStep { keys.onKeyboardEvent(event) } ?: SignInStep.Stay()
    }

    /**
     * A tap on [item]'s line at character [offset] with Plumemu's keyboard: the caret goes there;
     * the tap itself reaches the host as a tap on the item ([tap]), which focuses it and brings the tray up.
     */
    fun placeCaret(
        item: SignInItem,
        offset: Int,
    ) {
        if (isChecking || !item.isField) return
        val text = if (item == SignInItem.Password) password else username
        keysOf(item).caret = offset.coerceIn(0, text.length)
    }

    /**
     * An action or key for Plumemu's keyboard ([SignInTyping]); null when it is the form's own (the
     * cursor on a button, the tray down and not a typed key). A move of the cursor or of the keys is
     * a move; the action key may have submitted.
     */
    private inline fun ownKeyboardStep(outcomeOf: () -> TypingOutcome): SignInStep? {
        val before = focus
        val key = keysOf(before).keys.focus
        keyStep = null
        if (outcomeOf() == TypingOutcome.NotHandled) return null
        val step = keyStep
        keyStep = null
        return step ?: SignInStep.Stay(moved = focus != before || keysOf(focus).keys.focus != key)
    }

    /**
     * Plumemu's action key (the tray's, START, a hardware Enter): "Next" on the username goes to the
     * password (the tray stays up when it was up; a hardware keyboard's Enter with the tray down
     * keeps it down), "Sign in" on the password presses Sign in once, as Android's Done does.
     */
    internal fun onOwnKeyboardAction() {
        when (focus) {
            SignInItem.Username ->
                if (isTyping) {
                    onKeyboardNext()
                } else if (!isChecking) {
                    focus = SignInItem.Password
                }
            SignInItem.Password -> {
                onKeyboardDone()
                if (focus == SignInItem.SignIn) keyStep = submit()
            }
            else -> Unit
        }
    }

    /** The keyboard's Next on the username: the password, with its keyboard. */
    fun onKeyboardNext() {
        if (isChecking || focus != SignInItem.Username) return
        focus = SignInItem.Password
        askForKeyboard()
    }

    /**
     * The keyboard's Done on the password: the cursor goes to Sign in and the keyboard down; the
     * host then sends the Enter that presses it (through its own path, like the A button).
     */
    fun onKeyboardDone() {
        if (isChecking || focus != SignInItem.Password) return
        focus = SignInItem.SignIn
        keyboard.stop()
    }

    /** The keyboard goes down and the cursor stays (the host closed the form). */
    fun stopTyping() {
        keyboard.stop()
    }

    private fun askForKeyboard() {
        keyboard.ask()
    }

    /** The graph's answer to the last [SignInStep.Submit]. */
    fun showResult(result: SignInResult) {
        isChecking = false
        // The answer never brings the keyboard back by itself.
        keyboard.stop()
        if (result is SignInResult.SignedIn) {
            signedInAs = result.username
            password = ""
            message = null
            focus = SignInItem.Continue
            return
        }
        message = result
        // A wrong name or password: back to the password, ready to type it again (A opens its keyboard).
        focus = if (result == SignInResult.WrongCredentials) SignInItem.Password else SignInItem.SignIn
    }

    private fun move(direction: Direction): Boolean {
        val next =
            when (focus) {
                SignInItem.Username -> if (direction == Direction.Down) SignInItem.Password else null
                SignInItem.Password ->
                    when (direction) {
                        Direction.Up -> SignInItem.Username
                        Direction.Down -> SignInItem.SignIn
                        else -> null
                    }
                SignInItem.SignIn ->
                    when (direction) {
                        Direction.Up -> SignInItem.Password
                        Direction.Right -> SignInItem.Leave.takeUnless { stackedButtons }
                        Direction.Down -> SignInItem.Leave.takeIf { stackedButtons }
                        Direction.Left -> null
                    }
                SignInItem.Leave ->
                    when (direction) {
                        Direction.Up -> if (stackedButtons) SignInItem.SignIn else SignInItem.Password
                        Direction.Left -> SignInItem.SignIn.takeUnless { stackedButtons }
                        else -> null
                    }
                SignInItem.Continue -> null
            } ?: return false
        focus = next
        keyboard.stop()
        return true
    }

    private fun activate(item: SignInItem): SignInStep =
        when (item) {
            SignInItem.Username, SignInItem.Password -> {
                askForKeyboard()
                SignInStep.Stay()
            }
            SignInItem.SignIn -> submit()
            SignInItem.Leave -> SignInStep.Leave
            SignInItem.Continue -> SignInStep.Done
        }

    private fun submit(): SignInStep {
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) {
            message = SignInResult.Missing
            focus = if (name.isEmpty()) SignInItem.Username else SignInItem.Password
            keyboard.stop()
            return SignInStep.Stay(moved = true)
        }
        isChecking = true
        keyboard.stop()
        message = null
        return SignInStep.Submit(name, password)
    }
}
