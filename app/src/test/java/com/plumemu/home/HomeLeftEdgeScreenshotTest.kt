package com.plumemu.home

import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Appearance › Left edge › System codes: the board's games screen with a code where the count was (Medium and Largest). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeLeftEdgeScreenshotTest {
    private val gamesFocused =
        listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, FakeHomeContent.BOARD_REMEMBERED_ROW))

    @Test
    fun codes() = ReferenceShots.capture("home_codes") { HomeAt(FakeHomeContent.board().withLeftEdge(LeftEdgeSetting.Codes), gamesFocused) }

    @Test
    fun codesAtLargest() = ReferenceShots.capture("home_codes", textSize = TextSize.Largest) { HomeAt(FakeHomeContent.board().withLeftEdge(LeftEdgeSetting.Codes), gamesFocused) }
}
