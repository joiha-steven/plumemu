package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › Advanced › Export logs: A or a tap asks the Activity to share; a failure is said under the row. */
internal class SettingsExportLogsTest : SettingsModelHarness() {
    @Test
    fun exportLogsIsListedLastInAdvanced() {
        assertEquals(SettingsOption.ExportLogs, SettingsSection.Advanced.options.last())
        assertEquals(OptionKind.Action, SettingsOption.ExportLogs.kind)
    }

    @Test
    fun aPressSharesTheLogAndAFailureIsSaid() {
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.ExportLogs)
        press(Action.Enter)
        assertEquals(1, actions.logExports)
        assertNull(model.work.notice)

        actions.canShareLogs = false
        press(Action.Enter)
        assertEquals(2, actions.logExports)
        assertEquals(SettingsOption.ExportLogs to SettingsNotice.ExportLogsFailed, model.work.notice)
    }
}
