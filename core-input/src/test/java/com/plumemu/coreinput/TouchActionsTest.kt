package com.plumemu.coreinput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.strip.StripGeometry
import com.plumemu.coreinput.strip.StripIntent
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.strip.SystemEntry
import com.plumemu.coreinput.strip.rememberStripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumMotionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The touch path of every action: a finger on a row, a hint or the system edge, through the same
 * reducer the buttons use. (Swipes and flings inside a column are the lists' own scrolling.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class TouchActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val content =
        StripContent(
            listOf(SystemEntry("recent", 0), SystemEntry("favorites", 3), SystemEntry("snes", 248), SystemEntry("gba", 5)),
            hasBoxArt = true,
        )
    private val layout = PlumLayout(768.dp, 432.dp)
    private val geometry =
        StripGeometry(
            isNarrow = false,
            leadingMargin = PlumDimens.stripMarginSide,
            systemsWidth = PlumDimens.systemsColumnWidth,
            gamesListWidth = layout.gamesListWidth,
            columnGap = PlumDimens.columnGap,
            peek = layout.peek,
            detailsStopListPeek = PlumDimens.detailsStopListPeek,
        )

    private val sent = mutableListOf<Action>()
    private val intents = mutableListOf<StripIntent>()
    private lateinit var state: StripState

    private val hints =
        listOf(
            Action.Play,
            Action.Enter,
            Action.Back,
            Action.Menu,
            Action.ToggleView,
            Action.OpenSettings,
            Action.NextSystem,
            Action.PreviousSystem,
            Action.JumpLetterNext,
            Action.JumpLetterPrevious,
            Action.ToggleFavorite,
            Action.Search,
        )

    /** Rows for systems 0..3 and games 0..3, and one hint per hint action; every touch goes to the state. */
    private fun showScreen() {
        val onAction: (Action) -> Unit = { action ->
            sent += action
            intents += state.dispatch(action)
        }
        compose.setContent {
            state = rememberStripState(content, geometry, isMotionOn = false, animationSpec = PlumMotionState(false).slideSpec())
            Column {
                Row {
                    for (i in 0..3) Box(Modifier.size(PlumDimens.rowHeight).testTag("system$i").tapItem(Column.Systems, i, onAction))
                }
                Row {
                    for (i in 0..3) Box(Modifier.size(PlumDimens.rowHeight).testTag("game$i").tapItem(Column.Games, i, onAction))
                }
                // Five per row so every hint is on the test screen.
                for (row in hints.chunked(5)) {
                    Row {
                        for (action in row) Box(Modifier.size(PlumDimens.rowHeight).testTag("hint$action").hint(action, onAction))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun aTapSendsTapItemAndALongPressSendsLongPressItem() {
        showScreen()
        tap("game2")
        compose.onNodeWithTag("game1").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf(Action.TapItem(Column.Games, 2), Action.LongPressItem(Column.Games, 1)), sent)
    }

    @Test
    fun accessibilityClicksSendTheSameActions() {
        showScreen()
        compose.onNodeWithTag("system1").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("game0").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("hint${Action.Back}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Action.TapItem(Column.Systems, 1), Action.LongPressItem(Column.Games, 0), Action.Back), sent)
    }

    @Test
    fun everyHintSendsItsAction() {
        showScreen()
        for (action in hints) tap("hint$action")
        assertEquals(hints, sent)
    }

    @Test
    fun moveByTouchTapAnItemToFocusIt() {
        showScreen()
        tap("system2")
        assertEquals("snes", state.focus.systemId)
        tap("game3")
        assertEquals(Column.Games to 3, state.focus.column to state.focus.row)
    }

    @Test
    fun enterByTouchTapTheFocusedSystemAgain() {
        showScreen()
        tap("system1")
        tap("system1")
        assertEquals(Column.Games to "favorites", state.focus.column to state.focus.systemId)
    }

    @Test
    fun playByTouchTapTheFocusedGameAgainOrTheAPlayHint() {
        showScreen()
        tap("system2")
        tap("game2")
        tap("game2")
        tap("hint${Action.Play}")
        assertEquals(listOf(StripIntent.Play("snes", 2), StripIntent.Play("snes", 2)), intents)
    }

    @Test
    fun menuByTouchLongPressTheGame() {
        showScreen()
        tap("system2")
        compose.onNodeWithTag("game1").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf(StripIntent.OpenGameMenu("snes", 1)), intents)
    }

    @Test
    fun changeSystemByTouchTapTheSystemEdge() {
        showScreen()
        tap("system2")
        tap("game0")
        tap("system3")
        assertEquals(Column.Games to "gba", state.focus.column to state.focus.systemId)
        tap("system3")
        assertEquals("tapping the current system returns to the column", Column.Systems, state.focus.column)
    }

    @Test
    fun backToggleViewAndSettingsByTouchTapTheirHints() {
        showScreen()
        tap("system2")
        tap("game0")
        tap("hint${Action.Back}")
        assertEquals(Column.Systems, state.focus.column)
        tap("hint${Action.ToggleView}")
        tap("hint${Action.OpenSettings}")
        assertEquals(listOf(StripIntent.ToggleView("snes"), StripIntent.OpenSettings), intents)
    }

    @Test
    fun letterJumpByTouchTheHintPathAsksTheScreen() {
        // The letter rail may follow after v0.1; a hint sends the same action.
        showScreen()
        tap("system2")
        tap("game3")
        tap("hint${Action.JumpLetterNext}")
        tap("hint${Action.JumpLetterPrevious}")
        assertEquals(
            listOf(StripIntent.JumpLetter("snes", 3, forward = true), StripIntent.JumpLetter("snes", 3, forward = false)),
            intents,
        )
    }

    @Test
    fun favoriteAndSearchByTouchTapTheirHints() {
        showScreen()
        // On the system column Y has nothing to mark (and no hint there); X opens search.
        tap("hint${Action.ToggleFavorite}")
        tap("hint${Action.Search}")
        tap("system2")
        tap("game1")
        tap("hint${Action.ToggleFavorite}")
        tap("hint${Action.Search}")
        assertEquals(listOf(StripIntent.OpenSearch, StripIntent.ToggleFavorite("snes", 1), StripIntent.OpenSearch), intents)
    }
}
