package com.plumemu.settings.screen

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' sections column (seven sections do not fit at Largest on 768x432) and its options column (Emulators, a row per system)
 * while Up or Down is pressed or held, frame by frame on the test clock through the real repeat path:
 * every frame shows the focused section whole and the section past it whole. Before the fix the
 * column scrolled from a coroutine after the frame and slid for 180 ms, leaving the last
 * section 18.5 dp under the hint bar for ten frames after each press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsScrollFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private companion object {
        /** Rounding of px positions, in dp. */
        const val SLACK = 0.25f

        /** About 2 s held at the test clock's 16 ms frames: enough to cross 25 rows. */
        const val HELD_FRAMES = 130
        const val SYSTEMS = 25
    }

    private lateinit var router: InputRouter
    /** Moves the router sent, as state: written beside the screen's own dispatch of them. */
    private val sent = mutableIntStateOf(0)

    /** [sent] as of the last composition: the moves the frame drawn after it shows. */
    private var moves = 0

    /** Recomposes with the screen when a move arrives, alone: what it records is what was composed. */
    @Composable
    private fun MovesProbe() {
        val seen = sent.intValue
        SideEffect { moves = seen }
    }

    private val settings =
        PlumSettings(flags = SetupFlags(setupCompleted = true, homeRoleAsked = true), appearance = AppearanceSettings(textSize = TextSizeSetting.Largest))

    private fun key(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun gap(
        row: Pair<String, Int>,
        listTag: String,
    ): Pair<Float, Float> {
        val list = compose.onNodeWithTag(listTag, useUnmergedTree = true).getBoundsInRoot()
        val node = compose.onAllNodes(hasTestTag(row.first), useUnmergedTree = true).fetchSemanticsNodes()[row.second]
        val d = compose.density.density
        val top = node.positionInRoot.y / d
        val bottom = (node.positionInRoot.y + node.size.height) / d
        return (top - list.top.value) to (list.bottom.value - bottom)
    }

    private fun showSettings(host: SettingsHost) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            LaunchedEffect(router) { router.actions.collect { if (it is Action.Move) sent.intValue++ } }
            MovesProbe()
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                MainScreen(settings, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
            }
        }
        compose.waitForIdle()
        send(Action.Move(Direction.Left), Action.Menu)
    }

    /**
     * Presses Down then Up through rows 0..[last] one at a time, then holds Down and Up across them,
     * recording every frame: the focused row ([rowOf] gives its tag and which of the nodes with that
     * tag) and the row past it, against [listTag]'s bounds.
     */
    private fun walk(
        label: String,
        last: Int,
        listTag: String,
        rowOf: (Int) -> Pair<String, Int>,
    ): List<String> {
        val lines = mutableListOf<String>()
        var focus = 0
        for ((code, hold) in listOf(KeyEvent.KEYCODE_DPAD_DOWN to false, KeyEvent.KEYCODE_DPAD_UP to false, KeyEvent.KEYCODE_DPAD_DOWN to true, KeyEvent.KEYCODE_DPAD_UP to true)) {
            val step = if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
            val name = "${if (hold) "held" else "pressed"} ${if (step < 0) "Up" else "Down"}"
            val start = focus
            compose.mainClock.autoAdvance = false
            val sentBefore = sent.intValue
            var worstFocus = Float.MAX_VALUE
            var worstNext = Float.MAX_VALUE
            var bad = 0
            repeat(if (hold) 1 else last) {
                compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_DOWN, code)) }
                if (!hold) compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_UP, code)) }
                repeat(if (hold) HELD_FRAMES else 14) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    focus = (start + step * (moves - sentBefore)).coerceIn(0, last)
                    val (top, bottom) = gap(rowOf(focus), listTag)
                    val focusGap = if (step < 0) top else bottom
                    val next = (focus + step).takeIf { it in 0..last }
                    val nextGap = next?.let { gap(rowOf(it), listTag).let { (t, b) -> if (step < 0) t else b } }
                    worstFocus = minOf(worstFocus, focusGap)
                    if (nextGap != null) worstNext = minOf(worstNext, nextGap)
                    if (focusGap < -SLACK || (nextGap != null && nextGap < -SLACK)) bad++
                }
                if (hold) compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_UP, code)) }
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertTrue("$label $name went from one end to the other", focus == if (step > 0) last else 0)
            lines += "Largest 768x432 $label $name: rows $start->$focus, focus gap min $worstFocus dp, next-row gap min $worstNext dp, $bad bad frames"
        }
        return lines
    }

    @Test
    fun sectionsColumn() {
        showSettings(SettingsHost(versionName = "test", update = {}, resetAllSettings = {}, resetLibrary = {}))
        val lines = walk("sections", SettingsSection.entries.size - 1, SettingsTags.SECTIONS) { SettingsTags.section(SettingsSection.entries[it]) to 0 }
        println(lines.joinToString("\n"))
        assertTrue(lines.joinToString("\n"), lines.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    /** Emulators with a row per system: the long options column, walked end to end. */
    @Test
    fun optionsColumn() {
        val systems = List(SYSTEMS) { SystemEmulators("system$it", "System $it", automatic = null, chosen = null, installed = emptyList()) }
        showSettings(SettingsHost(versionName = "test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = flowOf(SettingsContent(systems = systems))))
        send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter) // Emulators › its rows
        val rows = SettingsSection.Emulators.rowsIn(SettingsContent(systems = systems))
        val lines =
            walk("Emulators options", rows.size - 1, SettingsTags.OPTION_LIST) { i ->
                if (rows[i] == SettingsOption.SystemEmulator) SettingsTags.system(i) to 0 else SettingsTags.option(rows[i]) to 0
            }
        println(lines.joinToString("\n"))
        assertTrue(lines.joinToString("\n"), lines.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    /**
     * Library › Systems with a system carried (A): pressed and held Up / Down carry it through the
     * real repeat path, and the carried row and the row past it stay whole on every frame.
     */
    @Test
    fun systemsPanelWhileCarrying() {
        val systems = List(SYSTEMS) { LibrarySystem("system$it", "System ${'A' + it}", gameCount = it + 1) }
        val content = SettingsContent(librarySystems = systems)
        showSettings(SettingsHost(versionName = "test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = flowOf(content)))
        send(Action.Enter) // Library's options
        val rows = SettingsSection.Library.rowsIn(content)
        repeat(rows.indexOf(SettingsOption.Systems)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter, Action.Enter) // into the panel, then pick the first system up
        compose.onNodeWithTag(SettingsTags.HINT_MOVE, useUnmergedTree = true).assertExists()
        val lines = walk("Systems carried", systems.size - 1, SettingsTags.SYSTEMS_LIST) { SettingsTags.panelItem(it) to 0 }
        println(lines.joinToString("\n"))
        assertTrue(lines.joinToString("\n"), lines.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    /**
     * Device bug, build 644: at the last row of Library, R1 to another section, a few moves,
     * L1 back showed the list from the top with the remembered row hidden under the hint bar. The focused
     * row must be whole inside the list once a section is shown.
     */
    @Test
    fun theRememberedRowIsInViewWhenASectionIsShownAgain() {
        val folders = List(12) { SettingsFolderRow(FolderKey(1, "Folder $it"), it + 1, FolderAnswer.Unanswered) }
        val content = SettingsContent(unknownFolders = folders)
        showSettings(SettingsHost(versionName = "test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = flowOf(content)))
        send(Action.Enter) // Library's options
        val rows = SettingsSection.Library.rowsIn(content)
        repeat(rows.size - 1) { send(Action.Move(Direction.Down)) }
        send(Action.NextSystem, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.PreviousSystem)
        val last = folders.last().key
        val list = compose.onNodeWithTag(SettingsTags.OPTION_LIST, useUnmergedTree = true).getBoundsInRoot()
        val row = compose.onNodeWithTag(SettingsTags.unknownFolder(last), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("row $row not inside list $list", row.top >= list.top - SLACK.dp && row.bottom <= list.bottom + SLACK.dp)
    }
}
