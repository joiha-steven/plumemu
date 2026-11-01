package com.plumemu.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.plumemu.coreinput.Column as StripColumn

/**
 * A long press on a field's line offers Android's Paste (only Paste), only while the clipboard holds text,
 * pastes only into the line pressed, and the menu goes when typing moves to another line or the gamepad
 * is used. Two lines share one toolbar, as on the sign-in form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class CaretLinePasteTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Android's floating toolbar as a test sees it: what it was asked to offer. */
    private class FakeToolbar : TextToolbar {
        var paste: (() -> Unit)? = null
        var others = 0
        override var status = TextToolbarStatus.Hidden

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {
            paste = onPasteRequested
            others = listOfNotNull(onCopyRequested, onCutRequested, onSelectAllRequested).size
            status = TextToolbarStatus.Shown
        }

        override fun hide() {
            status = TextToolbarStatus.Hidden
        }
    }

    private class Clip(private val has: Boolean) : ClipSource {
        override fun hasText() = has

        override fun text(): String? = null

        override fun watch(onChange: () -> Unit) = Unit
    }

    private val toolbar = FakeToolbar()

    /** The line typing is in: "a" or "b". */
    private val typingIn = mutableStateOf("a")
    private val pastes = mutableListOf<String>()
    private var taps = 0
    private lateinit var router: InputRouter

    @After
    fun emptyTheClipboard() = PasteBoard.install(Clip(has = false))

    private fun showLines() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            ClosePasteMenuOnActions(router)
            PlumTheme {
                CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                    Column {
                        for (name in listOf("a", "b")) {
                            CaretLine(
                                text = "plum",
                                caret = { 4 },
                                showsCaret = { typingIn.value == name },
                                placeholder = "",
                                style = PlumTheme.type.topBar,
                                onTapAt = {
                                    taps++
                                    typingIn.value = name
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textTag = "$name-text",
                                placeholderTag = "$name-placeholder",
                                onPaste = { pastes += name },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun longPress(name: String) {
        compose.onNodeWithTag("$name-text", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    private fun send(action: Action) {
        compose.runOnIdle { router.send(action) }
        compose.waitForIdle()
    }

    @Test
    fun aLongPressOffersPasteOnlyAndPastes() {
        PasteBoard.install(Clip(has = true))
        showLines()
        longPress("a")
        assertEquals("the long press places the caret", 1, taps)
        assertNotNull("Paste is offered", toolbar.paste)
        val paste = toolbar.paste!!
        assertEquals("nothing but Paste", 0, toolbar.others)
        compose.runOnIdle { paste() }
        assertEquals(listOf("a"), pastes)
        assertEquals("the menu goes after a paste", TextToolbarStatus.Hidden, toolbar.status)
    }

    @Test
    fun withNothingToPasteALongPressOffersNothing() {
        PasteBoard.install(Clip(has = false))
        showLines()
        longPress("a")
        assertNull("no menu", toolbar.paste)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertEquals(emptyList<String>(), pastes)
    }

    @Test
    fun movingToAnotherFieldClosesTheMenuAndAStalePasteDoesNothing() {
        PasteBoard.install(Clip(has = true))
        showLines()
        longPress("a")
        val paste = toolbar.paste!!
        compose.runOnIdle { typingIn.value = "b" }
        compose.waitForIdle()
        assertEquals("the menu goes when the cursor leaves the line", TextToolbarStatus.Hidden, toolbar.status)
        compose.runOnIdle { paste() }
        assertEquals("nothing pasted into another field", emptyList<String>(), pastes)
    }

    @Test
    fun aGamepadActionClosesTheMenuATapDoesNot() {
        PasteBoard.install(Clip(has = true))
        showLines()
        longPress("a")
        send(Action.TapItem(StripColumn.Games, 0))
        assertEquals("a tap reaches the menu or its field itself", TextToolbarStatus.Shown, toolbar.status)
        send(Action.Move(Direction.Down))
        assertEquals("the gamepad closes it", TextToolbarStatus.Hidden, toolbar.status)
    }

    @Test
    fun aMenuClosedByTheGamepadNeverClosesTheNextLinesMenu() {
        PasteBoard.install(Clip(has = true))
        showLines()
        longPress("a")
        send(Action.Move(Direction.Right))
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        longPress("b")
        assertEquals("b's menu stays while typing leaves a", TextToolbarStatus.Shown, toolbar.status)
        compose.runOnIdle { toolbar.paste!!() }
        assertEquals(listOf("b"), pastes)
    }
}
