package com.plumemu.report

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
import com.plumemu.telemetry.EmailProblem
import com.plumemu.telemetry.ProblemReports
import com.plumemu.telemetry.ReportResult

/** What the Report a problem form can focus, top to bottom; Send and [Leave] are stacked. */
internal enum class ReportItem {
    Text,
    Email,

    /** "Include the log and device details": A or a tap switches it. */
    Details,
    Send,

    /** Cancel. */
    Leave,

    /** After a report was sent or saved: the only item. */
    Done,
}

/** Why Send did nothing. */
internal enum class ReportMistake { NoText, BadEmail, EmailTooLong }

/** What the host must do after the form handled an action. */
internal sealed interface ReportStep {
    /** Handled inside the form; [moved]: the focus went to another item (for its sound). */
    data class Stay(val moved: Boolean = false) : ReportStep

    /** The person pressed Send with a good report; the form now shows "Sending…". */
    data class Submit(val text: String, val email: String?, val includeDetails: Boolean) : ReportStep {
        // Never in a log.
        override fun toString(): String = "Submit(text=***, email=***, includeDetails=$includeDetails)"
    }

    /** Cancel or Done: leave the form. */
    data object Leave : ReportStep

    /** Not the form's (Back, START): the host's own handling applies. */
    data object NotHandled : ReportStep
}

/**
 * Settings › About › Report a problem: a text field, an optional e-mail field, the switch
 * "Include the log and device details" (on), Send and Cancel, and one message line. For both
 * inputs: Up / Down move between the items, A or a tap on a field opens its keyboard
 * (moving off a field puts it away), A or a tap on the switch flips it. The keyboard's Next goes
 * from the text to the e-mail; its Done moves to Send without pressing it, so a report is never
 * sent by a stray key. While the report is being sent only Back is the host's. The text stops at
 * [ProblemReports.MAX_TEXT_CHARS]. Main thread only.
 *
 * [keyboardKind] is Settings › Controls › Keyboard when the form opened: with Plumemu's the
 * fields are typed through [typing] (the host draws the tray), with Android's through its text
 * fields. Android's by default, so a host without a tray never gets a field nobody can type into.
 */
