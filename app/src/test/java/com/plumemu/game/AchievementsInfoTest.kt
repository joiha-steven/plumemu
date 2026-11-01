package com.plumemu.game

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.AchievementsState
import com.plumemu.home.HomeAt
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Info's RetroAchievements section: no section signed out, each state's view, the list scrolling by gamepad and touch, the badges. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AchievementsInfoTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(
        state: AchievementsState?,
        badges: BadgeImages = BadgeImages.None,
    ): InfoScroll {
        val screen = PanelScreen.Info(AchievementsSamples.facts(state))
        val panel = OpenPanel(GameRef("1", "Super Metroid"), screen)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalBadgeImages provides badges) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
            }
        }
        compose.waitForIdle()
        return screen.scroll
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun shows(text: String): Boolean = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun signedOutThereIsNoSectionAtAll() {
        show(null)
        assertEquals("no section", 0, count(GamePanelTags.ACHIEVEMENTS))
        assertFalse(shows("Achievements"))
    }

    @Test
    fun aStateThatMeansNoSectionDrawsNothing() {
        for (state in listOf(AchievementsState.NotSignedIn, AchievementsState.NotSupported)) {
            assertFalse("$state has no section", hasAchievementsSection(state))
        }
        show(AchievementsState.NotSupported)
        assertEquals(0, count(GamePanelTags.ACHIEVEMENTS))
    }

    @Test
    fun everyQuietStateIsOneLineAndNeverAList() {
        var state by mutableStateOf<AchievementsState?>(AchievementsState.Loading)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                val shown = state
                val panel = remember(shown) { OpenPanel(GameRef("1", "Super Metroid"), PanelScreen.Info(AchievementsSamples.facts(shown))) }
                HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel)
            }
        }
        for (each in AchievementsSamples.quietStates) {
            state = each
            compose.waitForIdle()
            val expected = text(quietLineOf(each)!!)
            assertTrue("$each shows \"$expected\"", shows(expected))
            assertEquals("$each is one quiet line", 1, count(GamePanelTags.ACHIEVEMENTS_LINE))
            assertEquals("$each has no list", 0, count(GamePanelTags.achievement(0)))
        }
    }

    @Test
    fun theQuietLinesSayWhatTheOwnerAsked() {
        assertEquals("Achievements: this file type cannot be checked", text(R.string.achievements_cannot_hash))
        assertEquals(R.string.achievements_loading, quietLineOf(AchievementsState.Loading))
        assertEquals(R.string.achievements_no_set, quietLineOf(AchievementsState.NoSet))
        assertEquals("a listed game with no achievements reads as no set", R.string.achievements_no_set, quietLineOf(AchievementsSamples.set(0)))
        assertNull("a set is a list, not a line", quietLineOf(AchievementsSamples.set()))
    }

    @Test
    fun aSetHashedFromTheFileHasNoMatchedByNameLine() {
        show(AchievementsSamples.set())
        assertEquals(0, count(GamePanelTags.ACHIEVEMENTS_BY_NAME))
        assertFalse(shows("Matched by name"))
    }

    @Test
    fun aGameFoundByNameSaysSoInOneQuietLine() {
        assertEquals("Matched by name", text(R.string.achievements_matched_by_name))
        show(AchievementsSamples.set().copy(matchedByName = true))
        assertEquals(1, count(GamePanelTags.ACHIEVEMENTS_BY_NAME))
        assertTrue(shows("Matched by name"))
        assertEquals("the list is still there", 6, count(GamePanelTags.ACHIEVEMENT_BADGE))
    }

    @Test
    fun aSetShowsTheCountThePercentTheHardcoreLineAndEveryRow() {
        val set = AchievementsSamples.set()
        show(set)
        assertTrue(shows(text(R.string.achievements_summary, 3, 6, 50)))
        assertTrue(shows(text(R.string.achievements_hardcore, 1, 6)))
        assertEquals(6, count(GamePanelTags.ACHIEVEMENT_BADGE))
        for (index in set.achievements.indices) assertEquals("row $index", 1, count(GamePanelTags.achievement(index)))
        assertTrue("the long title is a row", shows(AchievementsSamples.LONG_TITLE))
    }

    @Test
    fun noHardcoreLineWithoutHardcoreUnlocks() {
        val set = AchievementsSamples.set().let { it.copy(awardedHardcore = 0) }
        show(set)
        assertTrue(shows(text(R.string.achievements_summary, 3, 6, 50)))
        assertFalse(shows("Hardcore 0"))
    }

    @Test
    fun anEarnedRowShowsItsDateAndALockedRowOnlyItsPoints() {
        show(AchievementsSamples.set())
        val date = earnedDate("2026-10-31 09:05:44", Locale.getDefault())!!
        assertTrue("earned row: points and date", shows(text(R.string.achievement_earned, "10 points", date)))
        assertTrue("hardcore unlock says so", shows(text(R.string.achievement_earned_hardcore, "5 points", earnedDate("2026-10-30 14:22:10", Locale.getDefault())!!)))
        assertTrue("locked row: points only", shows("25 points"))
    }

    @Test
    fun theListScrollsByGamepadUntilTheLastRowIsWhole() {
        val set = AchievementsSamples.set(12)
        val scroll = show(set)
        var presses = 0
        while (compose.runOnIdle { scroll.press(isDown = true) } && presses < 60) {
            presses++
            compose.waitForIdle()
        }
        assertTrue("Down moved the list ($presses presses)", presses > 3)
        assertTrue("it stopped before an endless scroll", presses < 60)
        compose.onNodeWithTag(GamePanelTags.achievement(set.achievements.lastIndex), useUnmergedTree = true).assertIsDisplayed()
        while (compose.runOnIdle { scroll.press(isDown = false) } && presses > -60) presses--
        compose.waitForIdle()
        compose.onNodeWithTag(GamePanelTags.fact(0), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aFingerScrollsToTheLastRow() {
        val set = AchievementsSamples.set(12)
        show(set)
        repeat(10) { compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        compose.onNodeWithTag(GamePanelTags.achievement(set.achievements.lastIndex), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anEarnedRowAsksForItsBadgeAndALockedRowForTheLockedOne() {
        val asked = mutableListOf<String>()
        val images =
            object : BadgeImages {
                override fun cached(url: String, sizePx: Int): ImageBitmap? = null

                override suspend fun load(url: String, sizePx: Int): ImageBitmap? {
                    asked += url
                    return null
                }
            }
        val set = AchievementsSamples.set(4)
        show(set, images)
        compose.waitForIdle()
        assertTrue("earned: its own badge", set.achievements[0].badgeUrl in asked)
        assertFalse("earned: not the locked one", set.achievements[0].lockedBadgeUrl in asked)
        assertTrue("locked: the _lock badge", set.achievements[3].lockedBadgeUrl in asked)
        assertFalse("locked: not the earned badge", set.achievements[3].badgeUrl in asked)
    }

    @Test
    fun aBadgeThatFailsLeavesAPlaceholderAndTheRowsStay() {
        val failing =
            object : BadgeImages {
                override fun cached(url: String, sizePx: Int): ImageBitmap? = null

                override suspend fun load(url: String, sizePx: Int): ImageBitmap? = null
            }
        show(AchievementsSamples.set(), failing)
        assertEquals("a placeholder per row, no crash", 6, count(GamePanelTags.ACHIEVEMENT_BADGE))
        assertTrue(shows("Morph Ball"))
    }

    @Test
    fun earnedDateReadsRetroAchievementsUtcTimeInThePersonsZone() {
        assertEquals("Oct\u00A030,\u00A02026", earnedDate("2026-10-30 14:22:10", Locale.US, TimeZone.getTimeZone("UTC")))
        assertFalse("a date never breaks inside itself", earnedDate("2026-10-30 14:22:10", Locale.US, TimeZone.getTimeZone("UTC"))!!.contains(' '))
        assertEquals("a late evening in UTC is the next day in Tokyo", "Oct\u00A031,\u00A02026", earnedDate("2026-10-30 20:00:00", Locale.US, TimeZone.getTimeZone("Asia/Tokyo")))
        assertNull(earnedDate(null, Locale.US))
        assertNull(earnedDate("  ", Locale.US))
        assertEquals("text it cannot read is not hidden", "30/10/2026", earnedDate("30/10/2026 10:00", Locale.US))
    }

    @Test
    fun thePercentRoundsToTheNearestAndOnlyAFullSetIsAHundred() {
        assertEquals(27, achievementPercent(12, 45))
        assertEquals("never rounds up to a hundred", 99, achievementPercent(199, 200))
        assertEquals("never rounds down to none", 1, achievementPercent(1, 500))
        assertEquals(100, achievementPercent(45, 45))
        assertEquals(0, achievementPercent(0, 0))
        assertEquals(100, achievementPercent(50, 45))
    }
}
