package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.game.FieldKeys
import com.plumemu.game.KeyboardAsk
import com.plumemu.game.TypingOutcome
import com.plumemu.game.TypingState
import com.plumemu.game.ownKeyboardOutcome
import com.plumemu.keyboard.KeyboardChoice

/**
 * The custom colour's hex field seen as a panel field, so Plumemu's keyboard
 * types into it with the game panel's own button map ([ownKeyboardOutcome]): the tray opens on the
 * digits without shift (the code is read without regard to case, [parseHexColor]); its action key,
 * START and a hardware Enter use the code as "Use this colour" does ([use]: applied and the tray
 * down, or the wrong-code line with the tray kept up). One per editing (A on the hex row), made by
 * [SettingsCustomColor.startEditingHex] with the keyboard Settings › Controls › Keyboard named then.
 * What is not the keyboard's (A, the D-pad and B with the tray down) stays [SettingsModel]'s, the
 * same rules as with Android's keyboard: [onAction] hands it back as [TypingOutcome.NotHandled].
 * Main thread only.
 */
@Stable
internal class HexTyping(
    private val custom: SettingsCustomColor,
    override val keyboardKind: KeyboardChoice,
    private val use: (String) -> Unit,
) : TypingState {
    /** On the field while editing; on "Use this colour" ([SettingsCustomColor.hexOnUse]) or not editing: not. */
    override val inField: Boolean get() = custom.isEditingHex && !custom.hexOnUse

    override val keyboard: KeyboardAsk get() = custom.hexKeyboard

    override val fieldText: String get() = custom.hexText

    override fun replaceText(text: String) = custom.onHexTextChange(text)

    override val fieldKeys = FieldKeys("", hasShift = false, startPage = KeyboardPage.Symbols)

    /**
     * How many times the action key tried the code (plain, not state: nothing draws from it). A
     * wrong code changes nothing else, yet the press must be heard as "Use this colour"'s is.
     */
    var tries = 0
        private set

    /** The action key: the code is used as typed, as "Use this colour" would. */
    override fun onKeyboardAction(): Action? {
        tries++
        use(custom.hexText)
        return null
    }

    override val cursor: Int get() = if (custom.hexOnUse) USE_ROW else -1

    override fun focusField() = custom.askHexKeyboard()

    override fun onAction(action: Action): TypingOutcome = ownKeyboardOutcome(action) ?: TypingOutcome.NotHandled

    private companion object {
        /** "Use this colour", the one row under the field. */
        const val USE_ROW = 0
    }
}
