package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** At 768x432 and the default text size the What's new card shows the first three groups whole, none behind the fade (it held them at 0.1.1). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class WhatsNewRoomTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theFixedNoteIsWholeInsideTheNotesBeforeTheFade() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                Box { WhatsNewCardLayout(remember { WhatsNewState(WhatsNewFixtures.grouped) }, ButtonLetters.Default) {} }
            }
        }
        compose.waitForIdle()
        val notes = compose.onNodeWithTag(WhatsNewTags.NOTES, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val fixed = compose.onNode(hasText("Box art no longer flickers", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        println("WHATSNEW notes ${notes.top.value}..${notes.bottom.value}, fixed note ${fixed.top.value}..${fixed.bottom.value}")
        assertTrue("the Fixed note ends at ${fixed.bottom.value} dp, below the notes' ${notes.bottom.value} dp", fixed.bottom <= notes.bottom)
    }

    @Test
    fun notesThatFitCannotScrollSoNoFadeIsDrawn() {
        val three = WhatsNewFixtures.grouped.let { it.copy(sections = it.sections.dropLast(1)) }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                Box { WhatsNewCardLayout(remember { WhatsNewState(three) }, ButtonLetters.Default) {} }
            }
        }
        compose.waitForIdle()
        val range = compose.onNodeWithTag(WhatsNewTags.NOTES, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("three groups fit, so the notes must not scroll (the fade draws only while they can)", range.maxValue() == 0f)
    }
}
