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
import com.plumemu.graph.AccountInFile
import com.plumemu.graph.RestoreProblem
import com.plumemu.graph.RestoreState
import com.plumemu.graph.RestoreSummary
import com.plumemu.graph.WayBackSet
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.UndoPanelItem
import com.plumemu.settings.screen.panelAt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Advanced › Restore… and Undo last restore's panels at every text size, the way
 * `SettingsBackupFitTest` checks Back up…: at the six sizes in Plum Sans and at Large and Largest in
 * Serif and Mono, no text is ellipsized, clipped, too short or on top of another (the panels
 * scroll), and the focused button is fully on screen: the longest confirm, the progress, a
 * refusal, a result, and the Undo panel.
 */
abstract class SettingsRestoreFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val signedIn = SettingsContent(account = AccountStatus.SignedIn("plumtester"))
    private val wayBack = WayBackSet(BackupKind.Everything, 1_792_843_200_000L, 1_234_567_890)

    private fun check(
        name: String,
        focusedIndex: Int,
        content: SettingsContent,
    ) = check(name, focusedIndex, SettingsTags.RESTORE_PANEL) { SettingsAt(panelAt(SettingsOption.Restore, PanelPage.Restore, focusedIndex), content = content) }

    private fun check(
        name: String,
        focusedIndex: Int,
        panelTag: String,
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
            val allowance = FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, panelTag), isLargest = each == TextSize.Largest)
            textProblems(root, allowance).mapTo(failures) { "$label: $it" }
            if (focusedIndex >= 0) {
                val focused = findTagged(root, SettingsTags.panelItem(focusedIndex))
                if (focused == null) failures += "$label: the focused button is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused button $it" }
            }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theLongestConfirm() =
        check(
            "restore_confirm",
            1,
            signedIn.copy(restore = RestoreState.Confirm(RestoreSummary(BackupKind.Everything, 1_792_756_800_000L, games = 12_345, pictures = 54_321, account = AccountInFile.SignsIn))),
        )

    @Test
    fun aConfirmWhoseFileHasNoPassword() =
        check(
            "restore_confirm_sign_in_again",
            0,
            signedIn.copy(restore = RestoreState.Confirm(RestoreSummary(BackupKind.Settings, 1_792_756_800_000L, games = 0, pictures = 0, account = AccountInFile.SignInAgain))),
        )

    @Test
    fun theReadingWithCancel() = check("restore_reading", 0, signedIn.copy(restore = RestoreState.Reading(BackupProgress(12_345, 54_321))))

    @Test
    fun aRefusal() = check("restore_refused", 0, signedIn.copy(restore = RestoreState.Refused(RestoreProblem.NewerFormat)))

    @Test
    fun theLongestResult() = check("restore_done", 0, signedIn.copy(restore = RestoreState.Done(isUndo = false, BackupKind.Everything, 1_792_756_800_000L)))

    @Test
    fun theLongestResultWithDamagedPicturesLeftOut() =
        check("restore_done_damaged", 0, signedIn.copy(restore = RestoreState.Done(isUndo = false, BackupKind.Everything, 1_792_756_800_000L, damagedMediaFiles = 12_345)))

    @Test
    fun theUndoPanel() {
        val content = signedIn.copy(wayBack = wayBack)
        val index = SettingsSection.Advanced.rowsIn(content).indexOf(SettingsOption.UndoRestore)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Advanced, rememberedOption = mapOf(SettingsSection.Advanced to index), page = PanelPage.UndoRestore, panelIndex = 1)
        check("undo_restore", 1, SettingsTags.UNDO_PANEL) { SettingsAt(focus, content = content) }
    }

    private fun checkUndoAsk(ask: UndoPanelItem) {
        val content = signedIn.copy(wayBack = wayBack)
        val index = SettingsSection.Advanced.rowsIn(content).indexOf(SettingsOption.UndoRestore)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Advanced, rememberedOption = mapOf(SettingsSection.Advanced to index), page = PanelPage.UndoRestore, panelIndex = 1, undoAsk = ask)
        check("undo_ask_${ask.name}", 1, SettingsTags.UNDO_PANEL) { SettingsAt(focus, content = content) }
    }

    @Test
    fun theUndoQuestion() = checkUndoAsk(UndoPanelItem.Undo)

    @Test
    fun theForgetQuestion() = checkUndoAsk(UndoPanelItem.Forget)

    @Test
    fun aConfirmThatReplacesTheKeptCopy() =
        check(
            "restore_confirm_replaces_copy",
            1,
            signedIn.copy(
                restore = RestoreState.Confirm(RestoreSummary(BackupKind.Everything, 1_792_756_800_000L, games = 12_345, pictures = 54_321, account = AccountInFile.SignsIn)),
                wayBack = wayBack,
            ),
        )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsRestoreFit768Test : SettingsRestoreFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsRestoreFit667Test : SettingsRestoreFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsRestoreFit576Test : SettingsRestoreFitBase(576, 432)
