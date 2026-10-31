package com.plumemu.settings.screen

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
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.TitleList
import com.plumemu.home.HomeTags
import com.plumemu.home.ImeProbe
import com.plumemu.home.appComposeRule
import com.plumemu.home.pressButton
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule

/**
 * Settings on the real screen ([SettingsScreen]) with a real router fed real key events, real
 * touches and a hardware keyboard, for its fields typed with Plumemu's keyboard:
 * the hex code of a custom colour and Library › Unidentified games › Fix name…. [shown] takes
 * Settings out of the composition, as B out of it or the Home button do.
 */
internal abstract class SettingsKeyboardPathHarness : SettingsModelHarness() {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected lateinit var router: InputRouter
    protected val ime = ImeProbe(compose)
    protected var confirm = KeyEvent.KEYCODE_BUTTON_A
    protected var back = KeyEvent.KEYCODE_BUTTON_B
    protected var shown by mutableStateOf(true)

    protected val unidentified =
        listOf(
            SettingsGame(21, "zork quest 2", "Game Boy Advance"),
            SettingsGame(22, "galfn island", "Super Nintendo"),
            SettingsGame(23, "dravon", "Test System"),
        )
    protected val titles =
        listOf(
            TitleChoice("Zork Quest II", "USA", 1993, "Test DAT"),
            TitleChoice("Galfen Island", "", 1995, "Test DAT"),
        )

    /** Unidentified games' graph side: the lists (game 23's system has none) and what was stored. */
    protected class Names(
        private val titles: List<TitleChoice>,
    ) : SettingsNameActions {
        val chosen = mutableListOf<Pair<Long, TitleChoice>>()
        val own = mutableListOf<Pair<Long, String>>()

        override fun titleList(
            gameId: Long,
            onDone: (TitleList?) -> Unit,
        ) = onDone(if (gameId == 23L) TitleList.NoList("Test System") else TitleList.Ready(TitleFinder(titles)))

        override fun setChosenTitle(
            gameId: Long,
            choice: TitleChoice,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) {
            chosen += gameId to choice
            onDone(true, null)
        }

        override fun setOwnName(
            gameId: Long,
            name: String,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) {
            own += gameId to name
            onDone(true, null)
        }
    }

    protected val names = Names(titles)

    /** What Settings shows: the unidentified games (and, for the sign-in's tests, an account state). */
    protected open val content: SettingsContent get() = SettingsContent(unidentifiedGames = unidentified)

    /** Settings on its sections, with Plumemu's keyboard unless [androidKeyboard]; [swapAB]: "Confirm with B". */
    protected fun show(
        swapAB: Boolean = false,
        androidKeyboard: Boolean = false,
    ) {
        if (androidKeyboard) useAndroidKeyboard()
        actions.nameActions = names
        confirm = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        back = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        val settings = actions.shown
        val shownContent = content
        // Typed as the interface: the Compose compiler gives the large FakeActions no `$stable`
        // field, and a composable call typed FakeActions reads it (NoSuchFieldError). Through a
        // function: a local val typed SettingsActions still compiled to a read of FakeActions'
        // `$stable` once Settings › Appearance gained Colors.
        val screenActions = asSettingsActions(actions)
        compose.setContent {
            ime.view = LocalView.current
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, InputSettings(swapAB = swapAB), onDebug = {}) }
            PlumTheme { if (shown) SettingsScreen(settings, router, screenActions, versionName = TEST_VERSION, content = shownContent) }
        }
        compose.waitForIdle()
    }

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

    protected fun typeOnKeyboard(text: String) = text.forEach { hardware(KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }

    /** B held for [frames] frames on the router's own repeat, then released. */
    protected fun holdB(frames: Int = HELD_FRAMES) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)) }
        repeat(frames) {
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)) }
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

    protected val isTrayUp get() = exists(SettingsTags.TRAY)

    protected fun textEntry(): TextEntry = compose.runOnIdle { router.textEntry }

    /** Appearance › Accent › "+": the accent palette, the cursor on its hex row (the stored accent is a preset). */
    protected fun openAccentHexRow() {
        repeat(SettingsSection.Appearance.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Appearance.rowsIn(content).indexOf(SettingsOption.Accent)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(6) { send(Action.Move(Direction.Right)) }
        send(Action.Move(Direction.Down), Action.Enter)
        repeat(7) { send(Action.Move(Direction.Down)) } // the hex code is the last row, below the swatches
        assertTrue("on the accent palette's hex row", exists(SettingsTags.HEX_ROW))
    }

    /** Library › Unidentified games, then A on game [index] (0: Fix name… with a list; 2: Use my own name, no list). */
    protected fun openNameFix(index: Int) {
        repeat(SettingsSection.Library.ordinal) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.UnidentifiedGames)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(index) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
    }

    protected companion object {
        const val TIMEOUT_MS = 5_000L

        /** The virtual keyboard's device id: its key map types like a full keyboard. */
        const val VIRTUAL_KEYBOARD = -1

        /** Two seconds at 60 Hz: enough repeats to erase a name and more. */
        const val HELD_FRAMES = 120

        const val IDLE_MS = 10_000L
    }
}

/** [actions] seen only as [SettingsActions], so no composable call is compiled against the fake's class. */
private fun asSettingsActions(actions: SettingsActions): SettingsActions = actions
