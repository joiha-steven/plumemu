package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard on the reference screens (docs/design/rules.md › Keyboard): keys 36 and the block 476
 * centred, the tray within half the screen (except 667×375, which passes it on purpose: half is 187.5, under
 * what 36 dp keys need), every key's touch area at least 40 dp, no label cut at the largest text in any
 * font. Run at 576×432, 768×432, 837×471 and 667×375 by the four classes below.
 */
abstract class KeyboardFitTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var spec by mutableStateOf(KeyboardSpec(actionLabel = "Search", hasShift = false))
    private var state by mutableStateOf(KeyboardState(spec.startModel()))
    private var trayToBottom by mutableStateOf(true)

    private fun show() {
        compose.setContent {
            PlumTheme(settings) { KeyboardOnScreen(state, spec, trayToBottom) }
        }
        compose.waitForIdle()
    }

    private fun use(
        newSpec: KeyboardSpec,
        model: KeyboardModel = newSpec.startModel(),
    ) {
        compose.runOnIdle {
            spec = newSpec
            state = KeyboardState(model)
        }
        compose.waitForIdle()
    }

    private fun pxPerDp(): Float = compose.density.density

    private fun screenHeightPx(): Float = compose.onRoot().fetchSemanticsNode().size.height.toFloat()

    /**
     * What the keyboard draws, measured on the real key nodes: from the tray's top to the bottom of
     * the last row of keys, plus what goes under them on the tray (the 6 gap, the hint bar 22 x the
     * height factor, the 12 margin). Keys are 36 at every size; the tray is within half the screen at every
     * size except on the 375 high RP4 Pro (half is 187.5, under what 36 keys need), where it is the plain sum.
     */
    @Test
    fun theKeysAre36AndTheTrayStaysWithinHalfTheScreen() {
        show()
        val px = pxPerDp()
        val halfGap = PlumDimens.keyboardKeyGap.value / 2 * px
        for (size in TextSize.entries) {
            compose.runOnIdle { settings = settings.copy(textSize = size) }
            compose.waitForIdle()
            val trayTop = compose.onNodeWithTag(KEYBOARD_TAG).fetchSemanticsNode().boundsInRoot.top
            val cells = compose.onAllNodes(isKeyCell).fetchSemanticsNodes()
            val keysBottom = cells.maxOf { it.positionInRoot.y + it.size.height } - halfGap
            val belowKeys = (6f + 22f * size.heightScale + 12f) * px
            val tray = (keysBottom - trayTop + belowKeys) / px
            val half = screenHeightPx() / 2 / px
            val keyHeight = (cells.minOf { it.size.height } - 2 * halfGap) / px
            assertEquals("at $size the keys are 36", 36f, keyHeight, 0.5f)
            if (half > 190f) assertTrue("at $size the tray is $tray dp of half $half", tray <= half + 0.5f)
            assertEquals("at $size the tray is 8 + 4 x 36 + 3 x 4 + 6 + hint bar + 12", 164f + 18f + 22f * size.heightScale, tray, 0.5f)
            // 8 dp of tray, four rows, three gaps: 164 dp of keyboard at every size.
            assertEquals(164f, (keysBottom - trayTop) / px, 0.5f)
        }
    }

    @Test
    fun everyKeyCanBeTouchedOnAtLeast40dp() {
        show()
        val minimum = 40f * pxPerDp()
        for (size in TextSize.entries) {
            for (hasShift in listOf(true, false)) {
                for (page in KeyboardPage.entries) {
                    compose.runOnIdle { settings = settings.copy(textSize = size) }
                    use(KeyboardSpec("Search", hasShift = hasShift, startPage = page))
                    val cells = compose.onAllNodes(isKeyCell).fetchSemanticsNodes()
                    assertEquals("$page keys", KeyboardLayout.rows(page, hasShift).sumOf { it.size }, cells.size)
                    for (cell in cells) {
                        val tag = cell.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
                        assertTrue("$size $page $tag is ${cell.size} px, under 40 dp ($minimum px)", cell.size.width >= minimum && cell.size.height >= minimum)
                    }
                }
            }
        }
    }

    @Test
    fun noLabelIsCutAtTheLargestTextInAnyFont() {
        show()
        val halfGap = PlumDimens.keyboardKeyGap.value / 2 * pxPerDp()
        val problems = mutableListOf<String>()
        for (font in PlumFont.entries) {
            for (action in listOf("Search", "Sign in", "Next", "Done")) {
                for (page in KeyboardPage.entries) {
                    // Shift locked: the letters in upper case, the widest they get.
                    val newSpec = KeyboardSpec(action, hasShift = true, startPage = page)
                    compose.runOnIdle { settings = settings.copy(font = font, textSize = TextSize.Largest) }
                    use(newSpec, newSpec.startModel().copy(shift = ShiftMode.Locked))
                    val (found, seen) = labelProblems(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), halfGap)
                    // Every key but the three icons has a label.
                    assertEquals("$font $page labels", KeyboardLayout.rows(page, true).sumOf { it.size } - if (page == KeyboardPage.Letters) 3 else 2, seen)
                    problems += found.map { "$font, $action, $page: $it" }
                }
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun theTrayRunsFromEdgeToEdgeAndTheKeysAre476WideCentred() {
        show()
        val keyboard = compose.onNodeWithTag(KEYBOARD_TAG).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.left, keyboard.left, 0.5f)
        assertEquals(root.right, keyboard.right, 0.5f)
        assertEquals(root.bottom, keyboard.bottom, 0.5f)
        // The block of 10 columns: 476 wide, centred (the first cell starts half a gap before its key).
        val gap = PlumDimens.keyboardKeyGap.value * pxPerDp()
        val cells = compose.onAllNodes(isKeyCell).fetchSemanticsNodes()
        val blockLeft = cells.minOf { it.boundsInRoot.left } + gap / 2
        val blockRight = cells.maxOf { it.boundsInRoot.right } - gap / 2
        assertEquals("block width", 476f * pxPerDp(), blockRight - blockLeft, 1f)
        assertEquals("centred", (root.width - (blockRight - blockLeft)) / 2, blockLeft - root.left, 1f)
        val first = compose.onAllNodesWithTag(keyboardKeyTag(KeyId(0, 0))).fetchSemanticsNodes().single().boundsInRoot
        assertEquals((root.width / pxPerDp() - 476f) / 2 * pxPerDp() - gap / 2, first.left, 1f)
        assertEquals(keyboard.top + (8f - 2f) * pxPerDp(), first.top, 1f)
        // A key is 44 wide: a letter's cell is 44 plus the gap.
        assertEquals(48f * pxPerDp(), first.width, 1f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class KeyboardFitNovaTest : KeyboardFitTest()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class KeyboardFitRp5Test : KeyboardFitTest()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class KeyboardFitThorTest : KeyboardFitTest()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class KeyboardFitRp4ProTest : KeyboardFitTest()
