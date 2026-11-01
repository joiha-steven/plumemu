package com.plumemu.settings.screen

import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.home.HomeTags
import com.plumemu.keyboard.ClipSource
import com.plumemu.keyboard.PasteBoard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pasting with Plumemu's keyboard, on the real Settings screen with a real router: the paste key and
 * the "R3 Paste" hint only while the clipboard holds text, R3 and a tap on the key, at the caret, one
 * line, only what fits (the hex code's seven characters), and the password: dots only, the pasted
 * text nowhere on screen. The clipboard is read only when the person pastes. Clipboard values here
 * are made up and never appear in a test name or a failure message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsPasteTest : SettingsKeyboardPathHarness() {
    override val content: SettingsContent get() = SettingsContent(account = AccountStatus.SignedOut)

    /** The clipboard as a test sets it; counts how often the text itself was read. */
    private class FakeClip : ClipSource {
        var reads = 0
        private var onChange: () -> Unit = {}
        var clip: String? = null
            set(value) {
                field = value
                onChange()
            }

        override fun hasText(): Boolean = !clip.isNullOrEmpty()

        override fun text(): String? {
            reads++
            return clip
        }

        override fun watch(onChange: () -> Unit) {
            this.onChange = onChange
        }
    }

    private val clipboard = FakeClip()

    @Before
    fun installTheClipboard() = PasteBoard.install(clipboard)

    @After
    fun emptyTheClipboard() = PasteBoard.install(FakeClip())

    private fun setClip(text: String?) {
        compose.runOnIdle { clipboard.clip = text }
        compose.waitForIdle()
    }

    private val username get() = textOf(SignInTags.typedText(SignInItem.Username))

    private val hasPasteKey get() = compose.onAllNodesWithContentDescription(PASTE).fetchSemanticsNodes().isNotEmpty()

    private val hasPasteHint get() = exists(HomeTags.hint(Action.Paste))

    /** Accounts › ScreenScraper and A: the sign-in form opens with the tray up on the username. */
    private fun typeInTheUsername() {
        show()
        repeat(SettingsSection.shownIn(content).indexOf(SettingsSection.Accounts)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.ScraperAccount)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertTrue("the tray is up", isTrayUp)
    }

    @Test
    fun anEmptyClipboardShowsNoPasteKeyAndNoHint() {
        typeInTheUsername()
        assertFalse("no paste key", hasPasteKey)
        assertFalse("no R3 hint", hasPasteHint)
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("R3 with nothing to paste types nothing", "", username)
        assertEquals("nothing read from the clipboard", 0, clipboard.reads)
    }

    @Test
    fun theKeyAndTheHintFollowTheClipboardAndNothingIsReadUntilAPaste() {
        typeInTheUsername()
        setClip("plum")
        assertTrue("the paste key comes", hasPasteKey)
        assertTrue("the hint comes", hasPasteHint)
        assertTrue("the hint says Paste", hintText(Action.Paste).endsWith(PASTE))
        assertEquals("showing the key reads no text", 0, clipboard.reads)
        setClip(null)
        assertFalse("the key goes with the text", hasPasteKey)
        assertFalse(hasPasteHint)
    }

    @Test
    fun r3PastesAtTheCaretInTheMiddleOfTheText() {
        setClip("XY")
        typeInTheUsername()
        typeOnKeyboard("plum")
        press(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1)
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("plXYum", username)
        typeOnKeyboard("z")
        assertEquals("the caret is after the pasted text", "plXYzum", username)
        assertEquals(1, clipboard.reads)
    }

    @Test
    fun aTapOnThePasteKeyAndTheHintPasteToo() {
        setClip("ab")
        typeInTheUsername()
        compose.onNodeWithContentDescription(PASTE).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("ab", username)
        tap(HomeTags.hint(Action.Paste))
        assertEquals("abab", username)
    }

    @Test
    fun aMultiLinePasteBecomesOneLine() {
        setClip("plum\ntester\n")
        typeInTheUsername()
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("plum tester", username)
    }

    @Test
    fun thePasswordShowsOnlyDotsAndThePastedTextIsNowhere() {
        val secret = "made-up-key-1234"
        setClip(secret)
        typeInTheUsername()
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue("Next: typing in the password", hintText(Action.Menu).endsWith("Sign in"))
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("one dot per character", "•".repeat(secret.length), textOf(SignInTags.typedText(SignInItem.Password)))
        assertTrue("the pasted text shows nowhere", compose.onAllNodes(hasText("made-up", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aPastePastTheLimitKeepsWhatFits() {
        show()
        openAccentHexRow()
        press(confirm)
        assertTrue("the hex tray is up", isTrayUp)
        holdB()
        if (!isTrayUp) press(confirm)
        assertEquals("the field starts empty", "", textOf(SettingsTags.HEX_TEXT))
        setClip("#12345678\n")
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("seven characters: what fits", "#123456", textOf(SettingsTags.HEX_TEXT))
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("a full field takes nothing more", "#123456", textOf(SettingsTags.HEX_TEXT))
    }

    @Test
    fun aPasteInTheMiddleOfANearlyFullFieldKeepsTheTextAfterTheCaret() {
        show()
        openAccentHexRow()
        press(confirm)
        holdB()
        if (!isTrayUp) press(confirm)
        setClip("12345")
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("12345", textOf(SettingsTags.HEX_TEXT))
        press(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1)
        setClip("ABCDEF")
        press(KeyEvent.KEYCODE_BUTTON_THUMBR)
        assertEquals("two characters fit; the text after the caret stays", "12AB345", textOf(SettingsTags.HEX_TEXT))
    }

    private companion object {
        const val PASTE = "Paste"
    }
}
