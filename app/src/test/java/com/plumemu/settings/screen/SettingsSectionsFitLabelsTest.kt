package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SettingsColumns
import com.plumemu.coreui.theme.SettingsThreeDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sections column is wide enough for its longest label, whole on one line, in every language at every text size and font that keeps
 * three columns, with the labels starting after the slab's accent bar and [LeftColumnDimens.systemLabelEndClear] clear of the divider.
 * The label's natural width is measured (intrinsic width of its laid-out text), not assumed; the open section's label (further in for its
 * dot) is measured too. 768 dp is the narrowest wide screen.
 */
abstract class SettingsSectionsFitLabelsBase(private val isWide: Boolean) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun everyLabelFitsWithTheSlabInset() {
        var size by mutableStateOf(TextSize.Small)
        var font by mutableStateOf(PlumFont.Jost)
        var focus by mutableStateOf(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library))
        var density = Density(1f)
        var three = true
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                density = LocalDensity.current
                three = PlumTheme.layout.isSettingsThreeColumns
                SettingsAt(focus, content = ShotContent.library)
            }
        }
        val problems = mutableListOf<String>()
        for (eachFont in PlumFont.entries) {
            // Three columns (768): every size that keeps them. Otherwise (two columns, 576): Medium and Large in Jost and Sans.
            val sizes =
                when {
                    isWide && SettingsColumns.fits(TextSize.Small, eachFont) -> TextSize.entries.filter { SettingsColumns.fits(it, eachFont) }
                    eachFont == PlumFont.Jost || eachFont == PlumFont.Sans -> listOf(TextSize.Small, TextSize.Large)
                    else -> emptyList()
                }
            for (each in sizes) {
                font = eachFont
                size = each
                for (column in listOf(SettingsColumn.Sections, SettingsColumn.Options)) {
                    for (section in SettingsSection.shownIn(ShotContent.library)) {
                        focus = SettingsFocus(column = column, section = section)
                        compose.waitForIdle()
                        val dp = with(density) { 1.dp.toPx() }
                        for (node in compose.onAllNodesWithTag(SystemRowTags.LABEL, useUnmergedTree = true).fetchSemanticsNodes()) {
                            val results = mutableListOf<TextLayoutResult>()
                            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                            val needs = results.firstOrNull()?.multiParagraph?.intrinsics?.maxIntrinsicWidth ?: continue
                            val columnRight = if (three) SettingsThreeDimens.sectionsWidth else PlumDimens.stripMarginSide + PlumDimens.settingsSectionsWidth - PlumDimens.systemRowPadding
                            val has = columnRight.value * dp - (node.boundsInRoot.left + LeftColumnDimens.systemLabelEndClear.value * dp)
                            if (needs > has + 0.5f) {
                                problems += "${each.name} ${eachFont.name} $column/$section: label needs ${needs / dp} dp, has ${has / dp} dp"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.distinct().take(20).joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768EnglishTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768SpanishTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768FrenchTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768VietnameseTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768JapaneseTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768KoreanTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768ChineseTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w768dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels768TraditionalChineseTest : SettingsSectionsFitLabelsBase(true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576EnglishTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576SpanishTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576FrenchTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576VietnameseTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576JapaneseTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576KoreanTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576ChineseTest : SettingsSectionsFitLabelsBase(false)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
class SettingsSectionsFitLabels576TraditionalChineseTest : SettingsSectionsFitLabelsBase(false)
