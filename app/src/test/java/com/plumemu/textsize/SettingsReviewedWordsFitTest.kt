package com.plumemu.textsize

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsModel
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.SystemEmulators
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.panelAt
import com.plumemu.settings.screen.withGamesListed
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The words that came out longest in Settings, at Largest in every font and language: Back up and restore's row and panel, the Updates
 * row's "0.1.3 available", Clear Recent's two buttons, an emulator list with none installed and the push-right row. None ellipsized,
 * cut sideways or on top of another text. Other texts of these screens are not this test's subject.
 */
abstract class SettingsReviewedWordsFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val release = UpdateManifest(41, "0.1.3", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val noEmulator = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = emptyList())

    private val stops: List<Pair<SettingsFocus, SettingsContent>>
        get() {
            val library = ShotContent.library
            val emulators = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0), page = PanelPage.Emulators, panelIndex = 0, systemId = "psx")
            // Clear Recent's place follows the rows that show only sometimes.
            val clearRecent = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Library, rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(library.withGamesListed()).indexOf(SettingsOption.ClearRecent)))
            return listOf(
                optionsAt(SettingsOption.BackupAndRestore) to library,
                panelAt(SettingsOption.BackupAndRestore, PanelPage.BackupRestore, 0) to library,
                optionsAt(SettingsOption.Updates) to library.copy(update = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release)),
                optionsAt(SettingsOption.Version) to library.copy(update = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release)),
                clearRecent to library,
                clearRecent.copy(column = SettingsColumn.Panel, page = PanelPage.Confirm, panelIndex = SettingsModel.CONFIRM_CANCEL) to library,
                emulators to library.copy(systems = listOf(noEmulator)),
                optionsAt(SettingsOption.PushRightToPlay) to library,
            )
        }

    @Test
    fun theyAreWholeAtLargest() {
        var focus by mutableStateOf(SettingsFocus(section = SettingsSection.Library))
        var content by mutableStateOf(SettingsContent())
        var font by mutableStateOf(PlumFont.Default)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) { SettingsAt(focus, content = content) } }
        val allow = FitAllowance(scrollTags = setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.PANEL, SettingsTags.PANEL_LIST))
        val failures = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val names =
            listOf(R.string.settings_backup_and_restore, R.string.settings_clear_recent_confirm, R.string.settings_emulator_automatic_none, R.string.settings_push_right).map(context::getString) +
                context.getString(R.string.settings_update_value_available, release.versionName)
        for (eachFont in PlumFont.entries) {
            for ((stop, shown) in stops) {
                font = eachFont
                focus = stop
                content = shown
                compose.waitForIdle()
                val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
                // The top bar's "Section › Row" gives way before the rows do in the wider fonts: it is held only in the default font.
                textProblems(root, allow)
                    .filter { problem -> names.any { "'$it'" in problem || (eachFont == PlumFont.Default && it in problem && " › " in problem) } }
                    .mapTo(failures) { "$eachFont ${stop.section}/${stop.column}/${stop.page}/${stop.rememberedOption[stop.section]}: $it" }
            }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.distinct().take(40).joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576EnglishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576SpanishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576FrenchTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576JapaneseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ko-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576KoreanTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576VietnameseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576ChineseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "zh-rTW-w576dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit576TraditionalChineseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit768EnglishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SettingsReviewedWordsFit640EnglishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w768dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit768SpanishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "es-w640dp-h480dp-land-xhdpi")
class SettingsReviewedWordsFit640SpanishTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w768dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit768FrenchTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "fr-w640dp-h480dp-land-xhdpi")
class SettingsReviewedWordsFit640FrenchTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w768dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit768VietnameseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "vi-w640dp-h480dp-land-xhdpi")
class SettingsReviewedWordsFit640VietnameseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w768dp-h432dp-land-xhdpi")
class SettingsReviewedWordsFit768JapaneseTest : SettingsReviewedWordsFitBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "ja-w640dp-h480dp-land-xhdpi")
class SettingsReviewedWordsFit640JapaneseTest : SettingsReviewedWordsFitBase()
