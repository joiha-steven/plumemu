package com.plumemu.home

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plumemu.coreui.components.CoverGlowColor
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A cover's glow colour from a real HARDWARE bitmap, which is what Coil returns by default (a unit test cannot make one):
 * reading its pixels directly throws IllegalStateException, so the colour must come through the copy
 * [CoverGlowColor.samplePixels] makes. Run on an emulator or device:
 * `adb shell am instrument -w -e class com.plumemu.home.CoverGlowColorOnDeviceTest com.plumemu.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class CoverGlowColorOnDeviceTest {
    @Test
    fun aHardwareBitmapGivesAColour() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val soft = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2E5AAC.toInt()) }
        val hardware = soft.copy(Bitmap.Config.HARDWARE, false)
        assertNotNull("the bitmap is hardware", hardware)
        assertNotNull("a colour, not a crash", CoverGlowColor.of(hardware.asImageBitmap()))
    }
}
