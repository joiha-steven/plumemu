package com.plumemu

import android.view.View
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import com.plumemu.content.HomeState
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.LibrarySystem
import com.plumemu.settings.screen.SettingsActivityActions
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsTags
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The semantics tree of each main screen stays within a budget. While any accessibility service is on (the handhelds ship one,
 * their game assistant), Compose walks and sorts the whole unmerged tree on every change of its bounds, on the main thread: the
 * walk takes every node, also those of cleared or unplaced subtrees, so only a node that does not exist costs nothing. Budgets
 * are today's counts; lower them when a change removes nodes. The counts and the sort's time are printed for reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SemanticsTreeSizeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var host: SettingsHost
    private lateinit var view: View
    private val real = SettingsContent(librarySystems = listOf(LibrarySystem("gba", "Game Boy Advance", 196)))

    private val activity =
        object : SettingsActivityActions {
            override fun openFolderPicker(): Boolean = false

            override fun openHomeSettings(onNotHome: () -> Unit): Boolean = false

            override fun isHomeApp(): Boolean = true

            override fun isUsageAccessGranted(): Boolean = false

            override fun openBackupPicker(kind: BackupKind): Boolean = false

            override fun openRestorePicker(): Boolean = false
        }

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            host = remember { SettingsHost("test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = MutableSharedFlow(replay = 1), remembered = { real }, activity = activity) }
            view = LocalView.current
            MainScreen(PlumSettings.Defaults, HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(games = FakeGameActions()), onRetry = {}, settingsHost = host)
        }
        compose.waitUntil(5_000) { host.deviceFacts.value != null }
        compose.waitForIdle()
    }

    private fun press(vararg actions: Action) {
        actions.forEach {
            compose.runOnIdle { router.send(it) }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /** Every node of the unmerged tree, as Compose's traversal sort walks it. */
    private fun nodes(): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()

        fun walk(node: SemanticsNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    /** Microseconds per run of Compose's traversal sort over the unmerged tree (its accessibility pass runs it), median of rounds; "n/a" when a Compose update renamed it. */
    private fun sortMicros(): String = runCatching { sortMicrosOrThrow().toString() }.getOrElse { "n/a (${it.javaClass.simpleName})" }

    private fun sortMicrosOrThrow(): Long {
        val root = (view as RootForTest).semanticsOwner.unmergedRootSemanticsNode
        val sort = Class.forName("androidx.compose.ui.semantics.SemanticsSortKt").getMethod("subtreeSortedByGeometryGrouping", SemanticsNode::class.java, Function1::class.java, Function1::class.java, List::class.java)
        val all: (SemanticsNode) -> Boolean = { true }
        val runs = mutableListOf<Long>()
        compose.runOnUiThread {
            repeat(SORT_WARM_UP) { sort.invoke(null, root, all, all, root.children) }
            repeat(SORT_ROUNDS) {
                val start = System.nanoTime()
                repeat(SORT_RUNS) { sort.invoke(null, root, all, all, root.children) }
                runs += (System.nanoTime() - start) / SORT_RUNS / 1_000
            }
        }
        return runs.sorted()[runs.size / 2]
    }

    private fun assertWithin(
        screen: String,
        budget: Int,
        shownBy: String,
    ) {
        val all = nodes()
        assertTrue("$screen shows (a node tagged $shownBy)", all.any { it.config.getOrNull(SemanticsProperties.TestTag) == shownBy })
        val silent = all.count { node -> node.config.none { it.key != SemanticsProperties.Shape } }
        println("semantics nodes, $screen: ${all.size} (budget $budget), with nothing to say $silent, sort ${sortMicros()} us")
        assertTrue("$screen: ${all.size} semantics nodes, budget $budget", all.size <= budget)
    }

    @Test
    fun eachMainScreenKeepsItsSemanticsTreeWithinItsBudget() {
        show()
        assertWithin("home (Recent)", HOME, HomeTags.system(0))
        press(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Right))
        // The hint bar offers Play only with a game focused.
        assertWithin("games list", GAMES_LIST, PLAY_HINT)
        press(Action.Menu)
        assertWithin("game menu open", GAME_MENU, GamePanelTags.CARD)
        press(Action.Back)
        press(Action.ToggleView)
        assertWithin("Apps grid", APPS_GRID, HomeTags.GRID)
        press(Action.ToggleView, Action.Back, Action.Move(Direction.Up), Action.Move(Direction.Up))
        press(Action.OpenSettings)
        assertWithin("Settings", SETTINGS, SettingsTags.SCREEN)
    }

    private companion object {
        // 640 x 480, the board library. Before graphics layers stopped being nodes (plainLayer): 129, 163, 155, 242, 67.
        const val HOME = 112
        const val GAMES_LIST = 146
        const val GAME_MENU = 138
        const val APPS_GRID = 225
        const val SETTINGS = 66
        const val PLAY_HINT = "home_hint_Play"
        const val SORT_WARM_UP = 50
        const val SORT_ROUNDS = 7
        const val SORT_RUNS = 200
    }
}
