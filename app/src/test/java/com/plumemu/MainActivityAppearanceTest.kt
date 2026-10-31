package com.plumemu

import android.graphics.drawable.ColorDrawable
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The window paints the person's background from the first frame, before the settings arrive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityAppearanceTest {
    private val graph = (RuntimeEnvironment.getApplication() as PlumApplication).graph

    private fun windowBackground(activity: MainActivity): Int = (activity.window.decorView.background as ColorDrawable).color

    @Test
    fun aFirstLaunchPaintsBlack() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(BackgroundPreset.Black.color.toArgb(), windowBackground(activity))
    }

    @Test
    fun withPaperStoredARecreatedActivityPaintsPaperAtOnce() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        runBlocking { graph.settingsStore.setAppearance(AppearanceSettings(ColorSetting.Preset("Paper"), TextColorSetting.Dark)) }
        // The cache is written off the main thread when the stored appearance changes.
        val paper = BackgroundPreset.Paper.color.toArgb()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (graph.appearanceCache.read().backgroundArgb != paper && System.nanoTime() < deadline) Thread.sleep(5)

        controller.recreate()

        assertEquals("#F4F3EF", "#%06X".format(windowBackground(controller.get()) and 0xFFFFFF))
        assertEquals(paper, windowBackground(controller.get()))
        assertEquals(true, graph.appearanceCache.read().isTextDark)
    }
}
