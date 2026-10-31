package com.plumemu.setup

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.account.FakeScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.HomeTags
import com.plumemu.home.ImeProbe
import com.plumemu.home.appComposeRule
import com.plumemu.home.pressButton
import com.plumemu.home.rulesAround
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ButtonLetters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule

/**
 * Setup on the real screen ([SetupScreen]) with a real router fed real key events, real touches and
 * a hardware keyboard, on the box art step's sign-in form typed with Plumemu's keyboard. [shown] takes setup out of the composition, as completing it or the Activity going do.
 */
internal abstract class SetupKeyboardPathHarness {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected val accounts = FakeScraperAccounts()
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected val model = SetupModel(modelScope, FakeSetupSource(), accounts) { }
    protected lateinit var router: InputRouter
    protected val ime = ImeProbe(compose)
    protected var confirm = KeyEvent.KEYCODE_BUTTON_A
    protected var back = KeyEvent.KEYCODE_BUTTON_B
    protected var shown by mutableStateOf(true)

    @After
    fun stopModel() = modelScope.cancel()

    /** Setup with one folder, Continue, then Use my account: the form, the cursor on the username. */
    protected fun showTheForm(
        swapAB: Boolean = false,
        keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    ) {
        confirm = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        back = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        compose.setContent {
            ime.view = LocalView.current
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, InputSettings(swapAB = swapAB), onDebug = {}) }
            PlumTheme { if (shown) SetupScreen(model, router, ButtonLetters.Default, onOpenFolderPicker = {}, keyboard = keyboard) }
        }
        compose.runOnIdle { model.onPicked(PickResult.Picked(SetupFakes.ROMS_TREE)) }
        compose.waitForIdle()
        press(confirm, confirm)
        assertNotNull("the box art step shows its form", model.state.value.boxArt)
    }

    protected val form: SignInForm get() = requireNotNull(model.state.value.boxArt) { "the form is shown" }

    protected fun send(vararg sent: Action) =
        sent.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    protected fun press(vararg keyCodes: Int) =
        keyCodes.forEach { code ->
            compose.runOnIdle { router.pressButton(code) }
            compose.waitForIdle()
        }

    protected fun hardware(code: Int) {
        compose.runOnIdle {
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, 0, VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, code, 0, 0, VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        compose.waitForIdle()
    }

    /** Letters and digits on a hardware keyboard (lower case). */
    protected fun typeOnKeyboard(text: String) = text.forEach { hardware(KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }

    /** B held for [frames] frames on the router's own repeat, then released. */
    protected fun holdB(frames: Int = HELD_FRAMES) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, back)) }
        repeat(frames) {
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, back)) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    protected fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    protected fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun textOf(tag: String): String =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()

    protected fun hintText(action: Action): String =
        compose.onNodeWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNode().let { node ->
            (listOf(node) + node.children).mapNotNull { it.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }.joinToString(" ")
        }

    protected val isTrayUp get() = exists(SetupTags.TRAY)

    protected fun textEntry(): TextEntry = compose.runOnIdle { router.textEntry }

    protected fun assertNoTray(where: String) = assertFalse("no tray $where", isTrayUp)

    protected companion object {
        /** The virtual keyboard's device id: its key map types like a full keyboard. */
        const val VIRTUAL_KEYBOARD = -1

        /** Two seconds at 60 Hz: enough repeats to erase a name and more. */
        const val HELD_FRAMES = 120

        const val IDLE_MS = 10_000L

        val USERNAME_FIELD = SignInTags.USERNAME
        val PASSWORD_FIELD = SignInTags.PASSWORD
    }
}
