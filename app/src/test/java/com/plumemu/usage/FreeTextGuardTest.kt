package com.plumemu.usage

import com.plumemu.telemetry.OpenFrom
import com.plumemu.telemetry.TelemetryEvent
import org.junit.Assert.assertThrows
import org.junit.Test

/** The guard the other usage tests use: it must fail on free text, or it proves nothing. */
class FreeTextGuardTest {
    private enum class Option { Background }

    @Suppress("EnumEntryName") // A name with spaces stands in for a free-text game title.
    private enum class Word { `Super Mario World` }

    @Test
    fun fixedValuesPass() {
        assertNoFreeText(listOf(TelemetryEvent.AppOpen(420, OpenFrom.Icon), TelemetryEvent.SearchUsed(4, 12, true), TelemetryEvent.UpdateInstalled("0.0.3-beta")))
    }

    @Test
    fun aTitleFails() {
        assertThrows(AssertionError::class.java) { assertNoFreeText(listOf(TelemetryEvent.SettingChanged(Option.Background, Word.`Super Mario World`))) }
    }
}
