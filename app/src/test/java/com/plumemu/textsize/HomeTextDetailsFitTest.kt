package com.plumemu.textsize

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The text-only details column (no box art, wide) at 768x432 at every
 * text size in Plum Sans and at Large and Largest in Serif and Mono: no text ellipsized, cut, clipped
 * or on top of another, and the column inside the screen's 24 dp margin. Its own file:
 * `TextSizeScreensTest` is at the 600-line guard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeTextDetailsFitTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        row: Int,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        val content = LibraryLikeContent.of()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) {
                    HomeAt(content, LibraryLikeContent.gamesFocused(row))
                }
            }
        }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val label = "$name at ${each.name} ${eachFont.name} on 768x432"
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val details = findTagged(root, HomeTags.DETAILS)
            if (details == null) {
                failures += "$label: no details column"
                continue
            }
            if (findTagged(details, HomeTags.DETAILS_FACTS) == null) failures += "$label: no facts table"
            textProblems(details, FitAllowance()).mapTo(failures) { "$label: $it" }
            // The one-line texts are also checked by width alone; the emulator may wrap to its second line by design.
            for (tag in listOf(HomeTags.DETAILS_TITLE, HomeTags.DETAILS_SYSTEM)) {
                val node = findTagged(details, tag)
                if (node == null) failures += "$label: no $tag" else runCatching { assertNoTextCut(node, label) }.exceptionOrNull()?.let { failures += it.message.orEmpty() }
            }
            notFullyShown(details, root)?.let { failures += "$label: the column $it" }
            val rightMargin = root.boundsInRoot.right - details.boundsInRoot.right
            if (rightMargin < MARGIN_PX - 1f) failures += "$label: the column ends ${rightMargin}px from the screen's edge (24 dp = ${MARGIN_PX}px)"
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun superMetroidWithItsFacts() = check("home_details_text", LibraryLikeContent.SUPER_METROID)

    @Test
    fun aGameWithoutARegion() = check("home_details_text_no_region", LibraryLikeContent.TETRIS_ATTACK)

    private companion object {
        /** 24 dp at xhdpi. */
        const val MARGIN_PX = 48f
    }
}
