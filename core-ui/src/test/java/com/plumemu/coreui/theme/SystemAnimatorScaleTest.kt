package com.plumemu.coreui.theme

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Changing the animator scale is not a configuration change, and the launcher activity lives for
 * days: `PlumTheme.motion` must follow the setting while composed, and stop observing after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SystemAnimatorScaleTest {
    @get:Rule
    val compose = createComposeRule()

    private val resolver = RuntimeEnvironment.getApplication().contentResolver
    private val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)

    private fun setSystemScale(scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        // The real settings provider notifies observers of the key; Robolectric's store may not.
        resolver.notifyChange(uri, null)
        compose.waitForIdle()
    }

    @Test
    fun motionFollowsTheSystemScaleWhileComposed() {
        var isOn: Boolean? = null
        compose.setContent { PlumTheme { isOn = PlumTheme.motion.isOn } }
        compose.waitForIdle()
        assertEquals(true, isOn)

        setSystemScale(0f)
        assertEquals(false, isOn)

        setSystemScale(1f)
        assertEquals(true, isOn)
    }

    @Test
    fun stopsObservingWhenTheThemeLeaves() {
        var showTheme by mutableStateOf(true)
        compose.setContent { if (showTheme) PlumTheme { } }
        compose.waitForIdle()
        assertEquals(1, shadowOf(resolver).getContentObservers(uri).size)

        showTheme = false
        compose.waitForIdle()
        assertTrue(shadowOf(resolver).getContentObservers(uri).isEmpty())
    }
}
