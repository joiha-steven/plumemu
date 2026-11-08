package com.plumemu.textsize

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.panelAt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' section titles and its longest row names whole in every language, wherever they show (rows, the sections column, panel
 * titles, the top bar): at the default size and at Largest in the default font, at 768x432, 576x432 and 832x468, with the sections
 * focused, each row focused, and the Game folders panel open. None ellipsized, clipped sideways or on top of another text. Other texts
 * of these screens are not this test's subject.
 */
abstract class SettingsLongNamesFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun everyRowIsWholeAtTheDefaultSizeAndAtLargest() {
        val content =
            ShotContent.library.withSecondScreen(SecondScreen(2, "Overlay #1", ScreenKind.BuiltIn), SecondScreenSettings(enabled = true))
        var size by mutableStateOf(TextSize.Medium)
        var focus by mutableStateOf(SettingsFocus(section = SettingsSection.Library))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(focus, content = content) } }
        val stops = mutableListOf(panelAt(SettingsOption.RomFolders, PanelPage.Folders, 0))
        for (section in SettingsSection.shownIn(content)) {
            stops += SettingsFocus(column = SettingsColumn.Sections, section = section)
            val rows = section.rowsIn(content)
            rows.indices.forEach { stops += SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to it)) }
        }
        val failures = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val names =
            listOf(R.string.settings_rom_folders, R.string.settings_left_edge, R.string.settings_sounds, R.string.settings_second_screen_in_game)
                .map(context::getString) + SettingsSection.entries.map { context.getString(it.title) }
        val allow = FitAllowance(scrollTags = setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL))
        for (each in listOf(TextSize.Medium, TextSize.Largest)) {
            size = each
            for (stop in stops) {
                focus = stop
                compose.waitForIdle()
                val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                val label = "${stop.section}/${stop.column}/${stop.rememberedOption[stop.section] ?: 0} at ${each.name}"
                textProblems(root, allow).filter { problem -> names.any { "'$it'" in problem || (it in problem && " › " in problem) } }.mapTo(failures) { "$label: $it" }
            }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.distinct().take(40).joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768EnglishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768SpanishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768FrenchTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768VietnameseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768JapaneseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768KoreanTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768ChineseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w768dp-h432dp-land-xhdpi")
class SettingsLongNamesFit768TraditionalChineseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576EnglishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576SpanishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576FrenchTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576VietnameseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576JapaneseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576KoreanTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576ChineseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
class SettingsLongNamesFit576TraditionalChineseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832EnglishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832SpanishTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832FrenchTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832VietnameseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832JapaneseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832KoreanTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832ChineseTest : SettingsLongNamesFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w832dp-h468dp-land-xhdpi")
class SettingsLongNamesFit832TraditionalChineseTest : SettingsLongNamesFitBase()