@Stable
internal class ProblemReportForm(
    val keyboardKind: KeyboardChoice = KeyboardChoice.Android,
) {
    var text: String by mutableStateOf("")
        private set

    var email: String by mutableStateOf("")
        private set

    var includeDetails: Boolean by mutableStateOf(true)
        private set

    var focus: ReportItem by mutableStateOf(ReportItem.Text)
        private set

    /** The report is on its way: the fields and buttons wait. */
    var isSending: Boolean by mutableStateOf(false)
        private set

    /** What the last Send ended in; null before one and while one runs. */
    var result: ReportResult? by mutableStateOf(null)
        private set

    /** Why Send did nothing; cleared by the next edit. */
    var mistake: ReportMistake? by mutableStateOf(null)
        private set

    /** The report was sent or saved: the form shows only that and Done. */
    val isFinished: Boolean get() = result == ReportResult.Sent || result == ReportResult.Saved

    /** The text has reached its longest. */
    val isAtLimit: Boolean get() = text.length >= ProblemReports.MAX_TEXT_CHARS

    /** The items in screen order; a tap on the n-th is item n. */
    val items: List<ReportItem>
        get() = if (isFinished) listOf(ReportItem.Done) else listOf(ReportItem.Text, ReportItem.Email, ReportItem.Details, ReportItem.Send, ReportItem.Leave)

    /** Whether the person asked to type, as in the sign-in form ([KeyboardAsk]). */
    val keyboard = KeyboardAsk()

    val keyboardAsks: Int get() = keyboard.asks

    val isTyping: Boolean get() = keyboard.isTyping

    /** The cursor is on a text field and the person asked to type there: its keyboard is up. */
    val inField: Boolean get() = !isSending && isTyping && focus.isField

    /** The cursor is on a text field (typing or not) and nothing is being sent. */
    val isOnField: Boolean get() = !isSending && focus.isField

    val textKeys = FieldKeys("", capitalizesFirst = true)

    val emailKeys = FieldKeys("", capitalizesFirst = false)

    /** The fields as Plumemu's keyboard types into them; null with Android's keyboard. */
    val typing: ReportTyping? = if (keyboardKind == KeyboardChoice.Plumemu) ReportTyping(this) else null

    /** Plumemu's tray is up over the form (never with Android's keyboard). */
    val isTrayUp: Boolean get() = typing != null && inField

    /** What the host's input router must send now. */
    val textEntry: TextEntry get() = typing?.textEntry ?: TextEntry.None

    private val ReportItem.isField: Boolean get() = this == ReportItem.Text || this == ReportItem.Email

    fun keysOf(item: ReportItem): FieldKeys = if (item == ReportItem.Email) emailKeys else textKeys

    /** What the action key made of the last key; read once by [ownKeyboardStep]. */
    private var keyStep: ReportStep? = null

    fun onTextChange(changed: String) {
        if (isSending) return
        // A paste past the limit keeps what fits (the line "as long as it can be" then shows); a key typed
        // into a full text is refused, so it does not push the end out.
        if (changed.length > ProblemReports.MAX_TEXT_CHARS && text.length >= ProblemReports.MAX_TEXT_CHARS) return
        text = changed.take(ProblemReports.MAX_TEXT_CHARS)
        textKeys.caret = text.length
        mistake = null
    }

    fun onEmailChange(changed: String) {
        if (isSending) return
        email = changed.take(ProblemReports.MAX_EMAIL_CHARS + 1)
        emailKeys.caret = email.length
        mistake = null
    }

    fun onAction(action: Action): ReportStep {
        typing?.let { keys -> ownKeyboardStep { keys.onAction(action) }?.let { return it } }
        if (action == Action.Back) return ReportStep.NotHandled
        if (isSending) return ReportStep.Stay()
        return when (action) {
            is Action.Move -> ReportStep.Stay(move(action.direction))
            Action.Enter -> activate(focus)
            is Action.TapItem -> tap(action.index)
            else -> ReportStep.NotHandled
        }
    }

    /** A tap on item [index] (fields: start typing there; the switch: flip it; buttons: press). */
    fun tap(index: Int): ReportStep {
        if (isSending) return ReportStep.Stay()
        val item = items.getOrNull(index) ?: return ReportStep.Stay()
        val moved = item != focus
        focus = item
        if (!item.isField) keyboard.stop()
        return if (item.isField) {
            keyboard.ask()
            ReportStep.Stay(moved = moved)
        } else {
            activate(item)
        }
    }

    /** A tap on a key of Plumemu's tray: what A on that key would do. */
    fun onKeyboardKey(event: KeyboardEvent): ReportStep {
        val keys = typing ?: return ReportStep.Stay()
        if (!isOnField) return ReportStep.Stay()
        return ownKeyboardStep { keys.onKeyboardEvent(event) } ?: ReportStep.Stay()
    }

    /** A tap on [item]'s line at character [offset] with Plumemu's keyboard: the caret goes there. */
    fun placeCaret(
        item: ReportItem,
        offset: Int,
    ) {
        if (isSending || !item.isField) return
        keysOf(item).caret = offset.coerceIn(0, (if (item == ReportItem.Email) email else text).length)
    }

    private inline fun ownKeyboardStep(outcomeOf: () -> TypingOutcome): ReportStep? {
        val before = focus
        val key = keysOf(before).keys.focus
        keyStep = null
        if (outcomeOf() == TypingOutcome.NotHandled) return null
        val step = keyStep
        keyStep = null
        return step ?: ReportStep.Stay(moved = focus != before || keysOf(focus).keys.focus != key)
    }

    /**
     * Plumemu's action key (the tray's, START, a hardware Enter): "Next" on the text goes to the
     * e-mail (the tray stays up when it was up), "Done" on the e-mail goes to Send, which is not pressed.
     */
    internal fun onOwnKeyboardAction() {
        when (focus) {
            ReportItem.Text ->
                if (isTyping) {
                    onKeyboardNext()
                } else if (!isSending) {
                    focus = ReportItem.Email
                }
            ReportItem.Email -> onKeyboardDone()
            else -> Unit
        }
    }

    /** The keyboard's Next on the text: the e-mail, with its keyboard. */
    fun onKeyboardNext() {
        if (isSending || focus != ReportItem.Text) return
        focus = ReportItem.Email
        keyboard.ask()
    }

    /** The keyboard's Done on the e-mail: the cursor goes to Send and the keyboard down. */
    fun onKeyboardDone() {
        if (isSending || focus != ReportItem.Email) return
        focus = ReportItem.Send
        keyboard.stop()
    }

    /** The keyboard goes down and the cursor stays (the host closed the form). */
    fun stopTyping() {
        keyboard.stop()
    }

    /** The graph's answer to the last [ReportStep.Submit]. */
    fun showResult(answer: ReportResult) {
        isSending = false
        keyboard.stop()
        result = answer
        when (answer) {
            ReportResult.Sent, ReportResult.Saved -> {
                // The words are gone from the device's memory too; a new report starts empty.
                text = ""
                email = ""
                mistake = null
                focus = ReportItem.Done
            }
            ReportResult.Empty -> {
                result = null
                mistake = ReportMistake.NoText
                focus = ReportItem.Text
            }
            ReportResult.Refused, ReportResult.NotSaved, ReportResult.Unavailable -> focus = ReportItem.Send
        }
    }

    private fun move(direction: Direction): Boolean {
        val all = items
        val at = all.indexOf(focus)
        val next =
            when (direction) {
                Direction.Down -> all.getOrNull(at + 1)
                Direction.Up -> all.getOrNull(at - 1)
                else -> null
            } ?: return false
        focus = next
        keyboard.stop()
        return true
    }

    private fun activate(item: ReportItem): ReportStep =
        when (item) {
            ReportItem.Text, ReportItem.Email -> {
                keyboard.ask()
                ReportStep.Stay()
            }
            ReportItem.Details -> {
                includeDetails = !includeDetails
                ReportStep.Stay()
            }
            ReportItem.Send -> submit()
            ReportItem.Leave, ReportItem.Done -> ReportStep.Leave
        }

    private fun submit(): ReportStep {
        val words = text.trim()
        val problem = ProblemReports.emailProblem(email)
        val found =
            when {
                words.isEmpty() -> ReportMistake.NoText
                problem == EmailProblem.TooLong -> ReportMistake.EmailTooLong
                problem != null -> ReportMistake.BadEmail
                else -> null
            }
        if (found != null) {
            mistake = found
            focus = if (found == ReportMistake.NoText) ReportItem.Text else ReportItem.Email
            keyboard.stop()
            return ReportStep.Stay(moved = true)
        }
        isSending = true
        keyboard.stop()
        mistake = null
        result = null
        return ReportStep.Submit(words, email.trim().ifEmpty { null }, includeDetails)
    }
}
