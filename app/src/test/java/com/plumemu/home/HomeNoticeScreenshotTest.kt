package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.LibraryNotice
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The new-games notice in the header, the dot on a system, and the empty library's folder ask. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeNoticeScreenshotTest {
    private val board = FakeHomeContent.board()
    private val first get() = board.entries.first { it.kind == EntryKind.System }.id

    @Test
    fun headerNoticeAndDot() =
        ReferenceShots.capture("home_notice_new_games") {
            val notices = HomeNotices(
                MutableStateFlow(LibraryNotice(1, 24, 0, listOf(first))),
                MutableStateFlow(setOf(first)),
                {},
                {},
            )
            CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(board, BoardSystemsFocused) }
        }

    @Test
    fun emptyLibrary() = ReferenceShots.capture("home_empty_library") { HomeAt(FakeHomeContent.noGames()) }
}
