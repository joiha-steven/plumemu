package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings' left column shares the system column's row: A held dips the focused section only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelSettingsSectionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())

    private fun pixels(section: SettingsSection): List<Int> {
        val map = compose.onNodeWithTag(SettingsTags.section(section), useUnmergedTree = true).captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y].hashCode()) }
    }

    @Test
    fun aHeldConfirmButtonDipsTheFocusedSectionRowOnly() {
        val focused = SettingsFocus().section
        val other = SettingsSection.entries.first { it != focused }
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { CompositionLocalProvider(LocalHeldKeys provides held) { SettingsAt(SettingsFocus()) } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val restingFocused = pixels(focused)
        val restingOther = pixels(other)
        held.value = setOf("A")
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        assertNotEquals("the focused section dips", restingFocused, pixels(focused))
        assertEquals("another section does not", restingOther, pixels(other))
    }
}
