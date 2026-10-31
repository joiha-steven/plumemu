package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.BackupState
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.panelAt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Advanced › Back up…'s panel at every text size, the way `SettingsStatisticsFitTest`
 * checks Statistics: at the six sizes in Plum Sans and at Large and Largest in Serif and Mono, no
 * text is ellipsized, clipped, too short or on top of another (the panel scrolls), and the focused
 * button is fully on screen: the choice with the password warning, the progress, a long file name.
 */
abstract class SettingsBackupFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val signedIn = SettingsContent(account = AccountStatus.SignedIn("plumtester"))

    private fun check(
        name: String,
        focusedIndex: Int,
        content: SettingsContent,
    ) = check(name, focusedIndex) { SettingsAt(panelAt(SettingsOption.Backup, PanelPage.Backup, focusedIndex), content = content) }

    private fun check(
        name: String,
        focusedIndex: Int,
        screen: @Composable () -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { screen() } }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            val allowance = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.BACKUP_PANEL), isLargest = each == TextSize.Largest)
            textProblems(root, allowance).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, SettingsTags.panelItem(focusedIndex))
            if (focused == null) failures += "$label: the focused button is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused button $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theChoiceWithThePasswordWarning() = check("backup_choice", 1, signedIn)

    @Test
    fun theProgressWithCancel() = check("backup_running", 0, signedIn.copy(backup = BackupState.Running(BackupKind.Everything, BackupProgress(12_345, 54_321))))

    @Test
    fun aLongFileName() =
        check("backup_done", 1, signedIn.copy(backup = BackupState.Done(BackupKind.Everything, "Plumemu backup 2026-10-23 (12).zip", 1_234_567_890)))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsBackupFit768Test : SettingsBackupFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsBackupFit667Test : SettingsBackupFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsBackupFit576Test : SettingsBackupFitBase(576, 432)
