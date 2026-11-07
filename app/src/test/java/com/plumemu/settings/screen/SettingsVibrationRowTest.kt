package com.plumemu.settings.screen

import android.app.Application
import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.withVibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Controls lists Vibration only on a device that has a vibrator, as Android says; nothing else in the section moves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsVibrationRowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theRowIsAbsentWithoutAVibratorAndTheOthersStay() {
        val with = SettingsSection.Controls.rowsIn(SettingsContent(hasVibrator = true))
        val without = SettingsSection.Controls.rowsIn(SettingsContent(hasVibrator = false))
        assertTrue(SettingsOption.Vibration in with)
        assertFalse(SettingsOption.Vibration in without)
        assertEquals(with - SettingsOption.Vibration, without)
    }

    private fun graphAnswer() = runBlocking { flowOf(SettingsContent()).withVibrator(context, Dispatchers.Unconfined).first().hasVibrator }

    @Test
    fun theGraphAsksAndroid() {
        val vibrator = shadowOf(context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        vibrator.setHasVibrator(false)
        assertFalse(graphAnswer())
        vibrator.setHasVibrator(true)
        assertTrue(graphAnswer())
    }
}
