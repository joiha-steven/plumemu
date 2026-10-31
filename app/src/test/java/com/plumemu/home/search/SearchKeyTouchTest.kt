package com.plumemu.home.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.textsize.findTagged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search's keys (uiautomator reported the Hide key 36.4 / 38.4 dp high; here every key, Hide
 * included, is a 40 dp cell at Large and Largest (the 400 dpi class is the emulator's density). The reported 36.4 is not
 * reproduced (it is an accessibility bound of the device); see the keyboard module's notes for the touch-area overlap with the hint bar.
 */
abstract class SearchKeyTouchBase(
    private val widthDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val actions = mutableListOf<Action>()
    private val search = SearchState().apply { open() }

    private var size by mutableStateOf(TextSize.Default)

    private var shown = false

    /** Shows Search once; a later call only changes the text size. */
    private fun show(each: TextSize) {
        size = each
        if (!shown) {
            shown = true
            compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(FakeHomeContent.withFavorites(), BoardGamesFocused, onAction = { actions += it }, search = search) } }
        }
        compose.waitForIdle()
    }

    private fun root(): SemanticsNode = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()

    @Test
    fun everyKeyCellIsFortyHighTheHideKeyToo() {
        for (size in listOf(TextSize.Default, TextSize.Largest)) {
            val sizeFailures = mutableListOf<String>()
            show(size)
            val root = root()
            val density = root.boundsInRoot.width / widthDp
            for (r in 0..3) for (i in 0..9) {
                val key = findTagged(root, keyboardKeyTag(KeyId(r, i))) ?: continue
                val drawn = key.boundsInRoot.height / density
                if (kotlin.math.abs(drawn - 40f) > 0.5f) sizeFailures += "key $r,$i is $drawn dp high"
            }
            val hide = findTagged(root, keyboardKeyTag(KeyId(3, 1)))!!.boundsInRoot.height / density
            assertTrue("${size.name}: the Hide key's cell is $hide dp high, not 40", kotlin.math.abs(hide - 40f) <= 0.5f)
            assertTrue("${size.name}: " + sizeFailures.joinToString("; "), sizeFailures.isEmpty())
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchKeyTouchRp5Test : SearchKeyTouchBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SearchKeyTouchNovaTest : SearchKeyTouchBase(576)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-400dpi")
class SearchKeyTouch400Test : SearchKeyTouchBase(768)
