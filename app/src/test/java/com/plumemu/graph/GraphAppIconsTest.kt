package com.plumemu.graph

import android.graphics.Bitmap
import com.plumemu.content.LibraryHomeContent
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.apps.AppIcon
import com.plumemu.launcher.apps.AppIcons
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Apps grid's icons on the graph: a row id (`package/activity`, as `LibraryHomeContent` makes it) finds
 * its app in the graph's list, a new list is looked up anew, and a row the list no longer has gets no icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphAppIconsTest {
    private val alpha = LaunchableApp("Alpha", "com.example.alpha", "com.example.alpha.Main")
    private val alphaSettings = LaunchableApp("Alpha settings", "com.example.alpha", "com.example.alpha.Settings")
    private val beta = LaunchableApp("Beta", "com.example.beta", "com.example.beta.Main")
    private var listed = listOf(alpha, alphaSettings)

    /** Hands out a bitmap for every app, recording which app was asked; "cached" for what was loaded. */
    private val launcher =
        object : AppIcons {
            val asked = mutableListOf<LaunchableApp>()
            val held = HashMap<LaunchableApp, Bitmap>()
            override val generation: StateFlow<Int> = MutableStateFlow(3)

            override suspend fun load(
                app: LaunchableApp,
                sizePx: Int,
            ): AppIcon {
                asked += app
                return AppIcon.Picture(held.getOrPut(app) { Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888) })
            }

            override fun cached(
                app: LaunchableApp,
                sizePx: Int,
            ): AppIcon.Picture? = held[app]?.let { AppIcon.Picture(it) }

            override fun invalidate(packageName: String?) = Unit
        }

    private val icons = GraphAppIcons(listed = { listed }, icons = launcher)

    @Test
    fun aRowIdFromTheLibraryFindsItsAppAmongTwoOfOnePackage() = runTest {
        assertNotNull(icons.load(LibraryHomeContent.appRowId(alphaSettings), 48))
        assertEquals(listOf(alphaSettings), launcher.asked)
        assertNotNull("in memory after the load", icons.cached(LibraryHomeContent.appRowId(alphaSettings), 48))
        assertNull("not loaded yet", icons.cached(LibraryHomeContent.appRowId(alpha), 48))
    }

    @Test
    fun aNewListIsLookedUpAnewAndARowItNoLongerHasGetsNoIcon() = runTest {
        val alphaRow = LibraryHomeContent.appRowId(alpha)
        val betaRow = LibraryHomeContent.appRowId(beta)
        assertNull("beta is not listed yet", icons.load(betaRow, 48))
        assertNotNull(icons.load(alphaRow, 48))
        listed = listOf(beta)
        assertNotNull("the new list's app", icons.load(betaRow, 48))
        assertNull("alpha left the list", icons.load(alphaRow, 48))
        assertNull("nor from memory", icons.cached(alphaRow, 48))
        assertEquals("the launcher was asked only for listed apps", listOf(alpha, beta), launcher.asked)
    }

    @Test
    fun theGenerationIsTheLaunchers() {
        assertEquals(3, icons.generation.value)
    }
}
