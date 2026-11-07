package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.textProblems
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home empty states: their buttons act by A and by touch, Left and Right move between them, and the text fits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeEmptyStateTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var folderAsked = 0
    private var filesNotAdded = 0
    private lateinit var router: InputRouter

    private fun show(
        why: EmptyLibrary,
        theme: com.plumemu.coreui.theme.PlumThemeSettings = TableText.Default,
    ): HomeNotices {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(why))
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(theme) {
                CompositionLocalProvider(LocalHomeNotices provides notices) {
                    HomeScreen(FakeHomeContent.noGames(), router, HomeCallbacks(onAddFolder = { folderAsked++ }, onFilesNotAdded = { filesNotAdded++ }, notices = notices))
                }
            }
        }
        compose.waitForIdle()
        return notices
    }

    private fun press(action: Action) {
        router.send(action)
        compose.waitForIdle()
    }

    private fun hasTag(button: EmptyButton) = compose.onAllNodesWithTag(HomeTags.emptyButton(button), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(button: EmptyButton) {
        compose.onNodeWithTag(HomeTags.emptyButton(button), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun hintShows(text: String) = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).let { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size >= 2 }

    private val left = Action.Move(Direction.Left)
    private val right = Action.Move(Direction.Right)

    @Test
    fun noFolderHasOneButtonAndAAddsAFolder() {
        show(EmptyLibrary.NoFolder)
        assertTrue(hasTag(EmptyButton.AddFolder))
        assertTrue(!hasTag(EmptyButton.FilesNotAdded) && !hasTag(EmptyButton.ChooseFolder))
        press(Action.Enter)
        assertEquals(1, folderAsked)
        assertEquals(0, filesNotAdded)
    }

    @Test
    fun anEmptyLibraryInvitesOnceAndCountsNothing() {
        show(EmptyLibrary.NoFolder)
        val bar = compose.onAllNodesWithText("Plumemu", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the top bar names the app", bar.isNotEmpty())
        assertTrue("no count of nothing", compose.onAllNodesWithText("0 systems", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals("the button is the only invitation", 1, compose.onAllNodesWithText("Add folder", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun noFolderTapOnTheButtonAddsAFolderAndLeftRightDoNothing() {
        show(EmptyLibrary.NoFolder)
        press(right)
        press(left)
        assertEquals(0, folderAsked)
        tap(EmptyButton.AddFolder)
        assertEquals(1, folderAsked)
    }

    @Test
    fun noGamesPrimaryIsFilesNotAddedAndAOpensIt() {
        show(EmptyLibrary.NoGames)
        assertTrue(hasTag(EmptyButton.FilesNotAdded) && hasTag(EmptyButton.ChooseFolder))
        press(Action.Enter)
        assertEquals(1, filesNotAdded)
        assertEquals(0, folderAsked)
    }

    /** A folder with no files to report: the only button is Choose folder, the primary one, and A presses it from the column. */
    @Test
    fun noFilesOffersOnlyChooseFolderAsThePrimaryAndAOpensThePicker() {
        show(EmptyLibrary.NoFiles)
        assertTrue(hasTag(EmptyButton.ChooseFolder))
        assertTrue(!hasTag(EmptyButton.FilesNotAdded) && !hasTag(EmptyButton.AddFolder))
        press(Action.Enter)
        assertEquals(1, folderAsked)
        assertEquals(0, filesNotAdded)
    }

    @Test
    fun noFilesKeepsTheFocusRulesRightEntersTheButtonLeftReturns() {
        val notices = show(EmptyLibrary.NoFiles)
        assertTrue("the hint names the primary button from the column", hintShows("Choose folder"))
        press(right)
        assertTrue(notices.emptyFocus.inButtons)
        press(right)
        assertTrue("Right at the only button stays on it", notices.emptyFocus.inButtons)
        press(Action.Enter)
        assertEquals(1, folderAsked)
        press(left)
        assertTrue("Left returns to the column", !notices.emptyFocus.inButtons)
        press(Action.Enter)
        assertEquals(2, folderAsked)
        tap(EmptyButton.ChooseFolder)
        assertEquals(3, folderAsked)
        assertEquals(0, filesNotAdded)
    }

    @Test
    fun rightMovesToChooseFolderLeftBackAndTheEndsStayPut() {
        show(EmptyLibrary.NoGames)
        press(left)
        press(Action.Enter)
        assertEquals("Left at the first button stays on it", 1, filesNotAdded)
        press(right)
        press(right)
        press(Action.Enter)
        assertEquals(1, folderAsked)
        assertEquals(1, filesNotAdded)
        press(left)
        press(Action.Enter)
        assertEquals(2, filesNotAdded)
        assertEquals(1, folderAsked)
    }

    @Test
    fun theHintBarNamesWhatAWouldDo() {
        show(EmptyLibrary.NoGames)
        assertTrue("A: Files not added (the shortcut from the column)", hintShows("Files not added"))
        press(right)
        assertTrue("A: Files not added (the first button has the focus)", hintShows("Files not added"))
        press(right)
        assertTrue("A: Choose folder", hintShows("Choose folder"))
        press(left)
        press(left)
        assertTrue("back on the column the hint names the primary button", hintShows("Files not added"))
    }

    @Test
    fun leftFromTheFirstButtonAndBackReturnToTheColumnAndAStillDoesThePrimaryAction() {
        show(EmptyLibrary.NoGames)
        press(right)
        press(left)
        press(Action.Enter)
        assertEquals(1, filesNotAdded)
        press(right)
        press(Action.Back)
        press(Action.Enter)
        assertEquals(2, filesNotAdded)
        assertEquals(0, folderAsked)
    }

    @Test
    fun upAndDownLeaveTheButtonsAndMoveTheColumn() {
        val notices = show(EmptyLibrary.NoGames)
        press(right)
        assertTrue(notices.emptyFocus.inButtons)
        press(Action.Move(Direction.Down))
        assertTrue(!notices.emptyFocus.inButtons)
    }

    @Test
    fun aColumnRowIsCurrentNotFocusedWhileAButtonHasTheFocus() {
        val focus = com.plumemu.coreinput.strip.StripFocus()
        assertEquals(com.plumemu.coreui.components.SystemRowLook.Focused, systemLook(focus, 0, buttonHasFocus = false))
        assertEquals(com.plumemu.coreui.components.SystemRowLook.Current, systemLook(focus, 0, buttonHasFocus = true))
    }

    private fun pixels(tag: String): List<Int> {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        return List(bitmap.width * bitmap.height) { bitmap.getPixel(it % bitmap.width, it / bitmap.width) }
    }

    /** Only one thing is drawn focused: with the column focused the row is the slab and the button rests; with a button focused it is the other way round. */
    private fun assertOneFocused(
        why: EmptyLibrary,
        theme: com.plumemu.coreui.theme.PlumThemeSettings,
    ) {
        show(why, theme)
        val row = HomeTags.system(0)
        val button = HomeTags.emptyButton(buttonsFor(why).first())
        val rowFocused = pixels(row)
        val buttonResting = pixels(button)
        press(right)
        val rowCurrent = pixels(row)
        val buttonLit = pixels(button)
        assertTrue("$why: the row's slab is gone while the button has the focus", rowFocused != rowCurrent)
        assertTrue("$why: the button's groove lights only while it has the focus", buttonResting != buttonLit)
        press(left)
        assertEquals("$why: back on the column the row looks as before", rowFocused, pixels(row))
        assertEquals("$why: and the button rests again", buttonResting, pixels(button))
    }

    @Test
    fun exactlyOneThingLooksFocusedNoGamesGraphite() = assertOneFocused(EmptyLibrary.NoGames, TableText.Graphite)

    @Test
    fun exactlyOneThingLooksFocusedNoGamesPaper() = assertOneFocused(EmptyLibrary.NoGames, TableText.Paper)

    @Test
    fun exactlyOneThingLooksFocusedNoFolderGraphite() = assertOneFocused(EmptyLibrary.NoFolder, TableText.Graphite)

    @Test
    fun exactlyOneThingLooksFocusedNoFolderPaper() = assertOneFocused(EmptyLibrary.NoFolder, TableText.Paper)

    @Test
    fun tapsOnEachButtonDoTheirOwnAction() {
        show(EmptyLibrary.NoGames)
        tap(EmptyButton.ChooseFolder)
        assertEquals(1, folderAsked)
        assertEquals(0, filesNotAdded)
        tap(EmptyButton.FilesNotAdded)
        assertEquals(1, filesNotAdded)
    }

    @Test
    fun whileReadingFoldersThereAreNoButtonsAndNeitherAnorATapAsksForAFolder() {
        show(EmptyLibrary.Scanning)
        assertTrue(!hasTag(EmptyButton.AddFolder) && !hasTag(EmptyButton.FilesNotAdded))
        press(Action.Enter)
        compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(0, folderAsked)
        assertEquals(0, filesNotAdded)
    }

    @Test
    fun whileReadingFoldersAOffersNothing() {
        val hints = hintsFor(FakeHomeContent.noGames(), Column.Systems, FakeHomeContent.noGames().entries.first(), emptyButton = null)
        assertTrue(hints.none { it.action == Action.Enter })
    }

    @Test
    fun aRefusedFolderAskStillSaysWhyAboveTheButtons() {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(EmptyLibrary.NoFolder), folderMessage = MutableStateFlow(com.plumemu.R.string.setup_message_no_picker))
        compose.setContent { PlumTheme(TableText.Default) { CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("This device has no folder picker", substring = true, useUnmergedTree = true).assertCountEquals(1)
        assertTrue(hasTag(EmptyButton.AddFolder))
    }

    private fun assertFits(
        why: EmptyLibrary,
        what: String,
        /** Button labels that may end in an ellipsis: at 576 x 432 the stacked button is a few dp short of "Files not" at Largest. */
        longLabels: Set<String> = emptySet(),
    ) {
        val largest = TableText.Default.copy(textSize = TextSize.Largest)
        show(why, largest)
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val message = compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).fetchSemanticsNode()
        val problems = textProblems(message, FitAllowance(isLargest = true, ellipsisTexts = longLabels))
        assertTrue("$what: ${problems.joinToString()} message=${message.boundsInRoot} parent=${message.parent?.boundsInRoot}", problems.isEmpty())
        val screen = root.boundsInRoot
        val hintTop = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val density = compose.density.density
        EmptyButton.entries.filter(::hasTag).forEach {
            val b = compose.onNodeWithTag(HomeTags.emptyButton(it), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$what: $it inside the screen ($b)", b.left.value >= 0f && b.right.value * density <= screen.right + 1f && b.top.value >= 0f)
            assertTrue("$what: $it above the hint bar ($b, bar top $hintTop)", b.bottom <= hintTop)
        }
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun noFolderFitsAtLargest768() = assertFits(EmptyLibrary.NoFolder, "no folder 768x432")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun noGamesFitsAtLargest768() = assertFits(EmptyLibrary.NoGames, "no games 768x432")

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noFolderFitsAtLargest576() = assertFits(EmptyLibrary.NoFolder, "no folder 576x432")

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noGamesFitsAtLargest576() = assertFits(EmptyLibrary.NoGames, "no games 576x432", longLabels = setOf("Files not added"))

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun noFolderFitsAtLargest832() = assertFits(EmptyLibrary.NoFolder, "no folder 832x468")

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun noGamesFitsAtLargest832() = assertFits(EmptyLibrary.NoGames, "no games 832x468")
}
