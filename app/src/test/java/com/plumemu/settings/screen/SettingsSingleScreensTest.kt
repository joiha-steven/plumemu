package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import com.plumemu.update.CheckOutcome
import com.plumemu.update.LastCheck
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateView
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
 * The faults a review of the reference shots found on single Settings screens, each pinned by numbers at Large and Largest:
 * what must stay readable, reachable and even when the text is large.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSingleScreensTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val stored = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private var density = Density(1f)

    private class Scene(val focus: SettingsFocus, val content: SettingsContent, val size: TextSize, val settings: PlumSettings)

    private var scene by mutableStateOf<Scene?>(null)
    private var bodyLine = 0f

    private fun open(
        focus: SettingsFocus,
        content: SettingsContent,
        size: TextSize,
        settings: PlumSettings = stored,
    ) {
        val isFirst = scene == null
        scene = Scene(focus, content, size, settings)
        if (isFirst) {
            compose.setContent {
                val now = scene ?: return@setContent
                PlumTheme(now.settings.toThemeSettings().copy(textSize = now.size)) {
                    key(now) {
                        geometry = settingsGeometry(PlumTheme.layout)
                        density = LocalDensity.current
                        bodyLine = with(density) { PlumTheme.type.panelBody.lineHeight.toPx() }
                        model = remember { SettingsModel(NoActions, now.focus).also { it.content = now.content } }
                        SettingsLayout(now.settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(
        action: Action,
        settings: PlumSettings = stored,
    ) {
        compose.runOnIdle { model.onAction(action, settings, geometry) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun textNodes(text: String, substring: Boolean = false): List<SemanticsNode> =
        compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes()

    private fun SemanticsNode.layout(): TextLayoutResult = mutableListOf<TextLayoutResult>().also { config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.first()

    private fun px(dp: Float) = dp * density.density

    private val release =
        UpdateManifest(
            41, "0.0.3-alpha", "https://plumemu-app.stevensdesk.com/update/p.apk", 9_800_000, "ab".repeat(32), 23, "2026-10-23",
            List(6) { "Folders are checked again when you come back to Plumemu." }, null,
        )

    private val updatesFocus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.About, rememberedOption = mapOf(SettingsSection.About to 1), page = PanelPage.Update, panelIndex = 0)

    private fun updates() = SettingsContent(update = UpdateView(LastCheck(1, CheckOutcome.Found), release))

    private fun updateKeepsNotesAndCheckNow() {
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            open(updatesFocus, updates(), size)
            val title = textNodes("→", substring = true).first { it.layout().layoutInput.text.text.contains("0.0.3-alpha") }.layout()
            assertTrue("$size: the title loses the new version: ${title.layoutInput.text.text}", !title.didOverflowHeight && title.lineCount <= 2 && !title.isLineEllipsized(title.lineCount - 1))
            val notes = bounds(SettingsTags.UPDATE_NOTES)
            val line = bodyLine
            assertTrue("$size: notes ${notes.height} px high, two lines need ${2 * line}", notes.height >= 2 * line)
            press(Action.Move(Direction.Down))
            repeat(NOTE_PRESSES) { if (model.focus.panelIndex == 0) press(Action.Move(Direction.Down)) }
            assertEquals("$size: Down reaches Check now", 1, model.focus.panelIndex)
            val button = bounds(SettingsTags.panelItem(1))
            val panel = bounds(SettingsTags.UPDATE_PANEL)
            assertTrue("$size: Check now $button is not whole in $panel", button.top >= panel.top && button.bottom <= panel.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun updateNotesAndCheckNowAt576() = updateKeepsNotesAndCheckNow()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun updateNotesAndCheckNowAt768() = updateKeepsNotesAndCheckNow()

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun updateNotesAndCheckNowAt832() = updateKeepsNotesAndCheckNow()

    private fun rowFocus(
        option: SettingsOption,
        content: SettingsContent,
    ): SettingsFocus {
        val section = SettingsSection.entries.first { option in it.rowsIn(content) }
        return SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to section.rowsIn(content).indexOf(option)))
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun clearRecentsFocusedButtonIsWholeAt768Largest() {
        val content = SettingsContent()
        open(rowFocus(SettingsOption.ClearRecent, content).copy(column = SettingsColumn.Panel, page = PanelPage.Confirm, panelIndex = SettingsModel.CONFIRM_CANCEL), content, TextSize.Largest)
        val panel = bounds(SettingsTags.PANEL)
        for (index in listOf(SettingsModel.CONFIRM_RESET, SettingsModel.CONFIRM_CANCEL)) {
            press(Action.Move(if (index == SettingsModel.CONFIRM_RESET) Direction.Down else Direction.Up))
            if (model.focus.panelIndex != index) press(Action.Move(if (index == SettingsModel.CONFIRM_RESET) Direction.Right else Direction.Left))
            assertEquals(index, model.focus.panelIndex)
            val button = bounds(SettingsTags.panelItem(index))
            val ring = px(PlumDimens.focusRingOutset.value)
            assertTrue("button $index $button is not whole with its ring in $panel", button.top - ring >= panel.top && button.bottom + ring <= panel.bottom)
        }
    }

    private val systems = listOf("Super Nintendo", "Game Boy Advance", "PlayStation", "PSP", "Mega Drive").map { LibrarySystem(it, it, 10) }

    private fun systemsContent() = SettingsContent(librarySystems = systems)

    private fun systemsOpen(size: TextSize) {
        open(rowFocus(SettingsOption.Systems, systemsContent()), systemsContent(), size)
        press(Action.Enter)
        assertEquals(PanelPage.Systems, model.focus.page)
    }

    private fun systemOrderIsFoundOnOneLine() {
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            systemsOpen(size)
            val panel = bounds(SettingsTags.PANEL)
            val label = textNodes("System order").first()
            assertEquals("$size: System order on one line", 1, label.layout().lineCount)
            val list = bounds(SettingsTags.SYSTEMS_LIST)
            assertEquals("$size: System order stands whole", label.size.height.toFloat(), label.boundsInRoot.height, 1f)
            val fades = compose.onAllNodesWithTag(SettingsTags.SYSTEMS_LIST + FADE_TOP_SUFFIX, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$size: System order ${label.boundsInRoot} is not whole in $list on opening, no fade over it", label.boundsInRoot.top >= list.top - 1f && label.boundsInRoot.bottom <= list.bottom && fades.isEmpty())
            val first = bounds(SettingsTags.panelItem(0))
            assertTrue("$size: the first system lies below System order", first.top >= label.boundsInRoot.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun systemOrderOnOpeningAt576() = systemOrderIsFoundOnOneLine()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun systemOrderOnOpeningAt768() = systemOrderIsFoundOnOneLine()

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun systemOrderOnOpeningAt832() = systemOrderIsFoundOnOneLine()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun systemsTextKeepsEmulatorsGapToTheList() {
        systemsOpen(TextSize.Large)
        val body = textNodes("How the systems", substring = true).first().boundsInRoot
        val systemsGap = bounds(SettingsTags.SYSTEMS_LIST).top - body.bottom
        val psx = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = emptyList())
        open(SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0), page = PanelPage.Emulators, systemId = "psx"), SettingsContent(systems = listOf(psx)), TextSize.Large)
        val emulatorsBody = textNodes("Used when", substring = true).first().boundsInRoot
        assertEquals("same gap from the text to the list", bounds(SettingsTags.PANEL_LIST).top - emulatorsBody.bottom, systemsGap, 1f)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun resetLastItemLeavesNoBandUnderTheTitle() {
        open(rowFocus(SettingsOption.Reset, SettingsContent()), SettingsContent(), TextSize.Large)
        press(Action.Enter)
        press(Action.Move(Direction.Down))
        val title = textNodes("Reset").filter { it.boundsInRoot.left >= bounds(SettingsTags.PANEL).left }.first().boundsInRoot
        val first = bounds(SettingsTags.panelItem(0))
        assertEquals("the list starts the panel's gap under the title", px(PlumDimens.settingsPanelListGap.value), first.top - title.bottom, 2f)
    }

    private fun colorsOpenOnWholeTiles() {
        val own = PlumSettings(appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite")), flags = SetupFlags(setupCompleted = true))
        val last = colorSetsPanelEntries(own).lastIndex
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            open(panelAt(SettingsOption.Colors, PanelPage.ColorSets, last), SettingsContent(), size, own)
            val panel = bounds(SettingsTags.PANEL)
            val peek = px(EdgePeek.value) + 1f
            for (index in 0..last) {
                val tile = compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
                val top = tile.top.value * density.density
                val bottom = tile.bottom.value * density.density
                assertTrue("$size: tile $index ($top..$bottom) shows more than the peek under the top of $panel", top >= panel.top || bottom - panel.top <= peek)
            }
            val body = textNodes("Pick a set", substring = true).first()
            val bodyTop = body.positionInRoot.y
            val bodyBottom = bodyTop + body.size.height
            assertTrue("$size: the text $bodyTop..$bodyBottom is cut under $panel", bodyTop >= panel.top || bodyBottom - panel.top <= peek)
        }
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun colorsOpenOnWholeTilesAt576() = colorsOpenOnWholeTiles()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun colorsOpenOnWholeTilesAt768() = colorsOpenOnWholeTiles()

    private fun longEmulatorSystem(): SystemEmulators {
        val swan = EmulatorEntry("retroarch-swanstation", "SwanStation", isLegalRisk = true)
        return SystemEmulators("psx", "PlayStation", automatic = EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false), chosen = null, installed = listOf(EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false), swan))
    }

    private fun emulatorsFocus(index: Int) =
        SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0), page = PanelPage.Emulators, panelIndex = index, systemId = "psx")

    private fun emulatorsFooterKeepsTheListsEndRoom() {
        var shown = 0
        for (size in listOf(TextSize.Small, TextSize.Medium, TextSize.Large, TextSize.Largest)) {
            open(emulatorsFocus(2), SettingsContent(systems = listOf(longEmulatorSystem()), emulatorAppCount = 9), size)
            // Measured but left out when the list needs the room: a footer not placed is not on screen.
            val footer = textNodes("9 emulators", substring = true).firstOrNull { it.layoutInfo.isPlaced }?.boundsInRoot ?: continue
            val card = compose.onNodeWithTag(SettingsTags.panelItem(2), useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value * density.density
            assertTrue("$size: the footer is ${footer.top - card} px under the card", footer.top - card >= px(SlabDimens.listEndRoom.value) - 1f)
            shown++
        }
        assertTrue("the footer shows at some size", shown > 0)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun emulatorsFooterRoomAt832() = emulatorsFooterKeepsTheListsEndRoom()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun emulatorsFooterRoomAt576() = emulatorsFooterKeepsTheListsEndRoom()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun focusedCardKeepsARowsPaddingRoundManyLines() {
        val psx = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = emptyList())
        open(emulatorsFocus(0), SettingsContent(systems = listOf(psx)), TextSize.Largest)
        val card = bounds(SettingsTags.panelItem(0))
        val text = textNodes("Ask on the first game", substring = true).firstOrNull { it.boundsInRoot.left >= card.left && it.boundsInRoot.right <= card.right + 1f }
            ?: textNodes("None installed", substring = true).first { it.boundsInRoot.left >= card.left }
        val pad = px(PlumDimens.settingsFolderCardPadding.value) - 1f
        assertTrue("text ${text.boundsInRoot} touches the card $card", text.boundsInRoot.top - card.top >= pad && card.bottom - text.boundsInRoot.bottom >= pad)
    }

    private val signedIn = SettingsContent(account = AccountStatus.SignedIn("plumtester"), raAccount = RaAccountStatus.SignedIn("plumtester", 1234))

    private fun brandsStayOnOneLine() {
        val focus = rowFocus(SettingsOption.RetroAchievementsAccount, signedIn)
        for (size in listOf(TextSize.Large, TextSize.Largest)) {
            open(focus, signedIn, size)
            for (name in listOf("ScreenScraper", "RetroAchievements")) {
                for (node in textNodes(name)) {
                    val layout = node.layout()
                    assertEquals("$size: $name breaks", 1, layout.lineCount)
                    assertTrue("$size: $name is cut: ${node.boundsInRoot} for ${layout.size}", node.boundsInRoot.width >= layout.size.width - 1f)
                }
            }
            val row = bounds(SettingsTags.option(SettingsOption.ScraperAccount))
            val value = textNodes("Signed in as plumtester", substring = true).first { it.boundsInRoot.top >= row.top - 1f }
            assertEquals("$size: the value is one line, under the label or beside it", 1, value.layout().lineCount)
        }
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun brandsOnOneLineAt576() = brandsStayOnOneLine()

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun brandsOnOneLineAt768() = brandsStayOnOneLine()

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun brandsOnOneLineAt832() = brandsStayOnOneLine()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noSliverOfAValuePastThePreviewEdge() {
        open(SettingsFocus(section = SettingsSection.About), updates(), TextSize.Largest)
        val row = bounds(SettingsTags.option(SettingsOption.Updates))
        val map = compose.onRoot().captureToImage().toPixelMap()
        val background = map[map.width - 1, 0]
        val marks = (map.width - px(9f).toInt() until map.width).sumOf { x -> (row.top.toInt() until row.bottom.toInt()).count { y -> map[x, y] != background } }
        assertEquals("pixels of the value past the screen's edge", 0, marks)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun aSwatchOfTheAccentsColourShowsItsFocus() {
        val blue = Color(0xFF3E7BE0)
        assertTrue(nearAccent(blue, blue))
        assertTrue(!nearAccent(Color(0xFFD01010), blue))
        var focused by mutableStateOf(false)
        var ink = Color.Unspecified
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                density = LocalDensity.current
                val colors = PlumTheme.colors
                ink = colors.onAccent
                Box(Modifier.size(SWATCH_BOX).background(colors.bg)) {
                    Swatch(colors.accent, PlumDimens.settingsPaletteCorner, focused, Modifier.align(Alignment.Center).testTag("swatch"), size = Modifier.size(SWATCH_SIZE), bleed = false)
                }
            }
        }
        focused = true
        compose.waitForIdle()
        val lit = compose.onNodeWithTag("swatch").captureToImage().toPixelMap()
        val h = lit.height / 2
        val inked = (0 until lit.width).count { x -> lit[x, h].let { abs(it.red - ink.red) + abs(it.green - ink.green) + abs(it.blue - ink.blue) } < INK_CLOSE }
        assertTrue("a line in the accent's ink marks the focused swatch: $inked px of ${lit.width}", inked >= 2)
    }

    /** The row said "1 folder to sort" while the page said every file was in. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun filesNotAddedNeverSaysAllIsInWhileAFolderWaits() {
        val folders = listOf(SettingsFolderRow(FolderKey(1, "Misc ROMs"), 0, FolderAnswer.Unanswered))
        open(rowFocus(SettingsOption.FilesNotAdded, SettingsContent()), SettingsContent(unknownFolders = folders, notAdded = notAddedItems(emptyList(), folders)), TextSize.Large)
        assertTrue("the page says every file is in", textNodes("Every file is in the library").isEmpty())
        assertTrue("the row says it waits", textNodes("1 folder to sort").isNotEmpty())
    }

    private companion object {
        const val NOTE_PRESSES = 40
        const val INK_CLOSE = 0.2f
        val SWATCH_BOX = 120.dp
        val SWATCH_SIZE = 64.dp
    }
}
