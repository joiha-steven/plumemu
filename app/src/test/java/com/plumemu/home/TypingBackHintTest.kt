package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B's hint while typing says what B does: Back while the field is empty (B leaves), Delete once it has text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class TypingBackHintTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.board()

    private fun bLabel(text: String): Boolean =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(HomeTags.hint(Action.Erase()))) and hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun searchWithAnEmptyFieldSaysBBackAndBCloses_withTextItSaysBDelete() {
        var search: SearchState? = null
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, BoardGamesFocused, search = remember { typingFor("", content).also { search = it } }) } }
        compose.waitForIdle()
        assertTrue("empty field: B Back", bLabel("Back"))
        assertFalse("empty field: no B Delete", bLabel("Delete"))

        compose.runOnIdle { search!!.onQueryChange("s") }
        compose.waitForIdle()
        assertTrue("with text: B Delete", bLabel("Delete"))
        assertFalse("with text: no B Back", bLabel("Back"))

        compose.runOnIdle { search!!.onQueryChange("") }
        compose.waitForIdle()
        assertTrue("emptied again: B Back", bLabel("Back"))
        // What the hint promises: a fresh B on the empty field closes Search.
        compose.runOnIdle { search!!.onAction(Action.Erase()) }
        assertFalse("B on the empty field closed Search", compose.runOnIdle { search!!.isOpen })
    }

    @Test
    fun everyTypingBarFollowsTheField() {
        val erase = { empty: Boolean -> typingHints(onLetters = true, R.string.home_hint_search, hasShift = true, fieldEmpty = empty).single { it.action is Action.Erase }.label }
        assertEquals(R.string.home_hint_back, erase(true))
        assertEquals(R.string.home_hint_delete, erase(false))
        assertEquals(R.string.home_hint_back, searchTypingHints(onLetters = true, fieldEmpty = true).single { it.action is Action.Erase }.label)
    }
}
