package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.textsize.FitAllowance
import com.plumemu.textsize.findTagged
import com.plumemu.textsize.textProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Recent and Favorites with no game: the games area is not blank, one muted line stands where the
 * list would start, whole and inside the margins, in every translation at 768x432 and 576x432.
 */
abstract class HomeEmptyEntryLineBase(
    private val widthDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var locale by mutableStateOf(Locale.ENGLISH)

    private fun checkLines(
        content: HomeContent,
        entry: Int,
        tag: String,
    ) {
        compose.setContent {
            val context = LocalContext.current
            val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
                PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, listOf(Action.TapItem(Column.Systems, entry))) }
            }
        }
        for (each in LOCALES) {
            locale = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val line = findTagged(root, tag)
            assertTrue("$each $widthDp: no empty line tagged $tag", line != null)
            val text = line!!.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            assertTrue("$each: the line is blank", text.isNotBlank())
            val problems = textProblems(line, FitAllowance(isLargest = false))
            assertTrue("$each $widthDp: '$text' is cut or overlapped: $problems", problems.isEmpty())
            val density = root.boundsInRoot.width / widthDp
            assertTrue("$each: '$text' starts left of the list's start: ${line.boundsInRoot.left / density}", line.boundsInRoot.left / density >= 200f)
            assertTrue("$each: '$text' runs into the right margin: ${line.boundsInRoot.right / density} of $widthDp", line.boundsInRoot.right / density <= widthDp - 24f + 0.5f)
        }
    }

    @Test
    fun emptyRecentShowsItsLineInEveryLocale() {
        checkLines(FakeHomeContent.noRecent(), entry = 0, tag = HomeTags.EMPTY_RECENT)
    }

    @Test
    fun emptyFavoritesShowsItsLineInEveryLocale() {
        checkLines(FakeHomeContent.noFavorites(), entry = 1, tag = HomeTags.EMPTY_FAVORITES)
    }

    @Test
    fun recentWithGamesShowsNoSuchLine() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, 0))) } }
        compose.waitForIdle()
        assertTrue("Recent with games shows the empty line", findTagged(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), HomeTags.EMPTY_RECENT) == null)
    }

    private companion object {
        val LOCALES = listOf("en", "es", "fr", "ja", "ko", "vi", "zh", "zh-TW").map(Locale::forLanguageTag)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeEmptyEntryLineRp5Test : HomeEmptyEntryLineBase(768)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeEmptyEntryLineNovaTest : HomeEmptyEntryLineBase(576)

/** The footer offers "A Select" only where A has something to open: an empty Recent or Favorites offers no A at all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeEmptyEntryHintsTest {
    @Test
    fun anEmptyEntryOffersNoSelect_AndOneWithGamesDoes() {
        val noRecent = FakeHomeContent.noRecent()
        val noFavorites = FakeHomeContent.noFavorites()
        val recent = noRecent.entries.single { it.kind == EntryKind.Recent }
        val favorites = noFavorites.entries.single { it.kind == EntryKind.Favorites }
        assertTrue("an empty Recent offers A Select: it would do nothing", hintsFor(noRecent, Column.Systems, recent).none { it.action == Action.Enter })
        assertTrue("an empty Favorites offers A Select: it would do nothing", hintsFor(noFavorites, Column.Systems, favorites).none { it.action == Action.Enter })
        val full = FakeHomeContent.board()
        val withGames = full.entries.single { it.kind == EntryKind.Recent }
        assertEquals(R.string.home_hint_select, hintsFor(full, Column.Systems, withGames).first { it.action == Action.Enter }.label)
    }
}
