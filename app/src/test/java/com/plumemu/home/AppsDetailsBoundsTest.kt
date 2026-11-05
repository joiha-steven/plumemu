package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Apps list's details column: the app's icon (96 dp), name, "Android app" and package, ending at the right margin; the grid has none. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppsDetailsBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, 0.55f)

    /** Real Apps row ids are "<package>/<activity>" (`LibraryHomeContent.appRowId`). */
    private fun realIds(content: HomeContent): HomeContent =
        content.withEntries(
            content.entries.map { e ->
                if (e.kind == EntryKind.Apps) e.copy(rows = e.rows.mapIndexed { i, r -> r.copy(id = "com.example.app$i/com.example.app$i.Main") }) else e
            },
        )

    private fun show(
        actions: List<Action>,
        gridIds: Set<String> = emptySet(),
    ) = compose.setContent {
        PlumTheme(TableText.Default) {
            CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons()) { HomeAt(realIds(appsGridLibrary(hasBoxArt = true)), actions, gridIds = gridIds) }
        }
    }

    private val onApps = listOf(Action.TapItem(Column.Systems, APPS_ENTRY))

    private fun assertContent() {
        compose.waitForIdle()
        compose.onNode(hasText(AppsGridApps[0].title) and hasAnyAncestor(hasTestTag(HomeTags.DETAILS)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.DETAILS_APP_PACKAGE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("com.example.app0"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals("the package only, never the activity", 0, compose.onAllNodes(hasText("/", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.DETAILS)), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNode(hasText("Android app"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun wideColumnBesideTheListEndsAtTheRightMargin() {
        show(onApps + Action.Enter)
        assertContent()
        val details = bounds(HomeTags.DETAILS)
        assertDp("details 24 after the 400 list", 496.dp, details.left)
        assertDp("details end at the right margin", 768.dp - 24.dp, details.right)
        val icon = bounds(HomeTags.APP_GAME_ICON)
        assertDp("icon about 96 wide", 96.dp, icon.right - icon.left)
        assertDp("icon about 96 high", 96.dp, icon.bottom - icon.top)
        assertDp("icon at the column's left", 496.dp, icon.left)
        assertDp("icon at the strip's top", TableText.StripTop, icon.top)
        assertDp("title at the column's left", 496.dp, bounds(HomeTags.DETAILS_TITLE).left)
        assertTrue("title under the icon", bounds(HomeTags.DETAILS_TITLE).top >= icon.bottom)
        assertTrue("package inside the margin", bounds(HomeTags.DETAILS_APP_PACKAGE).right <= 768.dp - 24.dp + 0.55.dp)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowThirdStopTakesWidthMinus104() {
        show(onApps + Action.Enter + Action.Move(Direction.Right))
        assertContent()
        val details = bounds(HomeTags.DETAILS)
        assertDp("details 24 after the list peek", 80.dp, details.left)
        assertDp("details end at the right margin", 552.dp, details.right)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theGridHasNoColumn() {
        show(onApps + Action.Enter, gridIds = setOf(appsGridLibrary().entries[APPS_ENTRY].id))
        compose.waitForIdle()
        assertEquals("no details column beside the Apps grid", 0, compose.onAllNodesWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
