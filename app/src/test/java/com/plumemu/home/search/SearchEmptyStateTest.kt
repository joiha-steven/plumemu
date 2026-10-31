package com.plumemu.home.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search with an empty field and Plumemu's keyboard up (audit B7): a muted line says what to type,
 * the keys stay above the hint bar with the design gap, and every key of the keys' block is as high
 * as a letter key. Measured at 768x432 and 576x432, at Medium and Largest text.
 */
abstract class SearchEmptyStateBase(
    private val widthDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.withFavorites()

    private fun each(check: (root: androidx.compose.ui.semantics.SemanticsNode, density: Float, label: String, failures: MutableList<String>) -> Unit) {
        var size by mutableStateOf(TextSize.Default)
        val search = SearchState().apply { open() }
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(content, BoardGamesFocused, search = search) } }
        val failures = mutableListOf<String>()
        for (each in listOf(TextSize.Default, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val density = root.boundsInRoot.width / widthDp
            check(root, density, "${each.name} on ${widthDp}x432", failures)
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun anEmptyFieldSaysWhatToType() =
        each { root, density, label, failures ->
            val line = findTagged(root, SearchTags.EMPTY)
            val tray = findTagged(root, SearchTags.TRAY)?.boundsInRoot
            if (line == null) {
                failures += "$label: no empty-field line"
            } else if (tray != null && line.boundsInRoot.bottom > tray.top + 1f) {
                failures += "$label: the line is under the tray (${line.boundsInRoot}, tray $tray)"
            }
        }

    @Test
    fun theLineComesBackWhenTheQueryIsErased() {
        val search = SearchState().apply { open() }
        search.onQueryChange("mario")
        search.onQueryChange("")
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, BoardGamesFocused, search = search) } }
        compose.waitForIdle()
        assertTrue("no empty-field line after erasing", findTagged(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), SearchTags.EMPTY) != null)
    }

    @Test
    fun theKeysStayAboveTheHintBar() =
        each { root, density, label, failures ->
            val hints = findTagged(root, HomeTags.HINT_BAR)!!.boundsInRoot
            for (r in 0..3) {
                val keys = (0..9).mapNotNull { findTagged(root, keyboardKeyTag(KeyId(r, it)))?.boundsInRoot }
                println("MEASURE $label row $r: top=${keys.minOf { it.top } / density} bottom=${keys.maxOf { it.bottom } / density} heights=${keys.map { it.height / density }.distinct()} hintTop=${hints.top / density}")
            }
            val last = findTagged(root, keyboardKeyTag(KeyId(3, 3)))!!.boundsInRoot
            // The cell holds the key and half a gap on each side: the key itself ends 2 dp above its cell's bottom.
            val gap = (hints.top - (last.bottom - 2f * density)) / density
            if (gap < 6f - 0.5f) failures += "$label: the last row's keys end $gap dp above the hint bar, not 6"
        }

    @Test
    fun everyKeyOfARowIsAsHighAsALetter() =
        each { root, density, label, failures ->
            for (r in 0..3) {
                val heights = (0..9).mapNotNull { findTagged(root, keyboardKeyTag(KeyId(r, it)))?.boundsInRoot?.height }
                if (heights.maxOrNull()!! - heights.minOrNull()!! > 0.5f) failures += "$label: row $r key heights differ: ${heights.map { it / density }}"
                if (heights.minOrNull()!! / density < 40f - 0.5f) failures += "$label: row $r has a key under 40 dp: ${heights.map { it / density }}"
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchEmptyStateRp5Test : SearchEmptyStateBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SearchEmptyStateNovaTest : SearchEmptyStateBase(576)
