package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.components.slabOverhang
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * What Settings' options list draws at its ends, at the three reference sizes in both themes: the focused last row's glow below it fades out (the
 * list's clip keeps [SlabDimens.listEndRoom] past the row, as the home lists do), and a row whose top lies below the visible window draws nothing
 * (no strip of its control or its shadow above the hint keys).
 */
abstract class SettingsListEndsBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var theme by mutableStateOf(TableText.Graphite)
    private var density = Density(1f)

    private val themes = listOf("Graphite" to TableText.Graphite, "Paper" to TableText.Paper)

    private var focus by mutableStateOf(SettingsFocus())

    /** Shows each of [focuses] in both themes (one setContent per test). */
    private fun eachFocus(
        focuses: List<SettingsFocus>,
        each: (SettingsFocus, String) -> Unit,
    ) {
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(theme) { SettingsAt(focus, content = ShotContent.library) }
        }
        for (at in focuses) {
            focus = at
            for ((name, shown) in themes) {
                theme = shown
                compose.waitForIdle()
                each(at, name)
            }
        }
    }

    private fun px(dp: Dp) = with(density) { dp.toPx() }

    /** Every section with option rows (Emulators adds a row per system, Statistics has none). */
    private val fixedSections = shownSections.filter { it.options.isNotEmpty() && it != SettingsSection.Emulators }

    private fun rowsOf(section: SettingsSection) = section.rowsIn(ShotContent.library)

    private fun lastRowFocus(section: SettingsSection) =
        SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to rowsOf(section).lastIndex))

    @Test
    fun theFocusedLastRowsGlowIsNotCutFlatBelowIt() {
        val problems = mutableListOf<String>()
        eachFocus(fixedSections.map { lastRowFocus(it) }) { at, name ->
            run {
                val section = at.section
                val option = rowsOf(section).last()
                val row = compose.onNodeWithTag(SettingsTags.option(option)).getUnclippedBoundsInRoot()
                val hint = compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).getBoundsInRoot()
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val x = px((row.left + row.right) / 2).toInt()
                val rowHeight = px(row.bottom - row.top)
                val slabBottom = px(row.bottom).toInt() + slabOverhang(rowHeight, density).toInt()
                val reach = slabBottom + px(SlabDimens.glowReach).toInt() - EDGE_PX
                if (reach >= px(hint.top).toInt()) problems += "${section.name} $name: the glow's reach ($reach px) is past the hint bar (${hint.top})"
                // The page's own colour, left of the sections column.
                val bg = pixels[2, slabBottom + EDGE_PX]
                // The shadow is there just under the slab: the list's clip keeps room for it.
                val near = stepBetween(pixels[x, slabBottom + EDGE_PX], bg)
                // No step down to the hint bar: the brightness changes by 5 levels at most between two pixel rows once past the slab's own edge, down to where the list's room ends: the Paper drop shadow itself falls 3 to 4 levels a pixel near the slab and fades over the last 12 dp; a clip edge cut 16.
                val last = minOf(px(hint.top).toInt() - 2, slabBottom + px(SlabDimens.listEndRoom).toInt() + EDGE_PX)
                var worst = 0f
                var at = 0
                for (y in slabBottom + SLAB_EDGE_PX until last) {
                    val step = stepBetween(pixels[x, y], pixels[x, y + 1])
                    if (step > worst) {
                        worst = step
                        at = y - slabBottom
                    }
                }
                if (worst > MAX_STEP) problems += "${section.name} $name: the shadow steps by ${(worst * 255).toInt()}/255 at ${at}px below the slab: a line"
                if (near < MIN_SHADOW) problems += "${section.name} $name: no shadow just below the last row (${near * 255}/255): the list's clip cuts it at the slab"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The screen with the focus on the first row of each section, to subtract: what stays beside the list's lower end is the screen's own content (the sections column, its divider). */
    private val firstRows = mutableMapOf<String, androidx.compose.ui.graphics.PixelMap>()

    private fun firstRowThenLast(
        each: (SettingsFocus, String, androidx.compose.ui.graphics.PixelMap, androidx.compose.ui.graphics.PixelMap) -> Unit,
    ) {
        firstRows.clear()
        val sections = fixedSections.filter { rowsOf(it).size > 2 }
        val focuses = sections.flatMap { s ->
            listOf(SettingsFocus(column = SettingsColumn.Options, section = s, rememberedOption = mapOf(s to 0)), lastRowFocus(s))
        }
        eachFocus(focuses) { at, name ->
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            if (at.rememberedOption[at.section] == 0) firstRows["${at.section.name} $name"] = pixels else each(at, name, firstRows["${at.section.name} $name"]!!, pixels)
        }
    }

    /**
     * The shadow beside the focused last row (its clip reaches [SlabDimens.blobReach] past the row on both sides) fades out at the clip's end too: against the same
     * screen with the focus on the first row, no step of more than [MAX_STEP] between two neighbour pixels of the difference, across x or across y, from the slab's
     * lower edge to where the list's room ends, left and right of the pill.
     */
    @Test
    fun theShadowBesideTheLastRowHasNoEdgeEither() {
        val problems = mutableListOf<String>()
        firstRowThenLast { at, name, before, pixels ->
            val row = compose.onNodeWithTag(SettingsTags.option(rowsOf(at.section).last())).getUnclippedBoundsInRoot()
            val hint = compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).getBoundsInRoot()
            val slabBottom = px(row.bottom).toInt() + slabOverhang(px(row.bottom - row.top), density).toInt()
            val last = minOf(px(hint.top).toInt() - 2, slabBottom + px(SlabDimens.listEndRoom).toInt() + EDGE_PX)
            val reach = px(SlabDimens.blobReach).toInt()
            val rowLeft = px(row.left).toInt()
            // The preview panel beside the list shows the focused row's own content: the right side stops where it begins.
            val panel = compose.onAllNodesWithTag(SettingsTags.PANEL)
            val panelLeft = if (panel.fetchSemanticsNodes().isEmpty()) Int.MAX_VALUE else px(panel.onFirst().getUnclippedBoundsInRoot().left).toInt()
            val rowRight = minOf(px(row.right).toInt(), pixels.width, panelLeft)
            // Below the slab's rim, from the clip's side edge to the pill's own side edge (and across it).
            val sides = listOf(maxOf(0, rowLeft - reach) to rowLeft + 2, rowRight - 2 to minOf(rowRight + reach, pixels.width, panelLeft) - 2)
            var worst = 0f
            var where = ""
            var scanned = 0
            fun diff(x: Int, y: Int, c: Int) = pixels[x, y].let { a -> before[x, y].let { b -> arrayOf(a.red - b.red, a.green - b.green, a.blue - b.blue)[c] } }
            for ((x0, x1) in sides) {
                for (y in slabBottom + SLAB_EDGE_PX until last) {
                    for (x in x0 until x1) {
                        scanned++
                        for (c in 0..2) {
                            val here = diff(x, y, c)
                            val across = abs(here - diff(x + 1, y, c))
                            val down = abs(here - diff(x, y + 1, c))
                            if (maxOf(across, down) > worst) {
                                worst = maxOf(across, down)
                                where = "x=${x}px y=${y}px (${if (across >= down) "across" else "down"})"
                            }
                        }
                    }
                }
            }
            if (scanned == 0) problems += "${at.section.name} $name: nothing beside the pill to scan"
            if (worst > MAX_STEP) problems += "${at.section.name} $name: the shadow beside the pill steps by ${(worst * 255).toInt()}/255 at $where: an edge"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The fade never paints over a neighbour drawn earlier: with the focus on the last row, the strip left of the pill's clip matches the same screen with the
     * focus on the first row to within 1/255 over the list's lower end, and the sections divider keeps its contrast with the page beside it (the shadow tints
     * both alike; a page-coloured band would wipe the divider out).
     */
    @Test
    fun theLastRowsFadeLeavesTheDividerAndTheColumnToTheLeftAlone() {
        val problems = mutableListOf<String>()
        firstRowThenLast { at, name, before, pixels ->
            val row = compose.onNodeWithTag(SettingsTags.option(rowsOf(at.section).last())).getUnclippedBoundsInRoot()
            val hint = compose.onNodeWithTag(SettingsTags.HINT_CONFIRM).getBoundsInRoot()
            val dividers = compose.onAllNodesWithTag(SettingsTags.SECTIONS_DIVIDER)
            val hasDivider = dividers.fetchSemanticsNodes().isNotEmpty()
            // Left of the clip: the blob's reach and the pill's side room.
            val strip = px(row.left - SlabDimens.blobReach).toInt() - SIDE_ROOM_PX
            val yEnd = minOf(px(hint.top).toInt() - px(SlabDimens.glowReach).toInt(), px(row.bottom + SlabDimens.listEndRoom).toInt())
            var worst = 0f
            var where = ""
            var kept = 0f
            var keptAt = ""
            // The divider's own columns, against the page 3 px to their left.
            val divider = if (hasDivider) dividers.onFirst().getUnclippedBoundsInRoot() else null
            val d0 = if (divider != null) px(divider.left).toInt() else 0
            for (y in px(row.top).toInt() until yEnd) {
                for (x in 0 until strip) {
                    val step = stepBetween(pixels[x, y], before[x, y])
                    if (step > worst) {
                        worst = step
                        where = "x=${x}px y=${y}px"
                    }
                }
                for (x in d0 until if (divider != null) px(divider.right).toInt() else 0) {
                    val loss = stepBetween(pixels[x, y] - pixels[d0 - 3, y], before[x, y] - before[d0 - 3, y])
                    if (loss > kept) {
                        kept = loss
                        keptAt = "x=${x}px y=${y}px"
                    }
                }
            }
            if (worst > 1f / 255f) problems += "${at.section.name} $name: the strip left of the list differs by ${(worst * 255).toInt()}/255 at $where"
            if (kept > MAX_TINT_DIFFERENCE) problems += "${at.section.name} $name: the divider's contrast changed by ${(kept * 255).toInt()}/255 at $keptAt"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aRowBelowTheVisibleWindowDrawsNothing() = drawsNothingBelowTheWindow(fixedSections.map { SettingsFocus(column = SettingsColumn.Sections, section = it) })

    /** The focus in the options column on every row but the last: a row that follows the focused one stays out of sight below the window. */
    @Test
    fun aFocusedRowWithRowsBelowItLeavesTheNextRowOutOfSight() = drawsNothingBelowTheWindow(
        fixedSections.flatMap { section ->
            (0 until rowsOf(section).lastIndex).map {
                SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to it))
            }
        },
    )

    private fun drawsNothingBelowTheWindow(focuses: List<SettingsFocus>) {
        val problems = mutableListOf<String>()
        eachFocus(focuses) { focusNow, name ->
            run {
                val section = focusNow.section
                val list = compose.onNodeWithTag(SettingsTags.OPTION_LIST).getUnclippedBoundsInRoot()
                val window = px(list.bottom - SlabDimens.listEndRoom)
                val rows = rowsOf(section).map { compose.onNodeWithTag(SettingsTags.option(it)).getUnclippedBoundsInRoot() }
                val shown = rows.filter { px(it.top) < window }
                val focusedRow = if (focusNow.column == SettingsColumn.Options) rows[focusNow.rememberedOption[section] ?: 0] else null
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                // The background beside the list: nothing else is drawn there.
                // Past the sections column's own glow, which spills over the list's left edge.
                val x0 = px(list.left + SlabDimens.glowReach * 2).toInt()
                // The narrow layouts keep the options wider than the screen (the panel slides in beside them).
                val x1 = minOf(px(list.right).toInt(), pixels.width) - 1
                val bg = pixels[x0, window.toInt() + 1]
                // The hint bar's keys sit below the list; stop short of its keys' own shadow.
                val hintTop = compose.onNodeWithTag(SettingsTags.HINT_BACK).getBoundsInRoot().top
                val yEnd = minOf(px(list.bottom).toInt() + px(SlabDimens.listEndRoom).toInt(), px(hintTop).toInt() - px(SlabDimens.glowReach).toInt())
                var bad = 0
                var at = ""
                for (y in window.toInt() + 1 until yEnd) {
                    // A row that begins in the window may draw its own lower part, cut or not (the sections have the focus: no slab hangs past a row).
                    // The list's last row, when shown, also draws its own shadow below it (nothing follows it).
                    if (shown.any { px(it.top) <= y && y <= px(it.bottom) + if (it == rows.last()) px(SlabDimens.listEndRoom) else if (it == focusedRow) slabHang(it) else 0f }) continue
                    var x = x0
                    while (x <= x1) {
                        if (stepBetween(pixels[x, y], bg) > BG_TOLERANCE) {
                            bad++
                            if (at.isEmpty()) at = "x=${x}px y=${y}px"
                        }
                        x += 2
                    }
                }
                if (bad > 0) problems += "${section.name} $name row ${focusNow.rememberedOption[section]}: $bad pixels drawn under the visible window (first at $at)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun slabHang(row: DpRect) = px(row.bottom - row.top).let { slabOverhang(it, density) }

    private operator fun Color.minus(o: Color) = Color(red - o.red + 0.5f, green - o.green + 0.5f, blue - o.blue + 0.5f)

    private fun stepBetween(a: Color, b: Color): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private companion object {
        const val MIN_SHADOW = 2f / 255f
        const val BG_TOLERANCE = 3f / 255f
        // The slab's rim changes faster than this, over the first few pixels.
        const val SLAB_EDGE_PX = 6
        const val MAX_STEP = 5.5f / 255f
        const val EDGE_PX = 3

        // The shadow tints the divider's translucent ink a shade differently from the page beside it.
        const val MAX_TINT_DIFFERENCE = 3f / 255f

        // The pill's clip also reaches this far past its side (GlidingPill's unclipped side).
        const val SIDE_ROOM_PX = 64

    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsListEnds832Test : SettingsListEndsBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsListEnds768Test : SettingsListEndsBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsListEnds576Test : SettingsListEndsBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SettingsListEnds640Test : SettingsListEndsBase()
