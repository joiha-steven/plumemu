package com.plumemu.settings.screen

import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
import com.plumemu.graph.BuildFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A build from source names no statistics server and no update manifest (app/build.gradle.kts), so
 * the rows that need them are not listed; a build that names them lists them all.
 */
internal class SettingsBuildFeaturesTest {
    private val statisticsRows = listOf(SettingsOption.UsageStatistics, SettingsOption.ReportProblem)
    private val updateRows = listOf(SettingsOption.CheckForUpdates, SettingsOption.CheckUpdatesDaily, SettingsOption.UpdateAvailable)

    private fun allRows(content: SettingsContent): List<SettingsOption> = SettingsSection.entries.flatMap { it.rowsIn(content) }

    @Test
    fun aBuildWithBothAddressesListsEveryRow() {
        val rows = allRows(SettingsContent(hasStatistics = true, hasUpdates = true))
        assertTrue(rows.containsAll(statisticsRows))
        assertTrue(rows.containsAll(updateRows - SettingsOption.UpdateAvailable))
    }

    @Test
    fun noStatisticsServerHidesTheStatisticsAndReportRowsOnly() {
        val rows = allRows(SettingsContent(hasStatistics = false, hasUpdates = true))
        statisticsRows.forEach { assertFalse("$it must be hidden", it in rows) }
        assertTrue(SettingsOption.CheckForUpdates in rows)
        assertTrue(SettingsOption.Licenses in rows)
    }

    @Test
    fun noManifestHidesEveryUpdateRowEvenWhenAnUpdateWasRecorded() {
        val rows = allRows(SettingsContent(hasStatistics = true, hasUpdates = false, update = UpdateView(found = UpdateManifest(41, "0.0.3-alpha", "https://example.com/p.apk", 1, "ab".repeat(32), 23, "2026-10-23", emptyList(), null))))
        updateRows.forEach { assertFalse("$it must be hidden", it in rows) }
        assertTrue(SettingsOption.UsageStatistics in rows)
    }

    @Test
    fun aBuildWithNeitherKeepsTheRestOfAbout() {
        val about = SettingsSection.About.rowsIn(SettingsContent(hasStatistics = false, hasUpdates = false))
        assertTrue(about == listOf(SettingsOption.Version, SettingsOption.Licenses, SettingsOption.Privacy))
    }

    @Test
    fun theFirstContentBeforeAnythingIsReadFollowsTheBuild() {
        val first = SettingsContent()
        assertEquals(BuildFeatures.hasStatistics, first.hasStatistics)
        assertEquals(BuildFeatures.hasUpdates, first.hasUpdates)
        assertEquals(BuildFeatures.hasStatistics, SettingsOption.ReportProblem in SettingsSection.About.rowsIn(first))
    }
}
