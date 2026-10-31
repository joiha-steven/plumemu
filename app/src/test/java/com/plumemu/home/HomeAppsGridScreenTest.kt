package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeAppActions
import com.plumemu.game.GamePanelTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Apps as a grid of icons on the main screen through a real router: SELECT
 * switches and is written once per press, signed out of ScreenScraper too (the icons need no box art);
 * an Apps entry without apps is never a grid; the size is fixed, so L1 / R1 do nothing there and L2 / R2 (key or trigger) switch the entry,
 * the hint bar says "L2 R2 Switch system"; A opens the app as in the list, START and Y do nothing, as in the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeAppsGridScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val apps = FakeAppActions()
    private val icons = FakeAppIcons()

    /** SELECT's writes, in order; and the grid sizes L1 / R1 asked for. */
    private val writes = mutableListOf<Pair<String, Boolean>>()
    private val sizes = mutableListOf<GridSize>()
    private var stored by mutableStateOf(emptySet<String>())
    private var shownContent by mutableStateOf<HomeContent?>(null)
    private var layout: PlumLayout? = null

    /** [content] on a fresh main screen (a new one for each call: the focus starts on Recent again). */
    private fun show(content: HomeContent = appsGridLibrary()) {
        val first = shownContent == null
        shownContent = content
        writes.clear()
        if (first) {
            compose.setContent {
                val scope = rememberCoroutineScope()
                router = remember { InputRouter(scope) }
                PlumTheme(PlumThemeSettings.Default) {
                    layout = PlumTheme.layout
                    CompositionLocalProvider(LocalHomeAppIcons provides icons) {
                        val shown = shownContent ?: return@CompositionLocalProvider
                        key(shown) {
                            HomeScreen(
                                shown,
                                router,
                                HomeCallbacks(
                                    apps = apps,
                                    onGridView = { id, isGrid, _ -> writes += id to isGrid },
                                    onGridSize = { size, _ -> sizes += size },
                                ),
                                gridSystems = stored,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    /** From the first frame (Recent focused): Apps' list or grid. */
    private fun openApps() = send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun selectOnAppsSignedOutShowsTheGridAndWritesOncePerPress() {
        show()
        openApps()
        assertEquals("a list until SELECT", 0, count(HomeTags.GRID))
        assertTrue("the SELECT hint without box art", hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("one write for one press", listOf(HomeContent.APPS_ID to true), writes.toList())
        assertEquals("the grid shows before the store answers", 1, count(HomeTags.GRID))
        assertTrue("SELECT List view in the grid", hasText("List view"))
        send(Action.ToggleView)
        assertEquals(listOf(HomeContent.APPS_ID to true, HomeContent.APPS_ID to false), writes.toList())
        assertEquals(0, count(HomeTags.GRID))
    }

    @Test
    fun aStoredAppsOpensAsTheGridSignedOutAndSignedIn() {
        for (hasBoxArt in listOf(false, true)) {
            stored = setOf(HomeContent.APPS_ID)
            show(appsGridLibrary(hasBoxArt))
            openApps()
            assertEquals("box art $hasBoxArt: the stored grid", 1, count(HomeTags.GRID))
            assertTrue("box art $hasBoxArt: the apps' tiles", count(HomeTags.APP_NAME) > 0)
            assertEquals("reading the stored choice writes nothing", emptyList<Pair<String, Boolean>>(), writes.toList())
        }
    }

    @Test
    fun anAppsEntryWithoutAppsIsNeverAGridAndSelectWritesNothing() {
        for (hasBoxArt in listOf(false, true)) {
            val board = appsGridLibrary(hasBoxArt)
            val empty = board.withEntries(board.entries.map { if (it.kind == EntryKind.Apps) it.copy(rows = emptyList(), shownCount = 0) else it })
            stored = setOf(HomeContent.APPS_ID)
            show(empty)
            send(Action.Move(Direction.Down), Action.Move(Direction.Down))
            assertEquals("box art $hasBoxArt: no grid for an empty Apps", 0, count(HomeTags.GRID))
            send(Action.ToggleView)
            assertEquals("box art $hasBoxArt: nothing written", emptyList<Pair<String, Boolean>>(), writes.toList())
            assertEquals(0, count(HomeTags.GRID))
        }
    }

    /** One fixed size and no system switch either: L1 / R1 in Apps' grid do nothing. */
    @Test
    fun l1AndR1InAppsGridDoNothing() {
        for (hasBoxArt in listOf(false, true)) {
            stored = setOf(HomeContent.APPS_ID)
            show(appsGridLibrary(hasBoxArt))
            openApps()
            val tiles = count(HomeTags.APP_NAME)
            assertTrue("box art $hasBoxArt: Apps' tiles", tiles > 0)
            send(Action.NextSystem, Action.PreviousSystem, Action.NextSystem)
            assertEquals("box art $hasBoxArt: still Apps' grid", tiles, count(HomeTags.APP_NAME))
            assertEquals("box art $hasBoxArt: no grid size asked", emptyList<GridSize>(), sizes.toList())
        }
    }

    /** Switching system in a grid is the D-pad alone (or the arrow keys, the same KEYCODE_DPAD_*): no stick, no L1 / R1. */
    @Test
    fun dpadLeftFromAppsGridReachesTheSystemsColumnAndUpDownChangesTheEntry() {
        stored = setOf(HomeContent.APPS_ID)
        show(appsGridLibrary(hasBoxArt = true))
        openApps()
        assertTrue("Apps' tiles", count(HomeTags.APP_NAME) > 0)
        dpad(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("the systems column has the focus: its own hint", hasText("Select"))
        dpad(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("Up changed the entry away from Apps", 0, count(HomeTags.APP_NAME))
        dpad(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("Down came back to Apps", count(HomeTags.APP_NAME) > 0)
    }

    /** L2 / R2 switch the entry from Apps' grid, as keys and as the Thor's analog triggers; L1 / R1 never do. */
    @Test
    fun l2AndR2InAppsGridSwitchTheEntryByKeyAndByTrigger() {
        stored = setOf(HomeContent.APPS_ID)
        show(appsGridLibrary(hasBoxArt = true))
        openApps()
        assertTrue("Apps' tiles", count(HomeTags.APP_NAME) > 0)
        pressKey(KeyEvent.KEYCODE_BUTTON_R2)
        assertEquals("R2 key went on to the first system's list", 0, count(HomeTags.APP_NAME))
        assertTrue("its games", hasText(FakeHomeContent.board().entries[FIRST_SYSTEM_INDEX].rows.first().title))
        pullTrigger(MotionEvent.AXIS_LTRIGGER)
        assertTrue("the left trigger came back to Apps' grid", count(HomeTags.APP_NAME) > 0)
        pullTrigger(MotionEvent.AXIS_RTRIGGER)
        assertEquals("the right trigger went on again", 0, count(HomeTags.APP_NAME))
        pullTrigger(MotionEvent.AXIS_BRAKE)
        assertTrue("a device that reports BRAKE for L2 works too", count(HomeTags.APP_NAME) > 0)
    }

    private fun dpad(code: Int) = pressKey(code)

    private fun pressKey(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            compose.runOnIdle { router.onKeyEvent(KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)) }
            compose.waitForIdle()
        }
    }

    /** An analog trigger pulled all the way and let go (the Thor's L2 / R2 are analog), as `Activity.dispatchGenericMotionEvent` gets it. */
    private fun pullTrigger(axis: Int) {
        for (value in listOf(1f, 0f)) {
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coords = MotionEvent.PointerCoords().apply { setAxisValue(axis, value) }
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
            compose.runOnIdle { router.onGenericMotionEvent(event) }
            compose.waitForIdle()
        }
    }

    @Test
    fun theHintBarOfAppsGridSaysSwitchSystemNotSize() {
        stored = setOf(HomeContent.APPS_ID)
        show(appsGridLibrary(hasBoxArt = true))
        openApps()
        assertEquals(1, count(HomeTags.GRID))
        assertTrue("A Open", hasText("Open"))
        assertTrue("L2 R2 Switch system", hasText("Switch system"))
        assertTrue("SELECT List view", hasText("List view"))
        assertEquals("no Size hint in Apps' grid", false, hasText("Size"))
        assertTrue("the L2 R2 key cap", hasText("L2 R2"))
        assertTrue("the bar names the focused app", hasText(AppsGridApps[0].title))
        // A game grid keeps its Size hint.
        send(Action.Back, Action.Move(Direction.Down), Action.Enter, Action.ToggleView)
        assertTrue("a system's grid still has Size", hasText("Size"))
    }

    @Test
    fun aOpensTheFocusedAppYDoesNothingAndStartOpensTheAppMenu() {
        stored = setOf(HomeContent.APPS_ID)
        show()
        openApps()
        send(Action.Move(Direction.Right), Action.Move(Direction.Down), Action.Play)
        val columns = appsGridColumns(checkNotNull(layout))
        assertEquals("A opens the app under the cursor (right one, down one row)", listOf(AppsGridApps[1 + columns].id), apps.opened)
        send(Action.ToggleFavorite)
        assertEquals("Y opens nothing on an app", 0, count(GamePanelTags.PANEL))
        send(Action.Menu)
        assertEquals("START opens the app menu, not an app", 1, count(GamePanelTags.PANEL))
        assertEquals("nothing else was opened", 1, apps.opened.size)
    }

    @Test
    fun iconsLoadOncePerTileAndNotOnFocusMoves() {
        stored = setOf(HomeContent.APPS_ID)
        show()
        openApps()
        val loaded = icons.loads.toList()
        assertTrue("the shown tiles loaded their icons", loaded.isNotEmpty())
        assertEquals("each once", loaded.distinct(), loaded)
        send(Action.Move(Direction.Right), Action.Move(Direction.Right), Action.Move(Direction.Left))
        assertEquals("a move on the shown rows loads nothing", loaded, icons.loads.toList())
    }

    /**
     * A package change (`AppGraph.packageChanges` invalidates the icons): the tiles on screen ask again without
     * leaving Apps, so an app whose icon failed while it was installing gets it, and the others are loaded anew.
     */
    @Test
    fun aPackageChangeReloadsTheIconsOnScreenWithoutLeavingApps() {
        stored = setOf(HomeContent.APPS_ID)
        show()
        openApps()
        assertEquals("the app without an icon shows the stand-in", 1, count(HomeTags.APP_ICON_STAND_IN))
        val before = icons.loads.size
        val tiles = count(HomeTags.APP_NAME)
        compose.runOnIdle {
            icons.missing = emptySet()
            icons.invalidate()
        }
        compose.waitForIdle()
        assertEquals("its icon now shows", 0, count(HomeTags.APP_ICON_STAND_IN))
        assertEquals("every shown tile has its icon", tiles, count(HomeTags.APP_ICON))
        assertEquals("each shown tile asked once more", tiles, icons.loads.size - before)
        assertEquals("still Apps' grid", 1, count(HomeTags.GRID))
    }

    /** Signed out with Recent and Apps stored as grids: only Apps is one; Recent offers no SELECT and a press writes nothing. */
    @Test
    fun signedOutAStoredRecentStaysAListBesideAppsGrid() {
        stored = setOf(HomeContent.RECENT_ID, HomeContent.APPS_ID)
        val content = appsGridLibrary(hasBoxArt = false)
        show(content)
        assertTrue("Recent has games", content.entries[0].rows.isNotEmpty())
        val strip = content.stripWith(stored, checkNotNull(layout))
        assertEquals("Recent: one column", 1, strip.systems[0].gridColumns)
        assertEquals("Apps: its grid's columns", appsGridColumns(checkNotNull(layout)), strip.systems[APPS_ENTRY].gridColumns)
        assertEquals(false, content.showsGrid(content.entries[0], stored))
        send(Action.Enter) // Recent's games
        assertEquals("Recent is a list", 0, count(HomeTags.GRID))
        assertEquals("no Grid view hint on Recent", false, hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("SELECT on Recent writes nothing", emptyList<Pair<String, Boolean>>(), writes.toList())
        assertEquals(0, count(HomeTags.GRID))
        send(Action.Back, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
        assertEquals("Apps is the stored grid", 1, count(HomeTags.GRID))
    }

    @Test
    fun anAppWithoutAnIconShowsTheStandInAndTheOthersTheirIcons() {
        stored = setOf(HomeContent.APPS_ID)
        show()
        openApps()
        val tiles = count(HomeTags.APP_NAME)
        assertEquals("one stand-in, for the app without an icon", 1, count(HomeTags.APP_ICON_STAND_IN))
        assertEquals("every other shown tile has its icon", tiles - 1, count(HomeTags.APP_ICON))
        assertTrue("its name's first letter on the stand-in", hasText(firstLetterOf(AppsGridApps[APP_WITHOUT_ICON].title)))
    }
}
