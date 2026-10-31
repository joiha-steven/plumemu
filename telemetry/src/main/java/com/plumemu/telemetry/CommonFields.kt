package com.plumemu.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The `common` object of every request (docs/conventions/telemetry.md › Common
 * fields), read once per process. Nothing here names the person: device model, Android version,
 * screen, memory range and languages.
 */
internal object CommonFields {
    fun read(
        context: Context,
        config: TelemetryConfig,
    ): JsonObject {
        val app = context.applicationContext
        val displays = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val main = displays.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics().also { main?.getRealMetrics(it) }
        val secondScreen = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()
        val memory = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        return of(
            config = config,
            device =
                Device(
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    brand = Build.BRAND.orEmpty(),
                    model = Build.MODEL.orEmpty(),
                    name = Build.DEVICE.orEmpty(),
                    socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                    sdkInt = Build.VERSION.SDK_INT,
                ),
            screen = screenOf(metrics),
            secondScreen = secondScreen,
            totalMemoryBytes = memory.totalMem,
            appLanguage = languageOf(app.resources.configuration),
            systemLanguage = languageOf(Resources.getSystem().configuration),
        )
    }

    class Device(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val name: String,
        val socModel: String?,
        val androidVersion: String,
        val sdkInt: Int,
    )

    class Screen(
        val dp: String,
        val densityDpi: Int,
    )

    /** The object itself, from values already read (the tests build it from fixed values). */
    fun of(
        config: TelemetryConfig,
        device: Device,
        screen: Screen,
        secondScreen: Boolean,
        totalMemoryBytes: Long,
        appLanguage: String,
        systemLanguage: String,
    ): JsonObject =
        buildJsonObject {
            put("app_version", JsonPrimitive(config.appVersion))
            put("version_code", JsonPrimitive(config.versionCode))
            put("build_type", JsonPrimitive(config.buildType.wire))
            put("device_manufacturer", JsonPrimitive(device.manufacturer))
            put("device_brand", JsonPrimitive(device.brand))
            put("device_model", JsonPrimitive(device.model))
            put("device_name", JsonPrimitive(device.name))
            // Android 12 and later only; "unknown" there is left out too.
            device.socModel?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }?.let { put("soc_model", JsonPrimitive(it)) }
            put("android_version", JsonPrimitive(device.androidVersion))
            put("sdk_int", JsonPrimitive(device.sdkInt))
            put("screen_dp", JsonPrimitive(screen.dp))
            put("screen_density", JsonPrimitive(screen.densityDpi))
            put("second_screen", JsonPrimitive(if (secondScreen) "yes" else "no"))
            put("ram_bucket", JsonPrimitive(Buckets.ram(totalMemoryBytes)))
            put("app_language", JsonPrimitive(appLanguage))
            put("system_language", JsonPrimitive(systemLanguage))
        }

    /**
     * The main display in dp, long side first (`"960x540"`), so the same device reads the same in
     * either orientation.
     */
    fun screenOf(metrics: DisplayMetrics): Screen {
        val density = if (metrics.density > 0f) metrics.density else 1f
        val width = (metrics.widthPixels / density).roundToInt()
        val height = (metrics.heightPixels / density).roundToInt()
        return Screen("${max(width, height)}x${min(width, height)}", metrics.densityDpi)
    }

    private fun languageOf(configuration: Configuration): String {
        val locale: Locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.locales[0]
            } else {
                // `locale` is the only accessor before API 24, where `locales` does not exist.
                @Suppress("DEPRECATION")
                configuration.locale
            }
        return tagOf(locale)
    }

    /**
     * `en-US`, `pt-BR`, `zh-Hans-CN`: the language tag without Unicode extensions or private use,
     * which Android 14 adds from regional preferences (`en-US-u-fw-mon-mu-celsius`) and the server
     * refuses.
     */
    fun tagOf(locale: Locale): String =
        try {
            Locale.Builder().setLocale(locale).clearExtensions().build().toLanguageTag()
        } catch (e: java.util.IllformedLocaleException) {
            // Old Java-only forms (ja_JP_JP): the language and region are enough.
            Locale(locale.language, locale.country).toLanguageTag()
        }
}
