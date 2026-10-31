package com.plumemu.settings.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.MainScreen
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Settings' lists move like home's (the PS Vita's way): the focus pill glides from row to row over
 * [PlumMotion.GLIDE_MS] on a decelerating curve and the rows glide when the list scrolls, in the sections column, the options
 * column and a panel list ([PanelLazyList]); a held key keeps the pill with the focus; with animations off everything jumps.
 * The pill is a draw, not a node, so it is found in the pixels the screen draws: the first pixel row of the list's middle
 * that is the ring's colour is its top edge. Frame by frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class SettingsGlidingPillFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter

    private fun settings(
        animationsOn: Boolean,
        textSize: TextSizeSetting = TextSizeSetting.Medium,
    ) = PlumSettings(
        flags = SetupFlags(setupCompleted = true, homeRoleAsked = true),
        appearance = AppearanceSettings(textSize = textSize),
        advanced = AdvancedSettings(animations = animationsOn),
    )

    private fun show(
        animationsOn: Boolean,
        content: SettingsContent = SettingsContent(),
        textSize: TextSizeSetting = TextSizeSetting.Medium,
    ) {
        val host = SettingsHost(versionName = "test", update = {}, resetAllSettings = {}, resetLibrary = {}, content = flowOf(content))
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                MainScreen(settings(animationsOn, textSize), HomeState.Ready(FakeHomeContent.board()), router, HomeCallbacks(), onRetry = {}, settingsHost = host)
            }
        }
        compose.waitForIdle()
        send(Action.Move(Direction.Left), Action.Menu)
    }

    private fun send(vararg actions: Action) {
        for (action in actions) {
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun down(times: Int) = repeat(times) { send(Action.Move(Direction.Down)) }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun rowTop(tag: String): Float = bounds(tag).top

    private fun ringColour(rowTop: Float, listTag: String): Color {
        val list = bounds(listTag)
        return compose.onRoot().captureToImage().toPixelMap()[(list.left + list.width * 0.55f).toInt(), rowTop.toInt() + 1]
    }

    /** The top of the pill now: the first pixel row of the list where most of a middle band is the ring's colour; -1 when none. */
    private fun pillTop(ring: Color, listTag: String): Int {
        val list = bounds(listTag)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val from = (list.left + list.width * 0.45f).toInt()
        val to = (list.left + list.width * 0.65f).toInt()
        for (y in list.top.toInt() until list.bottom.toInt()) {
            val hits = (from until to).count { x -> pixels[x, y].isNear(ring) }
            if (hits * 2 >= to - from) return y
        }
        return -1
    }

    private fun Color.isNear(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.12f

    /** One drawn frame: the pill's top, and the tops of the rows [follow] names. */
    private class Frame(
        val pill: Int,
        val rows: List<Float>,
    )

    private fun record(
        frames: Int,
        ring: Color,
        listTag: String,
        follow: List<String>,
        before: (Int) -> Unit = {},
    ): List<Frame> {
        compose.mainClock.autoAdvance = false
        return (0 until frames).map { i ->
            before(i)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            Frame(pillTop(ring, listTag), follow.map { rowTop(it) })
        }
    }

    private fun glideFrames() = PlumMotion.GLIDE_MS / FRAME_MS

    /** Presses Down once at rest on row [from] and records the pill. Returns the frames, the old row's top and the new row's top. */
    private fun oneStep(
        listTag: String,
        rowTag: (Int) -> String,
        from: Int,
    ): Triple<List<Frame>, Float, Float> {
        val oldTop = rowTop(rowTag(from))
        val newTop = rowTop(rowTag(from + 1))
        val ring = ringColour(oldTop, listTag)
        val background = compose.onRoot().captureToImage().toPixelMap()[(bounds(listTag).left + bounds(listTag).width * 0.55f).toInt(), (oldTop - 2).toInt()]
        assertTrue("the focused row wears a pill (its top edge is not the background): $ring on $background", !ring.isNear(background))
        assertEquals("at rest the pill is on the focused row", oldTop.toInt(), pillTop(ring, listTag))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.Move(Direction.Down)) }
        compose.mainClock.autoAdvance = true
        val frames = record(glideFrames() + 4, ring, listTag, listOf(rowTag(from + 1)))
        return Triple(frames, oldTop, newTop)
    }

    private fun assertPillGlides(
        what: String,
        result: Triple<List<Frame>, Float, Float>,
    ) {
        val (frames, oldTop, newTop) = result
        val tops = frames.map { it.pill }
        val old = oldTop.toInt()
        val new = newTop.toInt()
        println("$what pill tops (old $old, new $new): $tops")
        assertTrue("$what: never above the old row or below the new one: $tops", tops.all { it in old..new })
        assertTrue("$what: only moves down: $tops", tops.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("$what: first frame covers at most 30% of the way: $tops", tops.first() - old <= (new - old) * FIRST_FRAME_SHARE)
        assertTrue("$what: first frame is not on the new row: $tops", tops.first() < new)
        assertTrue("$what: still travelling after a third of the glide: $tops", tops[glideFrames() / 3] < new)
        assertEquals("$what: on the new row within the glide and a frame: $tops", new, tops[glideFrames() + 1])
        assertEquals("$what: ends exactly on the row: $tops", new, tops.last())
    }

    private fun emulators(): SettingsContent =
        SettingsContent(systems = List(SYSTEMS) { SystemEmulators("system$it", "System $it", automatic = null, chosen = null, installed = emptyList()) })

    private fun emulatorRowTag(content: SettingsContent): (Int) -> String {
        val rows = SettingsSection.Emulators.rowsIn(content)
        return { i -> if (rows[i] == SettingsOption.SystemEmulator) SettingsTags.system(i) else SettingsTags.option(rows[i]) }
    }

    /** Emulators' options with the focus on its first row. */
    private fun openEmulators(animationsOn: Boolean, textSize: TextSizeSetting = TextSizeSetting.Medium): (Int) -> String {
        val content = emulators()
        show(animationsOn, content, textSize)
        down(2)
        send(Action.Enter)
        return emulatorRowTag(content)
    }

    private fun sectionTag(i: Int) = SettingsTags.section(SettingsSection.entries[i])

    @Test
    fun theSectionsPillGlidesFromTheOldRowToTheNewOne() {
        show(true)
        assertPillGlides("sections", oneStep(SettingsTags.SECTIONS, ::sectionTag, 0))
    }

    @Test
    fun theOptionsPillGlidesFromTheOldRowToTheNewOne() {
        val tag = openEmulators(true)
        // Row 1 is a system row: row 0 is the detect row, a different height from it.
        assertPillGlides("options", oneStep(SettingsTags.OPTION_LIST, tag, 0))
    }

    @Test
    fun theSectionsListGlidesWhenItScrolls() {
        // At Largest the seven sections do not fit: the last steps scroll the column.
        show(true, textSize = TextSizeSetting.Largest)
        // The first step that moves the list is found by pressing; the next one scrolls it too.
        var from = 0
        while (true) {
            val before = rowTop(sectionTag(from + 1))
            down(1)
            from++
            if (rowTop(sectionTag(from)) != before) break
            assertTrue("the sections column scrolls at Largest", from < SettingsSection.entries.size - 2)
        }
        assertScrollGlide("sections", SettingsTags.SECTIONS, ::sectionTag, from)
    }

    @Test
    fun theOptionsListGlidesWhenItScrolls() {
        val tag = openEmulators(true)
        down(10)
        assertScrollGlide("options", SettingsTags.OPTION_LIST, tag, 10)
    }

    /** One step down from row [from] that makes the list scroll: the new row's drawn top travels to its place, inside the list on every frame. */
    private fun assertScrollGlide(
        what: String,
        listTag: String,
        rowTag: (Int) -> String,
        from: Int,
    ) {
        val ring = ringColour(rowTop(rowTag(from)), listTag)
        val list = bounds(listTag)
        val before = rowTop(rowTag(from + 1))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.Move(Direction.Down)) }
        val frames = record(glideFrames() + 4, ring, listTag, listOf(rowTag(from + 1), rowTag(from)))
        val tops = frames.map { it.rows[0] }
        val last = tops.last()
        println("$what scroll: new row tops $tops, pill ${frames.map { it.pill }}")
        assertTrue("$what: the list scrolled, so the row was drawn off its place at first: $tops", abs(tops.first() - last) > 1f)
        // The glide starts in the frame of the move: a jump would show the row at its place at once.
        assertTrue("$what: the first frame still has at least 60% of the way to go: $tops", abs(tops.first() - last) >= 0.6f * abs(before - last))
        assertTrue("$what: arrives and stays: $tops", tops.takeLast(3).all { it == last })
        assertTrue("$what: more than a few distinct places on the way (a glide, not a jump): $tops", tops.distinct().size > 4)
        assertTrue("$what: moves one way: $tops", tops.zipWithNext().all { (a, b) -> if (tops.first() > last) b <= a else b >= a })
        assertTrue("$what: the new row stays inside the list on every frame: $tops in $list", tops.all { it >= list.top - 1f && it <= list.bottom })
        assertTrue("$what: the pill is drawn on every frame", frames.all { it.pill >= 0 })
    }

    @Test
    fun aHeldKeyKeepsTheOptionsPillWithTheFocus() {
        val tag = openEmulators(true)
        val first = rowTop(tag(0))
        val stride = rowTop(tag(1)) - first
        val ring = ringColour(first, SettingsTags.OPTION_LIST)
        compose.mainClock.autoAdvance = false
        var focus = 0
        val worst = mutableListOf<String>()
        var last = -1
        val frames =
            record(80, ring, SettingsTags.OPTION_LIST, emptyList()) { i ->
                if (i % HOLD_REPEAT_FRAMES == 0 && focus < 14) {
                    compose.runOnIdle { router.send(Action.Move(Direction.Down)) }
                    focus++
                }
            }
        val tops = frames.map { it.pill }
        // The pill rides with the focused row: within a stride of its drawn place at the end, drawn on every frame, and never far from a neighbour.
        val rowNow = rowTop(tag(focus))
        tops.forEachIndexed { i, top ->
            if (top < 0) worst += "frame $i drew no pill"
            if (last >= 0 && top >= 0 && abs(top - last) > stride * 1.5f) worst += "frame $i jumped from $last to $top"
            last = top
        }
        println("held pill tops: $tops (focus $focus, row at $rowNow, stride $stride)")
        assertEquals("never jumps: $worst", emptyList<String>(), worst)
        assertTrue("the focus moved down several rows: $focus", focus >= 10)
        assertTrue("within a row of the focused row at the end: pill ${tops.last()} row $rowNow stride $stride", abs(rowNow - tops.last()) <= 1)
    }

    @Test
    fun aPanelListPillGlidesFromTheOldRowToTheNewOne() {
        show(true)
        down(SettingsSection.entries.indexOf(SettingsSection.Advanced))
        send(Action.Enter)
        down(SettingsSection.Advanced.options.indexOf(SettingsOption.Language))
        send(Action.Enter) // the language list, its first row focused
        // The panel fades in over 110 ms: let it finish.
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        Thread.sleep(400)
        // It opens on the language in use (the device's): the row whose top edge is not the background wears the pill.
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val list = bounds(SettingsTags.PANEL_LIST)
        val from = (0..4).first { pixels[(list.left + list.width * 0.55f).toInt(), rowTop(SettingsTags.panelItem(it)).toInt() + 1] != Color.Black }
        assertPillGlides("panel list", oneStep(SettingsTags.PANEL_LIST, SettingsTags::panelItem, from))
    }

    /** Pressing Right from a section: the options have the focus, and only they wear a pill; the sections keep none (a Current section is quiet). */
    @Test
    fun onlyTheFocusedColumnWearsAPillFromSectionsToOptions() {
        show(true)
        val ring = ringColour(rowTop(sectionTag(0)), SettingsTags.SECTIONS)
        assertTrue("the sections wear a pill while they have the focus", pillTop(ring, SettingsTags.SECTIONS) >= 0)
        send(Action.Move(Direction.Right))
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals("no pill colour is left in the sections column", -1, pillTop(ring, SettingsTags.SECTIONS))
        assertTrue("the options column wears one", pillTop(ring, SettingsTags.OPTION_LIST) >= 0)
    }

    /** Opening a panel from an option row: the panel wears the pill; the options keep none. */
    @Test
    fun onlyTheFocusedColumnWearsAPillFromOptionsToPanel() {
        show(true)
        val ring = ringColour(rowTop(sectionTag(0)), SettingsTags.SECTIONS)
        down(SettingsSection.entries.indexOf(SettingsSection.Advanced))
        send(Action.Enter)
        down(SettingsSection.Advanced.options.indexOf(SettingsOption.Language))
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertTrue("the options wear a pill while they have the focus", pillTop(ring, SettingsTags.OPTION_LIST) >= 0)
        send(Action.Enter)
        Thread.sleep(400)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals("no pill colour is left in the options column", -1, pillTop(ring, SettingsTags.OPTION_LIST))
        assertTrue("the panel wears one", pillTop(ring, SettingsTags.PANEL_LIST) >= 0)
    }

    @Test
    fun withAnimationsOffThePillAndTheRowsJumpInTheFirstFrame() {
        show(false)
        val (frames, _, newTop) = oneStep(SettingsTags.SECTIONS, ::sectionTag, 0)
        assertEquals("sections, animations off: the pill is on the new row at once: ${frames.map { it.pill }}", List(frames.size) { newTop.toInt() }, frames.map { it.pill })
    }

    @Test
    fun withAnimationsOffAScrollingOptionsListJumps() {
        val tag = openEmulators(false)
        down(10)
        val ring = ringColour(rowTop(tag(10)), SettingsTags.OPTION_LIST)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.send(Action.Move(Direction.Down)) }
        val frames = record(6, ring, SettingsTags.OPTION_LIST, listOf(tag(11)))
        val tops = frames.map { it.rows[0] }
        println("animations off options scroll: row tops $tops, pill ${frames.map { it.pill }}")
        assertTrue("the row is at its place from the first frame: $tops", tops.all { it == tops.first() })
        assertTrue("the pill is on the row from the first frame: ${frames.map { it.pill }}", frames.all { it.pill == tops.first().toInt() })
    }

    private companion object {
        const val FRAME_MS = 16
        const val FIRST_FRAME_SHARE = 0.3f
        const val HOLD_REPEAT_FRAMES = 4
        const val SYSTEMS = 25
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsGlidingPillFramesTest : SettingsGlidingPillFramesBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsGlidingPillFrames576Test : SettingsGlidingPillFramesBase()
