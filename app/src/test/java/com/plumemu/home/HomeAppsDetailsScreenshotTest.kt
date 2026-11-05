package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Apps' list with its details column, at the third stop (the games stop pushed one to the right): at 768x432 the column
 * is the whole screen's right part, at 576x432 the list peeks and the details stop is the whole width. Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeAppsDetailsScreenshotTest {
    private val thirdStop = listOf(Action.TapItem(Column.Systems, APPS_ENTRY), Action.Enter, Action.Move(Direction.Right))

    @Test
    fun appsThirdStop() = ReferenceShots.capture("home_apps_third_stop") { board() }

    @Test
    fun appsThirdStopAtLargestText() = ReferenceShots.capture("home_apps_third_stop", textSize = TextSize.Largest) { board() }

    @androidx.compose.runtime.Composable
    private fun board() = CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons()) { HomeAt(appsGridLibrary(hasBoxArt = true), thirdStop) }
}
