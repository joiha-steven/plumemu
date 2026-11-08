package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.R
import com.plumemu.coreui.components.SystemRow
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The systems column keeps its labels where they were (the slab's overhang took none of their room), so a name fits whole or ends in an
 * ellipsis, never a hard clip: "Game Boy Advance", the longest catalog names ("Neo Geo Pocket Color", "Sinclair ZX Spectrum") and the
 * three fixed entries in each language, focused (the boldest face), at Medium and Large in Jost and Sans.
 */
abstract class SystemsNamesFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun everyNameFits() {
        var size by mutableStateOf(TextSize.Small)
        var names = emptyList<String>()
        var name by mutableStateOf("")
        compose.setContent {
            names = listOf("Game Boy Advance", "Neo Geo Pocket Color", "Sinclair ZX Spectrum", stringResource(R.string.home_entry_recent), stringResource(R.string.home_entry_favorites), stringResource(R.string.home_entry_apps))
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                Box(Modifier.width(PlumDimens.stripMarginSide + PlumTheme.layout.systemsColumnWidth)) {
                    SystemRow(name, "248", SystemRowLook.Focused, showName = true, bleedStart = LeftColumnDimens.systemLabelStart)
                }
            }
        }
        val density = compose.density.density
        val problems = mutableListOf<String>()
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in listOf(TextSize.Small, TextSize.Large)) {
                size = each
                for (shown in names) {
                    name = shown
                    compose.waitForIdle()
                    val node = compose.onNodeWithTag(SystemRowTags.LABEL, useUnmergedTree = true).fetchSemanticsNode()
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    val layout = results.firstOrNull() ?: continue
                    val needs = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                    val has = node.boundsInRoot.width
                    // Too long for the room: the text must end in an ellipsis, not be cut off by the box.
                    if (needs > has + 0.5f && !layout.isLineEllipsized(0)) problems += "${each.name} ${eachFont.name} '$shown' needs ${needs / density} dp, has ${has / density} dp and is not ellipsized"
                }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768EnglishTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768SpanishTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768FrenchTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768VietnameseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768JapaneseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768KoreanTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768ChineseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w768dp-h432dp-land-xhdpi")
class SystemsNamesFit768TraditionalChineseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576EnglishTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576SpanishTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576FrenchTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576VietnameseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576JapaneseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576KoreanTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576ChineseTest : SystemsNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
class SystemsNamesFit576TraditionalChineseTest : SystemsNamesFitBase()
