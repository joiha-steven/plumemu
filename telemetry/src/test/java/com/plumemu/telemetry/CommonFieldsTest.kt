package com.plumemu.telemetry

import android.content.Context
import android.util.DisplayMetrics
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `common` object of the API: every field, read from a real (Robolectric) device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-hdpi")
class CommonFieldsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val config = TelemetryConfig("https://plumemu.stevensdesk.com", "0.0.3-beta", 321, TelemetryConfig.BuildType.Release)

    @Test
    fun everyFieldOfThePlanIsThere() {
        val common = CommonFields.read(context, config)

        val expected =
            setOf(
                "app_version", "version_code", "build_type", "device_manufacturer", "device_brand", "device_model", "device_name",
                "soc_model", "android_version", "sdk_int", "screen_dp", "screen_density", "second_screen", "ram_bucket",
                "app_language", "system_language",
            )
        // Robolectric reports no SoC model ("unknown"), which is left out as on a real device without one.
        assertEquals(expected - "soc_model", common.keys)
        assertEquals(JsonPrimitive("0.0.3-beta"), common["app_version"])
        assertEquals(JsonPrimitive(321L), common["version_code"])
        assertEquals(JsonPrimitive("release"), common["build_type"])
        assertEquals(JsonPrimitive(34), common["sdk_int"])
        assertEquals(JsonPrimitive("960x540"), common["screen_dp"])
        assertEquals(JsonPrimitive(240), common["screen_density"])
        assertEquals(JsonPrimitive("no"), common["second_screen"])
        assertTrue((common["app_language"] as JsonPrimitive).content.isNotBlank())
    }

    @Test
    fun theScreenIsLongSideFirstInEitherOrientation() {
        val portrait = DisplayMetrics().apply { widthPixels = 1080; heightPixels = 1920; density = 2f; densityDpi = 320 }
        assertEquals("960x540", CommonFields.screenOf(portrait).dp)
        assertEquals(320, CommonFields.screenOf(portrait).densityDpi)
    }

    @Test
    fun noDeviceFieldNamesThePerson() {
        val common = CommonFields.read(context, config)
        assertFalse(common.keys.any { it.contains("id") && it != "android_version" })
    }
}
