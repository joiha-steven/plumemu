package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.ButtonLetters
import com.plumemu.update.UpdateManifest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The update notice: the pill and START's "Update" hint on the main screen, and the "What's new" card over it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdateNoticeScreenshotTest {
    @Test
    fun homeWithThePill() =
        ReferenceShots.capture("home_update_pill") {
            CompositionLocalProvider(LocalUpdateFound provides MutableStateFlow("0.1.1")) {
                HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true })
            }
        }

    @Test
    fun homeWithThePillAtTheLargestText() =
        ReferenceShots.capture("home_update_pill_largest", textSize = TextSize.Largest) {
            CompositionLocalProvider(LocalUpdateFound provides MutableStateFlow("0.1.1")) {
                HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }, charging = { true })
            }
        }

    private fun card(release: UpdateManifest): @androidx.compose.runtime.Composable () -> Unit = {
        Box {
            HomeAt(FakeHomeContent.board(), BoardSystemsFocused)
            WhatsNewCardLayout(remember { WhatsNewState(release) }, ButtonLetters.Default) {}
        }
    }

    @Test
    fun cardWithAllFourGroups() = ReferenceShots.capture("whats_new_groups", content = card(WhatsNewFixtures.grouped))

    @Test
    fun cardWithNotesOnly() = ReferenceShots.capture("whats_new_notes_only", content = card(WhatsNewFixtures.notesOnly))

    @Test
    fun cardAtTheLargestText() = ReferenceShots.capture("whats_new_groups_largest", textSize = TextSize.Largest, content = card(WhatsNewFixtures.grouped))
}
