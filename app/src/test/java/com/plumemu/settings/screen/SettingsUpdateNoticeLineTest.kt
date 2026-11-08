package com.plumemu.settings.screen

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoticeLine
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Updates panel's notice line: Home is named only when Plumemu is the home app; nothing before the press or with notifications on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsUpdateNoticeLineTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val release = UpdateManifest(41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update, panelIndex = 0)

    private fun show(
        notice: NoticeLine,
        isHomeApp: Boolean,
    ) = compose.setContent {
        PlumTheme(PlumThemeSettings.Graphite) {
            SettingsAt(focus, content = SettingsContent(isHomeApp = isHomeApp, update = UpdateView(LastCheck(1, CheckOutcome.Found), release, notice = notice)))
        }
    }

    private fun line() = compose.onNodeWithTag(SettingsPanelTags.UPDATE_NOTICE, useUnmergedTree = true)

    @Test
    fun theHomeAppIsToldToPressHome() {
        show(NoticeLine.NotificationsOff, isHomeApp = true)
        line().assertTextEquals(context.getString(R.string.settings_update_notice_off))
    }

    @Test
    fun anotherHomeAppIsToldToOpenPlumemuFromItsApps() {
        show(NoticeLine.NotificationsOff, isHomeApp = false)
        line().assertTextEquals(context.getString(R.string.settings_update_notice_off_not_home))
    }

    @Test
    fun theReasonDoesNotDependOnTheHomeApp() {
        show(NoticeLine.Reason, isHomeApp = false)
        line().assertTextEquals(context.getString(R.string.settings_update_notice_reason))
    }

    @Test
    fun noLineBeforeThePress() {
        show(NoticeLine.None, isHomeApp = false)
        compose.onAllNodesWithTag(SettingsPanelTags.UPDATE_NOTICE, useUnmergedTree = true).assertCountEquals(0)
    }
}
