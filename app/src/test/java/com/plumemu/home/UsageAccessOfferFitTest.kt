package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsProperties
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

/** At 576x432 and the largest text the offer card shows whole (its scroll has at most its padding left to scroll) in the languages with the longest words. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class UsageAccessOfferFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun assertWhole() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                Box { UsageAccessOfferLayout(UsageAccessOfferState.NOT_NOW, ButtonLetters.Default) {} }
            }
        }
        compose.waitForIdle()
        val range = compose.onNodeWithTag(UsageAccessOfferTags.SCROLL, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        // A few px is the card's own bottom padding (CJK lines run taller); a wrapped button or line would be 60 px or more.
        assertTrue("the card scrolls by ${range.maxValue()} px, so something is cut", range.maxValue() <= MAX_PADDING_SCROLL_PX)
    }

    @Test
    fun englishIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "fr-w576dp-h432dp-land-xhdpi")
    fun frenchIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "es-w576dp-h432dp-land-xhdpi")
    fun spanishIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "vi-w576dp-h432dp-land-xhdpi")
    fun vietnameseIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "ja-w576dp-h432dp-land-xhdpi")
    fun japaneseIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "ko-w576dp-h432dp-land-xhdpi")
    fun koreanIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "zh-w576dp-h432dp-land-xhdpi")
    fun chineseSimplifiedIsWhole() = assertWhole()

    @Test
    @Config(qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
    fun chineseTraditionalIsWhole() = assertWhole()

    private companion object {
        const val MAX_PADDING_SCROLL_PX = 10f
    }
}
