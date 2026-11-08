package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.NoticeLine
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › About's update rows by gamepad: where they are, what A does, the panel's buttons for each step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsUpdatesTest : SettingsModelHarness() {
    // Its own model over the harness's actions with About's update actions recorded: adding them to
    // the harness's FakeActions made the Compose compiler drop that class's `$stable`, and every
    // compose test that passes it failed with NoSuchFieldError.
    private val updates = FakeUpdateActions()
    // Delegating to the interface, not FakeActions, for the same reason: a delegate typed FakeActions
    // reads its missing `$stable` when this test class loads.
    private val delegate: SettingsActions get() = actions
    private val withUpdates = object : SettingsActions by delegate {
        override val updates: SettingsUpdateActions get() = this@SettingsUpdatesTest.updates
    }
    private val about = SettingsModel(withUpdates)
    private val updateCalls: List<String> get() = updates.calls

    private fun pressHere(action: Action) = about.onAction(action, actions.shown, wide)

    private fun openAbout() {
        // As the screen does: the first content arrives before any key (later ones keep the cursor's row).
        about.content = about.content
        while (about.focus.section.ordinal < SettingsSection.About.ordinal) pressHere(Action.Move(Direction.Down))
        pressHere(Action.Enter)
    }

    private fun focusHere(option: SettingsOption) {
        repeat(SettingsSection.About.options.size) { if (about.focusedOption != option) pressHere(Action.Move(Direction.Down)) }
        assertEquals(option, about.focusedOption)
    }
    private val release = UpdateManifest(41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val found = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release)

    @Test
    fun aboutHasOneUpdatesRowWhetherOrNotAnUpdateIsFound() {
        val rows = listOf(SettingsOption.Version, SettingsOption.Updates, SettingsOption.UsageStatistics, SettingsOption.Licenses, SettingsOption.Privacy, SettingsOption.ReportProblem)
        assertEquals(rows, SettingsSection.About.rowsIn(SettingsContent(hasStatistics = true, hasUpdates = true)))
        assertEquals(rows, SettingsSection.About.rowsIn(SettingsContent(hasStatistics = true, hasUpdates = true, update = found)))
    }

    private fun openPanel() {
        openAbout()
        focusHere(SettingsOption.Updates)
        pressHere(Action.Enter)
        assertEquals(PanelPage.Update, about.focus.page)
    }

    private fun itemAtCursor() = updatesPanelItems(about.content.update).getOrNull(about.focus.panelIndex)

    @Test
    fun theSwitchIsOnByDefaultAndFlipsTheStoredValue() {
        assertTrue(SettingsOption.CheckUpdatesDaily.switchValue(actions.settings))
        openPanel()
        pressHere(Action.Move(Direction.Down))
        assertEquals(UpdatePanelItem.CheckDaily, itemAtCursor())
        pressHere(Action.Enter)
        assertFalse(actions.settings.updates.checkDaily)
        pressHere(Action.Enter)
        assertTrue(actions.settings.updates.checkDaily)
    }

    @Test
    fun aOnCheckNowChecksNow() {
        openPanel()
        assertEquals(UpdatePanelItem.CheckNow, itemAtCursor())
        pressHere(Action.Enter)
        assertEquals(listOf("check"), updateCalls)
    }

    @Test
    fun theUpdatesRowOpensItsPanelOnUpdateAndUpdateStartsIt() {
        about.content = about.content.copy(update = found)
        openPanel()
        assertEquals(SettingsColumn.Panel, about.focus.column)
        assertEquals(UpdatePanelItem.Update, itemAtCursor())
        pressHere(Action.Enter)
        assertEquals(listOf("update"), updateCalls)
    }

    @Test
    fun theCursorStaysOnCheckNowWhenACheckFindsAnUpdate() {
        openPanel()
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found)
        assertEquals(UpdatePanelItem.CheckNow, itemAtCursor())
        pressHere(Action.Enter)
        assertEquals("A checks again, it does not start the update", listOf("check", "check"), updateCalls)
    }

    @Test
    fun anUpdateThatGoesLeavesThePanelOnItsFirstItem() {
        about.content = about.content.copy(update = found)
        openPanel()
        about.content = about.content.copy(update = UpdateView())
        assertEquals(PanelPage.Update, about.focus.page)
        assertEquals(UpdatePanelItem.CheckNow, itemAtCursor())
    }

    @Test
    fun eachStepHasItsOwnButtons() {
        fun items(step: UpdateStep) = updatePanelItems(found.copy(step = step))
        assertEquals(listOf(UpdatePanelItem.Update), items(UpdateStep.Idle))
        assertEquals(listOf(UpdatePanelItem.Update), items(UpdateStep.Failed(UpdateFailure.HashMismatch)))
        assertEquals(listOf(UpdatePanelItem.DownloadAnyway, UpdatePanelItem.Cancel), items(UpdateStep.AskMetered))
        assertEquals(listOf(UpdatePanelItem.AllowInstalls, UpdatePanelItem.Cancel), items(UpdateStep.NeedsPermission))
        assertEquals(listOf(UpdatePanelItem.Cancel), items(UpdateStep.Downloading(1, 2)))
        assertEquals(emptyList<UpdatePanelItem>(), items(UpdateStep.Installing()))
        assertEquals(listOf(UpdatePanelItem.Cancel), items(UpdateStep.NoPermissionScreen))
        // While a check runs nothing is offered: the line says "Checking for updates…".
        assertEquals(emptyList<UpdatePanelItem>(), updatePanelItems(found.copy(isChecking = true, step = UpdateStep.Failed(UpdateFailure.Offline))))
        assertEquals(listOf(UpdatePanelItem.Cancel), updatePanelItems(found.copy(isChecking = true, step = UpdateStep.Downloading(1, 2))))
        assertTrue(found.copy(step = UpdateStep.Downloading(1, 2)).isUpdateRunning)
        // Android 6 sends nothing when its "Install blocked" dialog is cancelled: never stuck.
        assertEquals(listOf(UpdatePanelItem.Update), items(UpdateStep.Installing(askedPerson = true)))
    }

    @Test
    fun onMobileDataTheFirstButtonDownloadsAnywayAndTheCursorStartsOnIt() {
        about.content = about.content.copy(update = found)
        openPanel()
        about.content = about.content.copy(update = found.copy(step = UpdateStep.AskMetered))
        assertEquals(0, about.focus.panelIndex)
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found.copy(step = UpdateStep.NeedsPermission))
        pressHere(Action.Enter)
        pressHere(Action.Move(Direction.Down))
        pressHere(Action.Enter)
        assertEquals(listOf("update metered", "allow", "cancel"), updateCalls)
    }

    private val off = found.copy(notice = NoticeLine.NotificationsOff)

    @Test
    fun turnOnNotificationsShowsOnlyWhileNotificationsAreOff() {
        for (notice in listOf(NoticeLine.None, NoticeLine.Reason)) {
            assertFalse("$notice", UpdatePanelItem.TurnOnNotifications in updatesPanelItems(found.copy(notice = notice)))
        }
        assertEquals(listOf(UpdatePanelItem.Update, UpdatePanelItem.TurnOnNotifications, UpdatePanelItem.CheckNow, UpdatePanelItem.CheckDaily), updatesPanelItems(off))
        assertEquals(listOf(UpdatePanelItem.Cancel, UpdatePanelItem.TurnOnNotifications), updateStepItems(off.copy(step = UpdateStep.Downloading(1, 2))))
        assertEquals("nothing found: no update line, no button", emptyList<UpdatePanelItem>(), updateStepItems(UpdateView(notice = NoticeLine.NotificationsOff)))
    }

    @Test
    fun theKeysReachTurnOnNotificationsAndAOpensTheSettings() {
        about.content = about.content.copy(update = off)
        openPanel()
        assertEquals(UpdatePanelItem.Update, itemAtCursor())
        pressHere(Action.Move(Direction.Down))
        assertEquals(UpdatePanelItem.TurnOnNotifications, itemAtCursor())
        pressHere(Action.Enter)
        assertEquals(listOf("notifications"), updateCalls)
        pressHere(Action.Move(Direction.Up))
        assertEquals(UpdatePanelItem.Update, itemAtCursor())
    }

    @Test
    fun aTapOpensTheNotificationSettings() {
        about.content = about.content.copy(update = off)
        openPanel()
        val index = updatesPanelItems(off).indexOf(UpdatePanelItem.TurnOnNotifications)
        // Like every button: the first tap focuses it, the second uses it.
        about.onTapPanelItem(index, actions.shown, wide)
        assertEquals(emptyList<String>(), updateCalls)
        about.onTapPanelItem(index, actions.shown, wide)
        assertEquals(listOf("notifications"), updateCalls)
    }

    @Test
    fun theButtonGoesWhenNotificationsAreOnAndTheCursorIsNotLeftOnIt() {
        about.content = about.content.copy(update = off)
        openPanel()
        pressHere(Action.Move(Direction.Down))
        assertEquals(UpdatePanelItem.TurnOnNotifications, itemAtCursor())
        about.content = about.content.copy(update = found)
        assertFalse(itemAtCursor() == UpdatePanelItem.TurnOnNotifications)
        assertFalse(UpdatePanelItem.TurnOnNotifications in updatesPanelItems(about.content.update))
    }

    @Test
    fun whileItDownloadsTheOnlyButtonIsCancel() {
        about.content = about.content.copy(update = found)
        openPanel()
        about.content = about.content.copy(update = found.copy(step = UpdateStep.Downloading(1_000, 9_800_000)))
        pressHere(Action.Enter)
        assertEquals(listOf("cancel"), updateCalls)
    }

    @Test
    fun upAndDownPastTheButtonsScrollTheNotesWithoutMovingFocus() {
        about.content = about.content.copy(update = found)
        openPanel()
        val before = about.updateNotes.presses
        pressHere(Action.Move(Direction.Down))
        pressHere(Action.Move(Direction.Down))
        pressHere(Action.Move(Direction.Up))
        assertEquals(before + 1, about.updateNotes.presses)
        assertEquals(PanelPage.Update, about.focus.page)
        assertEquals(0, about.focus.panelIndex)
    }

    @Test
    fun atTheNotesEndDownGoesOnToCheckNowAndTheSwitch() {
        about.content = about.content.copy(update = found)
        openPanel()
        about.updateNotes.canScroll = { false }
        val before = about.updateNotes.presses
        pressHere(Action.Move(Direction.Down))
        assertEquals(UpdatePanelItem.CheckNow, itemAtCursor())
        pressHere(Action.Move(Direction.Down))
        assertEquals(UpdatePanelItem.CheckDaily, itemAtCursor())
        assertEquals(before, about.updateNotes.presses)
    }

    private val dailyIndex get() = updatesPanelItems(about.content.update).indexOf(UpdatePanelItem.CheckDaily)

    @Test
    fun oneTapChangesTheDailySwitchInTheOpenPanel() {
        openPanel()
        val before = actions.settings
        about.onTapPanelItem(dailyIndex, actions.shown, wide)
        assertEquals(UpdatePanelItem.CheckDaily, itemAtCursor())
        assertFalse("the first tap flips it", SettingsOption.CheckUpdatesDaily.switchValue(actions.settings) == SettingsOption.CheckUpdatesDaily.switchValue(before))
    }

    @Test
    fun oneTapChangesTheDailySwitchBesideItsRowAndBesideAnUpdate() {
        about.content = about.content.copy(update = found)
        openAbout()
        focusHere(SettingsOption.Updates)
        assertEquals(SettingsColumn.Options, about.focus.column)
        val before = actions.settings
        about.onTapPanelItem(dailyIndex, actions.shown, wide)
        assertEquals(PanelPage.Update, about.focus.page)
        assertFalse(SettingsOption.CheckUpdatesDaily.switchValue(actions.settings) == SettingsOption.CheckUpdatesDaily.switchValue(before))
    }

    @Test
    fun aTapOnCheckNowStillTakesTwoLikeEveryButton() {
        openPanel()
        val checkNow = updatesPanelItems(about.content.update).indexOf(UpdatePanelItem.CheckNow)
        about.onTapPanelItem(dailyIndex, actions.shown, wide)
        about.onTapPanelItem(checkNow, actions.shown, wide)
        assertTrue(updateCalls.isEmpty())
        about.onTapPanelItem(checkNow, actions.shown, wide)
        assertEquals(1, updateCalls.size)
    }

    @Test
    fun thePillOpensThePanelOnUpdateOnlyWhileOneWaits() {
        val before = about.focus
        assertFalse(about.openUpdate(actions.shown, wide))
        assertEquals(before, about.focus)
        about.content = about.content.copy(update = found)
        assertTrue(about.openUpdate(actions.shown, wide))
        assertEquals(PanelPage.Update, about.focus.page)
        assertEquals(UpdatePanelItem.Update, itemAtCursor())
    }

    @Test
    fun withTwoButtonsDownMovesToTheSecondBeforeScrolling() {
        about.content = about.content.copy(update = found.copy(step = UpdateStep.AskMetered))
        openPanel()
        val before = about.updateNotes.presses
        pressHere(Action.Move(Direction.Down))
        assertEquals(1, about.focus.panelIndex)
        assertEquals(before, about.updateNotes.presses)
        pressHere(Action.Move(Direction.Down))
        assertEquals(before + 1, about.updateNotes.presses)
    }
}

/** Records About's update actions by name. */
internal class FakeUpdateActions : SettingsUpdateActions {
    val calls = mutableListOf<String>()

    override fun checkNow() {
        calls += "check"
    }

    override fun startUpdate(allowMetered: Boolean) {
        calls += if (allowMetered) "update metered" else "update"
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun openInstallPermission(): Boolean {
        calls += "allow"
        return true
    }

    override fun openNotificationSettings(): Boolean {
        calls += "notifications"
        return true
    }
}
