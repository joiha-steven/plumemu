package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
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
        while (about.focusedOption != option) pressHere(Action.Move(Direction.Down))
    }
    private val release = UpdateManifest(41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null)
    private val found = UpdateView(lastCheck = LastCheck(1, CheckOutcome.Found), found = release)

    @Test
    fun aboutListsTheCheckAndTheSwitchAndUpdateAvailableOnlyWhileFound() {
        val rows = SettingsSection.About.rowsIn(SettingsContent())
        assertEquals(listOf(SettingsOption.Version, SettingsOption.CheckForUpdates, SettingsOption.CheckUpdatesDaily, SettingsOption.Licenses, SettingsOption.Privacy, SettingsOption.ReportProblem), rows)
        val withUpdate = SettingsSection.About.rowsIn(SettingsContent(update = found))
        assertEquals(SettingsOption.UpdateAvailable, withUpdate[1])
        assertEquals(rows.size + 1, withUpdate.size)
    }

    @Test
    fun theSwitchIsOnByDefaultAndFlipsTheStoredValue() {
        assertTrue(SettingsOption.CheckUpdatesDaily.switchValue(actions.settings))
        openAbout()
        focusHere(SettingsOption.CheckUpdatesDaily)
        pressHere(Action.Enter)
        assertFalse(actions.settings.updates.checkDaily)
        pressHere(Action.Enter)
        assertTrue(actions.settings.updates.checkDaily)
    }

    @Test
    fun aOnCheckForUpdatesChecksNow() {
        openAbout()
        focusHere(SettingsOption.CheckForUpdates)
        pressHere(Action.Enter)
        assertEquals(listOf("check"), updateCalls)
    }

    @Test
    fun updateAvailableOpensItsPanelAndUpdateStartsIt() {
        about.content = about.content.copy(update = found)
        openAbout()
        focusHere(SettingsOption.UpdateAvailable)
        pressHere(Action.Enter)
        assertEquals(PanelPage.Update, about.focus.page)
        assertEquals(SettingsColumn.Panel, about.focus.column)
        pressHere(Action.Enter)
        assertEquals(listOf("update"), updateCalls)
    }

    @Test
    fun theCursorStaysOnCheckForUpdatesWhenTheUpdateRowAppears() {
        openAbout()
        focusHere(SettingsOption.CheckForUpdates)
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found)
        assertEquals(SettingsOption.CheckForUpdates, about.focusedOption)
        pressHere(Action.Enter)
        assertEquals(listOf("check", "check"), updateCalls)
    }

    @Test
    fun theUpdatePanelClosesWhenItsRowGoes() {
        about.content = about.content.copy(update = found)
        openAbout()
        focusHere(SettingsOption.UpdateAvailable)
        pressHere(Action.Enter)
        assertEquals(PanelPage.Update, about.focus.page)
        about.content = about.content.copy(update = UpdateView())
        assertEquals(SettingsColumn.Options, about.focus.column)
        assertEquals(null, about.focus.page)
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
        openAbout()
        focusHere(SettingsOption.UpdateAvailable)
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found.copy(step = UpdateStep.AskMetered))
        assertEquals(0, about.focus.panelIndex)
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found.copy(step = UpdateStep.NeedsPermission))
        pressHere(Action.Enter)
        pressHere(Action.Move(Direction.Down))
        pressHere(Action.Enter)
        assertEquals(listOf("update metered", "allow", "cancel"), updateCalls)
    }

    @Test
    fun whileItDownloadsTheOnlyButtonIsCancel() {
        about.content = about.content.copy(update = found)
        openAbout()
        focusHere(SettingsOption.UpdateAvailable)
        pressHere(Action.Enter)
        about.content = about.content.copy(update = found.copy(step = UpdateStep.Downloading(1_000, 9_800_000)))
        pressHere(Action.Enter)
        assertEquals(listOf("cancel"), updateCalls)
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
}
