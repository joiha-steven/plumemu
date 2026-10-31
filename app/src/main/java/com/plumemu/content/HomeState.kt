package com.plumemu.content

import com.plumemu.home.HomeContent

/** What the main screen can show: the library's content, or a message that it could not be read. */
sealed interface HomeState {
    data class Ready(val content: HomeContent) : HomeState

    /** The library could not be read; the screen says so and what to try (see the log for why). */
    data object Failed : HomeState
}
