package com.plumemu.settings.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.graph.BackupState
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A button of a side panel at rest is never cut through its label by the panel's edge: it is whole, or no more than the fade band
 * ([PlumDimens.listFadeBand], which the fade covers) of it shows. Where the panel rests decides it: buttons are stops of the rest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsPanelButtonsRestTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val signedIn = AccountStatus.SignedIn("plumtester")
    private val pageSettings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private fun open(
        qualifiers: String,
        focus: SettingsFocus,
        content: SettingsContent,
        textSize: TextSize = TextSize.Small,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = false, textSize = textSize)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, focus).also { it.content = content } }
                SettingsLayout(pageSettings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    /** Every button of the open panel that is cut by its edge by more than the fade band. */
    private fun problems(at: String): List<String> {
        val density = Density(RuntimeEnvironment.getApplication().resources.displayMetrics.density)
        val panel = compose.onNodeWithTag(SettingsTags.PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val peek = with(density) { PlumDimens.listFadeBand.toPx() }
        val isButton = SemanticsMatcher("is a button") { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
        val buttons = compose.onAllNodes(isButton and hasAnyAncestor(hasTestTag(SettingsTags.PANEL)), useUnmergedTree = true).fetchSemanticsNodes()
        return buttons.mapIndexedNotNull { index, node ->
            // Unclipped: boundsInRoot stops at the panel's edge and would call a cut button whole.
            val box = Rect(node.positionInRoot, node.size.toSize())
            if (box.height <= 0f) return@mapIndexedNotNull null
            val shown = minOf(box.bottom, panel.bottom) - maxOf(box.top, panel.top)
            if (shown >= box.height - 1f || shown <= peek) null else "$at: button $index ${box.top}..${box.bottom} is cut by the panel ${panel.top}..${panel.bottom}: ${shown}px of ${box.height}px show"
        }
    }

    private fun check(
        focus: SettingsFocus,
        content: SettingsContent,
        textSize: TextSize = TextSize.Small,
        downs: Int = 1,
    ) {
        // Only the person's moves keep buttons whole: the panel as opened may have one cut (titleStaysAtTheTop).
        val found = mutableListOf<String>()
        for (size in SIZES) {
            open(size, focus, content, textSize)
            repeat(downs) { n ->
                compose.runOnIdle { model.onAction(Action.Move(Direction.Down), pageSettings, geometry) }
                compose.waitForIdle()
                found += problems("$size after Down ${n + 1}")
            }
        }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /** The top of the highest text in the open panel above the panel's top, dp: 0 when its title is first on the panel's first line. */
    private fun titleDrop(tag: String): Float {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val panel = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        return (texts.minOf { it.positionInRoot.y } - panel.top) / density
    }

    private fun titleStaysAtTheTop(
        focus: SettingsFocus,
        content: SettingsContent,
        textSize: TextSize = TextSize.Small,
        qualifiers: List<String> = SIZES,
        tag: String = SettingsTags.PANEL,
    ) {
        val found = qualifiers.mapNotNull { size ->
            open(size, focus, content, textSize)
            val drop = titleDrop(tag)
            if (drop in -1f..TITLE_ROOM_DP) null else "$size: the panel's first text starts $drop dp from its top, not within $TITLE_ROOM_DP"
        }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun anOpenedPanelShowsItsTitleAtTheTop() {
        titleStaysAtTheTop(backupFocus, backupDone, qualifiers = listOf(SIZES[1]))
        titleStaysAtTheTop(boxArtFocus, boxArtDaily, qualifiers = listOf(SIZES[0]))
    }

    @Test
    fun aPreviewedPanelShowsItsTitleAtTheTop() {
        // Beside the list (768 and up): the scroll column of the panel in the right column.
        titleStaysAtTheTop(AccountShotContent.focusOn(boxArtDaily, SettingsOption.ScraperAccount), boxArtDaily, qualifiers = listOf(SIZES[1]), tag = SettingsTags.ACCOUNT_PANEL)
    }

    @Test
    fun theRefusedRaPageShowsItsTitleAtTheTopAtLarger() {
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true))
        val row = SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Accounts, rememberedOption = mapOf(SettingsSection.Accounts to row), page = PanelPage.RaAccount)
        titleStaysAtTheTop(focus, content, TextSize.Larger, listOf(SIZES[1]))
    }

    private val backupFocus = panelAt(SettingsOption.Backup, PanelPage.Backup, 0)
    private val backupContent = SettingsContent(account = signedIn)
    private val backupDone = backupContent.copy(backup = BackupState.Done(BackupKind.Everything, "Plumemu backup 2026-10-23.zip", 412_000_000))
    private val boxArtDaily = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.dailyLimit)
    private val boxArtFocus = AccountShotContent.focusOn(boxArtDaily, SettingsOption.ScraperAccount, PanelPage.ScraperAccount)

    @Test
    fun backupDone() = check(backupFocus, backupDone)

    @Test
    fun backupFailed() = check(backupFocus, backupContent.copy(backup = BackupState.Failed(deleted = true)))

    @Test
    fun boxArtPanelDailyLimit() = check(boxArtFocus, boxArtDaily)

    @Test
    fun boxArtPanelRunning() {
        val content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running)
        check(AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperAccount), content)
    }

    @Test
    fun boxArtSystemsSomeChosen() {
        val content = BoxArtShots.longSystems
        check(AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperSystems).copy(panelIndex = 0, chosenSystems = setOf("nes", "fds")), content)
    }

    @Test
    fun backupDoneAtLargerText() = check(backupFocus, backupDone, TextSize.Larger)

    @Test
    fun boxArtPanelAtLargerText() = check(boxArtFocus, boxArtDaily, TextSize.Larger)

    @Test
    fun keysDownThroughTheButtonsLeaveEachRestWhole() {
        check(backupFocus, backupDone, downs = 1)
        check(boxArtFocus.copy(panelIndex = 0), boxArtDaily, downs = 4)
    }

    private val boxArtListFocus = AccountShotContent.focusOn(boxArtDaily, SettingsOption.ScraperAccount)

    private fun assertTitleAtTop(
        what: String,
        tag: String,
    ) {
        val drop = titleDrop(tag)
        assertTrue("$what: the panel's first text starts $drop dp from its top, not within $TITLE_ROOM_DP", drop in -1f..TITLE_ROOM_DP)
    }

    private fun press(action: Action) {
        compose.runOnIdle { model.onAction(action, pageSettings, geometry) }
        compose.waitForIdle()
    }

    @Test
    fun enteringAPreviewedPanelKeepsItsTitleAtTheTop() {
        open(SIZES[1], boxArtListFocus, boxArtDaily)
        assertTitleAtTop("previewed", SettingsTags.ACCOUNT_PANEL)
        press(Action.Move(Direction.Right))
        assertTrue("Right did not enter the panel", model.focus.column == SettingsColumn.Panel)
        assertTitleAtTop("entered", SettingsTags.PANEL)
    }

    @Test
    fun enteringAPanelAgainAfterLeavingItKeepsItsTitleAtTheTop() {
        open(SIZES[1], boxArtListFocus, boxArtDaily)
        press(Action.Move(Direction.Right))
        press(Action.Back)
        assertTrue("Back did not leave the panel", model.focus.column != SettingsColumn.Panel)
        press(Action.Move(Direction.Right))
        assertTrue("Right did not enter the panel", model.focus.column == SettingsColumn.Panel)
        assertTitleAtTop("re-entered", SettingsTags.PANEL)
    }

    @Test
    fun openingAPanelAfterKeyMovesInAnEarlierOneKeepsItsTitleAtTheTop() {
        // At 576 the panel replaces the list: opening it again makes a new panel, which must not take the earlier moves for its own.
        open(SIZES[0], boxArtFocus.copy(panelIndex = 0), boxArtDaily)
        press(Action.Move(Direction.Down))
        press(Action.Back)
        assertTrue("Back did not leave the panel", model.focus.column != SettingsColumn.Panel)
        press(Action.Move(Direction.Right))
        assertTrue("Right did not open the panel", model.focus.column == SettingsColumn.Panel)
        assertTitleAtTop("opened again", SettingsTags.PANEL)
    }

    @Test
    fun aPanelWhoseContentChangesUnderTheFocusKeepsItsTitleAtTheTop() {
        open(SIZES[1], boxArtFocus.copy(panelIndex = 0), boxArtDaily)
        assertTitleAtTop("opened", SettingsTags.PANEL)
        compose.runOnIdle { model.content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running) }
        compose.waitForIdle()
        assertTitleAtTop("after the content changed", SettingsTags.PANEL)
    }

    private lateinit var scroll: ScrollState
    private lateinit var scope: CoroutineScope
    private var presses by mutableIntStateOf(0)

    /** Down on a column of text blocks after a button goes on forward where the rest needs the column to grow at its foot, as it does with no button. */
    @Test
    fun aKeyDownStillGoesForwardWhereTheFootGrowsForTheRest() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = true)) {
                scroll = rememberScrollState()
                scope = rememberCoroutineScope()
                Box(Modifier.width(300.dp).height(COLUMN_DP.dp)) {
                    PanelScrollColumn(GAP_DP.dp, scroll = scroll, tag = "column", keyMoves = presses) {
                        SetupButton("Button", isPrimary = false, isFocused = false, modifier = Modifier.fillMaxWidth().bringIntoViewWhen(false))
                        repeat(BLOCKS) { Box(Modifier.panelBlock().fillMaxWidth().height(BLOCK_DP.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        var at = 0
        repeat(PRESSES) { n ->
            compose.runOnIdle {
                presses++
                scope.launch { scroll.animateScrollTo(scroll.value + (BLOCK_DP / 2 * density).toInt()) }
            }
            compose.waitForIdle()
            assertTrue("Down $n moved back: $at to ${scroll.value}", scroll.value >= at)
            at = scroll.value
        }
        val third = ((BLOCK_DP + GAP_DP) * 2 * density).toInt()
        assertTrue("Down reached the end part: $at", at >= third - (EDGE_DP * density).toInt())
    }

    private companion object {
        val SIZES = listOf("w576dp-h432dp-land-xhdpi", "w768dp-h432dp-land-xhdpi")
        const val COLUMN_DP = 300
        const val BLOCK_DP = 200
        const val GAP_DP = 12
        const val EDGE_DP = 36
        const val BLOCKS = 3
        const val PRESSES = 4
        const val TITLE_ROOM_DP = 16f
    }
}
