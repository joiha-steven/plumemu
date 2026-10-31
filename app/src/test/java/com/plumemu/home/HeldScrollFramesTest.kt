package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Holding Up or Down in a long list, frame by frame on the test clock, through the real repeat
 * path (`InputRouter` and its `RepeatPacer`) and the real main screen. Every frame, the focused
 * row is drawn (its laid-out place plus the glide's translation) wholly inside the list, and the
 * row past it in the direction of travel is too: the focus never rides the edge, never sits under
 * the top bar or the hint bar. Seen on an AYN Thor: the focused row half under
 * the top bar for the whole hold, because the list scrolled the focus just to its edge and the
 * glide then drew it up to a row further out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HeldScrollFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private lateinit var state: StripState

    /** The focused row as last composed: what the frame drawn after it shows. */
    private var composedFocus = -1

    private fun show(
        content: HomeContent,
        textSize: TextSize,
        gridIds: Set<String> = emptySet(),
        focusOf: (StripState) -> Int,
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, InputSettings(holdToScrollFaster = true)) }
            // The glide tells held jumps apart on frame time, not on how fast this machine runs.
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(PlumThemeSettings(textSize = textSize)) {
                    HomeAt(content, isMotionOn = PlumTheme.motion.isOn, onAction = router::send, onState = { state = it }, gridIds = gridIds)
                    // HomeScreen's path: the router's actions, repeats included, dispatched to the strip.
                    LaunchedEffect(router) { router.actions.collect { state.dispatch(it) } }
                    FocusProbe { focusOf(state) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Records the focus in the composition that lays the list out for it. */
    @Composable
    private fun FocusProbe(focus: () -> Int) {
        val shown = focus()
        SideEffect { composedFocus = shown }
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun key(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    /** One drawn frame: where the focused row and the row past it stand against the list. */
    private data class Frame(
        val at: Int,
        val focus: Int,
        val list: Rect,
        val focused: Rect,
        val ahead: Rect?,
    )

    /**
     * Presses [code], keeps it held for [heldFrames] frames when [hold] or releases it in the same
     * frame, then lets [restFrames] frames pass; records every frame.
     */
    private fun pressAndRecord(
        code: Int,
        hold: Boolean,
        heldFrames: Int,
        restFrames: Int,
        tagOf: (Int) -> String,
        listTag: String,
        count: Int,
        frames: MutableList<Frame>,
    ) {
        val step = if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_DOWN, code)) }
        if (!hold) compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_UP, code)) }
        repeat(heldFrames + restFrames) { i ->
            if (hold && i == heldFrames) compose.runOnIdle { router.onKeyEvent(key(KeyEvent.ACTION_UP, code)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val focus = composedFocus
            val next = focus + step
            val ahead = if (next in 0 until count) drawnBounds(tagOf(next)) ?: NOT_COMPOSED else null
            frames += Frame(frames.size, focus, boundsOf(listTag), drawnBounds(tagOf(focus))!!, ahead)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun boundsOf(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let { r -> with(compose.density) { Rect(r.left.toPx(), r.top.toPx(), r.right.toPx(), r.bottom.toPx()) } }

    /** Where a row is drawn, glide included and not clipped: null when it is not composed. */
    private fun drawnBounds(tag: String): Rect? {
        val nodes = compose.onAllNodesWithTagUnmerged(tag)
        val node = nodes.firstOrNull() ?: return null
        val at = node.positionInRoot
        return Rect(at.x, at.y, at.x + node.size.width, at.y + node.size.height)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagUnmerged(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()

    private fun Float.dp(): String = "%.1f".format(this / compose.density.density)

    /**
     * The frames where the focused row is not drawn whole inside the list, or the row past it in
     * the direction of travel is not (when there is one): what the person sees as the list
     * bunching at its edge.
     */
    private fun badFrames(
        frames: List<Frame>,
        up: Boolean,
    ): List<String> =
        frames.mapNotNull { f ->
            val focusOut = f.focused.top < f.list.top - SLACK || f.focused.bottom > f.list.bottom + SLACK
            val ahead = f.ahead
            val aheadOut = ahead != null && (ahead.top < f.list.top - SLACK || ahead.bottom > f.list.bottom + SLACK)
            if (!focusOut && !aheadOut) return@mapNotNull null
            val edge = if (up) "top" else "bottom"
            val focusGap = if (up) f.focused.top - f.list.top else f.list.bottom - f.focused.bottom
            val aheadGap = ahead?.let { if (up) it.top - f.list.top else f.list.bottom - it.bottom }
            "frame ${f.at} row ${f.focus}: focus ${focusGap.dp()} dp inside the $edge, next row ${aheadGap?.dp() ?: "-"} dp"
        }

    /**
     * The grid's frames where the focused cover is cut by the grid's edge when it should stand whole. While the grid
     * glides, the new row slides in from the edge and the grid's clip may cut it, but only within
     * [GLIDE_FRAMES] frames of the last move of the focus (a glide lasts 200 ms, plus one frame); any later frame must
     * have the cover whole, so a cover that stays clipped through a hold or after it is caught. The cover must also
     * stand whole once the clock has run on after the run (checked in [gridHeld]).
     */
    private fun badGridFrames(frames: List<Frame>): List<String> {
        var lastMove = 0
        return frames.mapIndexedNotNull { i, f ->
            if (i > 0 && frames[i - 1].focus != f.focus) lastMove = i
            val out = f.focused.top < f.list.top - SLACK || f.focused.bottom > f.list.bottom + SLACK
            if (out && i - lastMove > GLIDE_FRAMES) "frame ${f.at} row ${f.focus}: cover at ${f.focused.top.dp()}..${f.focused.bottom.dp()} dp, grid ${f.list.top.dp()}..${f.list.bottom.dp()} dp, ${i - lastMove} frames after the last move" else null
        }
    }

    /** Summary of a run, for the report and the failure message: worst focus and next-row gaps. */
    private fun summary(
        name: String,
        frames: List<Frame>,
        up: Boolean,
    ): String {
        val focusGaps = frames.map { f -> if (up) f.focused.top - f.list.top else f.list.bottom - f.focused.bottom }
        val aheadGaps = frames.mapNotNull { f -> f.ahead?.let { if (up) it.top - f.list.top else f.list.bottom - it.bottom } }
        val bad = badFrames(frames, up)
        return "$name: ${frames.size} frames, rows ${frames.first().focus}->${frames.last().focus}, " +
            "focus gap min ${focusGaps.min().dp()} dp, next-row gap min ${aheadGaps.minOrNull()?.dp() ?: "-"} dp, ${bad.size} bad frames"
    }

    private fun gamesList(
        textSize: TextSize,
        label: String,
    ) {
        val content = FakeHomeContent.large(GAMES_PER_SYSTEM * 18)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        show(content, textSize) { it.focus.rowOf(content.entries[system].id) }
        val count = content.entries[system].rows.size
        send(Action.TapItem(Column.Systems, system), Action.Enter, Action.TapItem(Column.Games, START_ROW))
        val results = mutableListOf<String>()
        for ((code, hold) in listOf(KeyEvent.KEYCODE_DPAD_DOWN to true, KeyEvent.KEYCODE_DPAD_UP to true, KeyEvent.KEYCODE_DPAD_DOWN to false, KeyEvent.KEYCODE_DPAD_UP to false)) {
            val up = code == KeyEvent.KEYCODE_DPAD_UP
            val frames = mutableListOf<Frame>()
            if (hold) {
                pressAndRecord(code, hold = true, heldFrames = HELD_FRAMES, restFrames = REST_FRAMES, tagOf = HomeTags::game, listTag = HomeTags.GAMES, count = count, frames = frames)
            } else {
                repeat(PRESSES) {
                    pressAndRecord(code, hold = false, heldFrames = 0, restFrames = REST_FRAMES, tagOf = HomeTags::game, listTag = HomeTags.GAMES, count = count, frames = frames)
                }
            }
            val name = "$label games ${if (hold) "held" else "pressed"} ${if (up) "Up" else "Down"}"
            // A hold that moved nothing would prove nothing.
            assertTrue("$name moved the focus", frames.first().focus != frames.last().focus)
            results += summary(name, frames, up) + "\n    " + badFrames(frames, up).take(6).joinToString("\n    ")
        }
        println(results.joinToString("\n"))
        assertTrue(results.joinToString("\n"), results.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    private fun systemsColumn(
        textSize: TextSize,
        label: String,
    ) {
        val content = FakeHomeContent.large(GAMES_PER_SYSTEM * 18)
        show(content, textSize) { it.focus.systemIndex }
        val count = content.entries.size
        // The screen opens on the system column, at its first entry.
        val results = mutableListOf<String>()
        for ((code, hold) in listOf(KeyEvent.KEYCODE_DPAD_DOWN to true, KeyEvent.KEYCODE_DPAD_UP to true, KeyEvent.KEYCODE_DPAD_DOWN to false, KeyEvent.KEYCODE_DPAD_UP to false)) {
            val up = code == KeyEvent.KEYCODE_DPAD_UP
            val frames = mutableListOf<Frame>()
            if (hold) {
                pressAndRecord(code, hold = true, heldFrames = HELD_FRAMES, restFrames = REST_FRAMES, tagOf = HomeTags::system, listTag = HomeTags.SYSTEMS, count = count, frames = frames)
            } else {
                repeat(PRESSES) {
                    pressAndRecord(code, hold = false, heldFrames = 0, restFrames = REST_FRAMES, tagOf = HomeTags::system, listTag = HomeTags.SYSTEMS, count = count, frames = frames)
                }
            }
            val name = "$label systems ${if (hold) "held" else "pressed"} ${if (up) "Up" else "Down"}"
            // A hold that moved nothing would prove nothing.
            assertTrue("$name moved the focus", frames.first().focus != frames.last().focus)
            results += summary(name, frames, up) + "\n    " + badFrames(frames, up).take(6).joinToString("\n    ")
        }
        println(results.joinToString("\n"))
        assertTrue(results.joinToString("\n"), results.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    /**
     * Holds to the end of a long list and wraps past both ends: the rows the list has never laid
     * out are placed by estimate, and the glide has no row of context to draw into there.
     */
    private fun gamesListEndsAndWrap(
        textSize: TextSize,
        label: String,
    ) {
        val content = FakeHomeContent.large(GAMES_PER_SYSTEM * 18)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        show(content, textSize) { it.focus.rowOf(content.entries[system].id) }
        val count = content.entries[system].rows.size
        send(Action.TapItem(Column.Systems, system), Action.Enter, Action.TapItem(Column.Games, count - 12))
        val down = KeyEvent.KEYCODE_DPAD_DOWN
        val up = KeyEvent.KEYCODE_DPAD_UP
        val steps =
            listOf(
                Triple("held Down to the end", down, 1),
                Triple("pressed Down, wrapping to the top", down, 1),
                Triple("pressed Down", down, 10),
                Triple("held Up to the top", up, 1),
                Triple("pressed Up, wrapping to the end", up, 1),
            )
        val results = mutableListOf<String>()
        for ((name, code, presses) in steps) {
            val hold = name.startsWith("held")
            val frames = mutableListOf<Frame>()
            repeat(presses) {
                pressAndRecord(code, hold = hold, heldFrames = if (hold) HELD_FRAMES else 0, restFrames = REST_FRAMES, tagOf = HomeTags::game, listTag = HomeTags.GAMES, count = count, frames = frames)
            }
            results += summary("$label games $name", frames, code == up) + "\n    " + badFrames(frames, code == up).take(6).joinToString("\n    ")
        }
        assertTrue("reached the end and wrapped: $results", results[0].contains("->${count - 1},") && results[1].contains("->0,") && results[4].contains("->${count - 1},"))
        println(results.joinToString("\n"))
        assertTrue(results.joinToString("\n"), results.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    @Test
    @Config(qualifiers = RP5)
    fun gamesListEndsAndWrapRp5Medium() = gamesListEndsAndWrap(TextSize.Medium, "768x432 Medium")

    @Test
    @Config(qualifiers = THOR)
    fun gamesListEndsAndWrapThorLarge() = gamesListEndsAndWrap(TextSize.Large, "837x471 Large")

    /**
     * A list opens where it always did: the system column rebuilt with the focus near its end shows
     * the focused system at the list's bottom edge, with no context kept until the first move, and a
     * later layout with nothing pressed (the content arriving again) moves nothing. Keeping context
     * from the opening scrolled it two rows further, then glided.
     */
    @Test
    @Config(qualifiers = RP5)
    fun theSystemColumnOpensAtItsEdgeAndALaterLayoutMovesNothing() {
        val content = mutableStateOf(FakeHomeContent.large(GAMES_PER_SYSTEM * 18))
        val far = content.value.entries.size - 3
        compose.setContent {
            PlumTheme(PlumThemeSettings(textSize = TextSize.Medium)) {
                HomeAt(content.value, listOf(Action.TapItem(Column.Systems, far)), isMotionOn = PlumTheme.motion.isOn)
            }
        }
        compose.waitForIdle()
        val opened = drawnBounds(HomeTags.system(far))!!
        val list = boundsOf(HomeTags.SYSTEMS)
        assertEquals("the focused system at the list's bottom edge, as before (dp)", 0f, (list.bottom - opened.bottom) / compose.density.density, 0.5f)
        compose.runOnIdle { content.value = FakeHomeContent.large(GAMES_PER_SYSTEM * 18) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("a later layout moved nothing", opened, drawnBounds(HomeTags.system(far)))
    }

    /**
     * One Move Down every other frame, [sleepMs] of real time before each frame: the drawn top of
     * [probeRow] per frame, as the change from the frame before.
     */
    private fun glideSteps(
        startRow: Int,
        probeRow: Int,
        sleepMs: Long,
        everyFrames: Int = 2,
        frames: Int = 16,
        gapFrames: List<Int>? = null,
    ): List<Float> {
        // Frames that carry a Move: every [everyFrames], or after each of [gapFrames] in turn (a real hold's cadence).
        val moveFrames = gapFrames?.runningFold(0) { at, gap -> at + gap }?.toSet()
        send(Action.TapItem(Column.Games, startRow))
        compose.mainClock.autoAdvance = false
        val tops = mutableListOf<Float>()
        repeat(frames) { f ->
            if (if (moveFrames != null) f in moveFrames else f % everyFrames == 0) compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            if (sleepMs > 0) Thread.sleep(sleepMs)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            tops += drawnBounds(HomeTags.game(probeRow))?.top ?: -9999f
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        println("glide tops (dp) sleep $sleepMs: " + tops.map { (it / compose.density.density).toInt() })
        return tops.zipWithNext { a, b -> b - a }
    }

    /**
     * The glide follows frame time, not the clock of the machine: a Move every other frame glides
     * the same whether the frames come at once or 130 ms of real time apart (the 110 ms window
     * and the linear-or-ease-out choice once read System.nanoTime: a fast machine finished each
     * glide within a frame, a slow one drew a longer ease-out). And in between jumps the rows are
     * really mid-glide: a frame with a nonzero step that is not a whole row.
     */
    @Test
    @Config(qualifiers = RP5)
    fun theGlideFollowsFrameTimeNotTheMachinesClock() {
        val content = FakeHomeContent.large(GAMES_PER_SYSTEM * 18)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        show(content, TextSize.Medium) { it.focus.rowOf(content.entries[system].id) }
        send(Action.TapItem(Column.Systems, system), Action.Enter)
        val fast = glideSteps(startRow = 20, probeRow = 22, sleepMs = 0)
        // Let the fast run's glide settle: the slow run must start from rest, as the fast one did (the longer glide would otherwise carry over).
        compose.mainClock.advanceTimeBy(1_000)
        val slow = glideSteps(startRow = 40, probeRow = 42, sleepMs = 130)
        println("glide steps fast: $fast\nglide steps slow: $slow")
        assertEquals("the same glide with real time between frames", fast, slow)
    }

    /**
     * Held Down (a Move every 5 frames, about 80 ms, and every 2 frames, the fastest repeat): the rows keep moving up the screen in
     * every frame once the first move has come, and no frame draws them back down. A glide that
     * restarted from zero on each repeat showed as a frame (the repeat's) with the rows jumping
     * back by what was left, then a stop.
     */
    @Test
    @Config(qualifiers = RP5)
    fun aHeldDirectionMovesTheRowsContinuouslyWithNoFrameThatResets() {
        val content = FakeHomeContent.large(GAMES_PER_SYSTEM * 18)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        show(content, TextSize.Medium) { it.focus.rowOf(content.entries[system].id) }
        send(Action.TapItem(Column.Systems, system), Action.Enter)
        // Leading frames where the probe row is not composed yet (a sentinel) are not moves.
        // Cadences: 80 ms repeats; the fastest (2 frames); and a real hold: first repeat after 300 ms (19 frames), then 120 ms
        // getting 20% quicker down to 32 ms (HoldRepeat, whole frames).
        val accelerating = listOf(19, 7, 6, 5, 4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2)
        val cadences =
            listOf(
                Triple(listOf(20, 4), 40, 5 to null),
                Triple(listOf(60, 6), 16, 2 to null),
                Triple(listOf(100, 8), 60, 0 to accelerating),
            )
        for ((place, frames, cadence) in cadences) {
            val (start, ahead) = place
            val (every, gaps) = cadence
            val steps =
                glideSteps(startRow = start, probeRow = start + ahead, sleepMs = 0, everyFrames = every.coerceAtLeast(1), frames = frames, gapFrames = gaps)
                    .dropWhile { it == 0f || kotlin.math.abs(it) > 5000f }
            println("held glide steps every $every frames ($gaps): $steps")
            assertTrue("no frame moves the rows back down: $steps", steps.all { it <= 0.5f })
            val moving = steps.drop(2).dropLast(4)
            assertTrue("every frame while held moves the rows (no stop between repeats): $steps", moving.all { it < -0.5f })
            // No forward jump either: a cut shows as one frame moving the rows much further than the rest.
            // 1.3: the hold's first step, glided from what is left of the press's glide over the first (shorter) gap, runs up to a quarter faster.
            if (gaps == null) {
                val sizes = moving.map { -it }.sorted()
                val median = sizes[sizes.size / 2]
                assertTrue("no frame moves the rows more than 1.3x the median step ($median): $steps", sizes.last() <= 1.3f * median)
            } else {
                // A hold that quickens must move faster, so a step cannot be held to the median; it can be held to the step before it:
                // a cut (the rows owed eaten by the room) shows as a frame moving them 2x or more as far as the one before. The repeats
                // themselves, in whole frames, speed up by up to 1.5x (3 frames to 2) in one go.
                moving.map { -it }.zipWithNext().forEach { (before, now) ->
                    assertTrue("no frame moves the rows more than 1.75x the frame before it: $steps", now <= 1.75f * before)
                }
            }
        }
    }

    /**
     * The grid: holding or pressing Up and Down through a long Super Nintendo grid,
     * the focused cover is drawn whole inside the grid at rest and once a glide ends; during a glide the
     * grid's edge may cut it ([badGridFrames]). Its rows are taller than the room a list keeps, so the row
     * past it is not checked.
     */
    private fun gridHeld(
        textSize: TextSize,
        label: String,
    ) {
        val content = largeWithBoxArt(GAMES_PER_SYSTEM * 18)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        val entry = content.entries[system]
        show(content, textSize, gridIds = setOf(entry.id)) { it.focus.rowOf(entry.id) }
        send(Action.TapItem(Column.Systems, system), Action.Enter, Action.TapItem(Column.Games, START_ROW))
        val results = mutableListOf<String>()
        for ((code, hold) in listOf(KeyEvent.KEYCODE_DPAD_DOWN to true, KeyEvent.KEYCODE_DPAD_UP to true, KeyEvent.KEYCODE_DPAD_DOWN to false, KeyEvent.KEYCODE_DPAD_UP to false)) {
            val up = code == KeyEvent.KEYCODE_DPAD_UP
            val frames = mutableListOf<Frame>()
            // count = 0: no "next row" to check (a grid row is taller than the room kept past the focus).
            if (hold) {
                pressAndRecord(code, hold = true, heldFrames = HELD_FRAMES, restFrames = REST_FRAMES, tagOf = HomeTags::cover, listTag = HomeTags.GRID, count = 0, frames = frames)
            } else {
                repeat(PRESSES) {
                    pressAndRecord(code, hold = false, heldFrames = 0, restFrames = REST_FRAMES, tagOf = HomeTags::cover, listTag = HomeTags.GRID, count = 0, frames = frames)
                }
            }
            val name = "$label grid ${if (hold) "held" else "pressed"} ${if (up) "Up" else "Down"}"
            assertTrue("$name moved the focus", frames.first().focus != frames.last().focus)
            // The glide has ended: the focused cover stands whole in the grid.
            val settled = frames.last().let { f -> drawnBounds(HomeTags.cover(f.focus))!! to boundsOf(HomeTags.GRID) }
            val cut = settled.first.top < settled.second.top - SLACK || settled.first.bottom > settled.second.bottom + SLACK
            val bad = badGridFrames(frames) + if (cut) listOf("settled cover ${settled.first} is cut by the grid ${settled.second}") else emptyList()
            results += "$name: ${frames.size} frames, rows ${frames.first().focus}->${frames.last().focus}, ${bad.size} bad frames\n    " + bad.take(6).joinToString("\n    ")
        }
        println(results.joinToString("\n"))
        assertTrue(results.joinToString("\n"), results.none { it.contains(Regex(", [1-9][0-9]* bad frames")) })
    }

    @Test
    @Config(qualifiers = RP5)
    fun gridRp5Medium() = gridHeld(TextSize.Medium, "768x432 Medium")

    @Test
    @Config(qualifiers = THOR)
    fun gridThorLarge() = gridHeld(TextSize.Large, "837x471 Large")

    @Test
    @Config(qualifiers = RP5)
    fun systemsColumnRp5Medium() = systemsColumn(TextSize.Medium, "768x432 Medium")

    @Test
    @Config(qualifiers = RP5)
    fun gamesListRp5Medium() = gamesList(TextSize.Medium, "768x432 Medium")

    @Test
    @Config(qualifiers = RP5)
    fun gamesListRp5Large() = gamesList(TextSize.Large, "768x432 Large")

    @Test
    @Config(qualifiers = THOR)
    fun gamesListThorMedium() = gamesList(TextSize.Medium, "837x471 Medium")

    @Test
    @Config(qualifiers = THOR)
    fun gamesListThorLarge() = gamesList(TextSize.Large, "837x471 Large")

    @Test
    @Config(qualifiers = RP5)
    fun systemsColumnRp5Large() = systemsColumn(TextSize.Large, "768x432 Large")

    @Test
    @Config(qualifiers = THOR)
    fun systemsColumnThorMedium() = systemsColumn(TextSize.Medium, "837x471 Medium")

    @Test
    @Config(qualifiers = THOR)
    fun systemsColumnThorLarge() = systemsColumn(TextSize.Large, "837x471 Large")

    private companion object {
        const val RP5 = "w768dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"
        const val GAMES_PER_SYSTEM = 200
        const val START_ROW = 60

        /** About 1.6 s of holding at the test clock's 16 ms frames: the floor of 32 ms is reached. */
        const val HELD_FRAMES = 100

        /** A glide's 200 ms at the test clock's 16 ms frames (13), plus one frame. */
        const val GLIDE_FRAMES = 14
        const val REST_FRAMES = 12
        const val PRESSES = 12

        /** A row past the focus that exists but is not even composed: far outside the list. */
        val NOT_COMPOSED = Rect(0f, -1e6f, 0f, -1e6f + 1f)

        /** Rounding of px positions. */
        const val SLACK = 0.5f
    }
}

