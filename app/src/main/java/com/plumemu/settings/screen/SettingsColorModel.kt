package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.withColors
import com.plumemu.sound.Outcome

/**
 * What the colour pages do with presses: Colors' panel (its tiles and the Background and Text color
 * rows under them) and the colour pages (presets, Color, Shade, Color code). [SettingsPanelModel] adds every other page.
 */
internal abstract class SettingsColorModel(
    protected val actions: SettingsActions,
    initialFocus: SettingsFocus,
) : SettingsCollectionsModel(initialFocus, SettingsWork(actions), actions.collections) {
    protected val custom = SettingsCustomColor(actions) { hexRow -> if (focus.page != null) moveTo(focus.copy(panelIndex = hexRow)) }

    /** Text color's page says no colour reads on this background: A has nothing to choose there. */
    val isTextBlocked: Boolean get() = focus.page == PanelPage.TextPresets && custom.textBlocked

    /** Which part of the colour page the cursor is on; null on any other page. */
    val colourPart: ColorPart? get() = custom.partOf(focus.page, focus.panelIndex)

    /** The strip stop in effect on the colour page, and the shades of it ([ColorShades]) for drawing. */
    val colorStop: Int get() = custom.stop

    fun colorShades(page: PanelPage): List<Rgb> = custom.shades(page)

    /** The panel indexes of the colour page's Color row, its shades and its Color code row. */
    fun colorIndex(page: PanelPage): Int = custom.colorIndex(page)

    fun shadeIndex(
        page: PanelPage,
        i: Int,
    ): Int = custom.shadeIndex(page, i)

    fun hexIndex(page: PanelPage): Int = custom.hexIndex(page)

    /** The place in the Shade row the strip's stops keep (the middle one until a shade was rested on). */
    fun shadePlaceOf(page: PanelPage): Int = custom.shadePlaceIn(page)

    fun isHexRow(
        page: PanelPage,
        index: Int,
    ): Boolean = custom.isHexRow(page, index)

    protected abstract fun onPanelBack(page: PanelPage)

    /** A on the hex row: typing starts, with the keyboard Settings › Controls › Keyboard names now. */
    protected fun startEditingHex() {
        custom.startEditingHex(focus.page ?: return, keyboardChoice)
    }

    /** Colors' panel: the tiles' grid, then Background, Text color and Accent color under it. Right opens a row, Left leaves for the Colors row. */
    protected fun moveInColorSets(
        direction: Direction,
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        val index = focus.panelIndex
        val row = colorsRowOption(index)
        // A held key opens nothing and never leaves: one more repeat on the Colors row would replace all three colours.
        if (row != null && direction == Direction.Right) return if (isHeldMove) Unit else openColorsRow(row, settings)
        val next = colorSetsItemAfter(index, colorSetsPanelEntries(settings).size, geometry.colorSetColumns, direction)
        if (next != index) return focusPanelItem(next)
        if (direction == Direction.Left && !isHeldMove) leaveColourPageToItsRow(PanelPage.ColorSets)
    }

    /**
     * A colour page: the presets (as a grid; Down from the last row reaches Color), Color (Left and
     * Right step the strip, Down reaches Shade), Shade (Left and Right among the shades) and the Color
     * code row. A held key never crosses between the presets and what is under them.
     */
    protected fun moveInColourPage(
        page: PanelPage,
        direction: Direction,
        geometry: SettingsGeometry,
    ) {
        val index = focus.panelIndex
        val colour = custom.colorIndex(page)
        val shades = custom.shades(page)
        when (custom.partOf(page, index) ?: return) {
            ColorPart.Preset -> {
                val next = PanelGrid(colour, geometry.presetColumns).move(index, direction)
                if (next != index) return focusPanelItem(next)
                if (direction == Direction.Left) return if (isHeldMove) Unit else leaveColourPageToItsRow(page)
                if (direction == Direction.Down && !isHeldMove && !custom.textBlocked) focusPanelItem(colour)
            }
            ColorPart.Color ->
                when (direction) {
                    Direction.Left, Direction.Right -> if (custom.stepStop(page, if (direction == Direction.Left) -1 else 1)) feel.report(Outcome.Moved)
                    Direction.Down -> focusPanelItem(if (shades.isEmpty()) custom.hexIndex(page) else custom.shadeIndex(page, custom.shadePlaceIn(page)))
                    Direction.Up -> if (!isHeldMove) focusPanelItem(custom.presetAbove(page))
                }
            ColorPart.Shade -> {
                val at = index - colour - 1
                when (direction) {
                    Direction.Left -> if (at > 0) focusPanelItem(index - 1)
                    Direction.Right -> if (at < shades.lastIndex) focusPanelItem(index + 1)
                    Direction.Up -> focusPanelItem(colour)
                    Direction.Down -> focusPanelItem(custom.hexIndex(page))
                }
            }
            ColorPart.Hex ->
                when (direction) {
                    Direction.Up -> focusPanelItem(if (shades.isEmpty()) colour else custom.shadeIndex(page, custom.shadePlaceIn(page)))
                    Direction.Left -> if (!isHeldMove) leaveColourPageToItsRow(page)
                    else -> Unit
                }
        }
    }

    /**
     * A tap on item [index] of [page] when it is not yet the cursor's: the cursor goes there; on the cursor's own item
     * [enter] runs (as A does). A colour page's Color code row starts typing, its strip takes taps through the touch
     * callbacks (the label only focuses), and a shade the page opened on without previewing keeps it on its second tap.
     */
    protected fun onPanelItemTapped(
        page: PanelPage,
        index: Int,
        enter: () -> Unit,
    ) {
        when (custom.partOf(page, index)) {
            ColorPart.Hex -> {
                // Like reaching it with the D-pad: the colour shown stays.
                focusPanelItem(index)
                // Already typing: a tap on the field goes back to it with what was typed.
                if (custom.isEditingHex) custom.askHexKeyboard() else startEditingHex()
            }
            ColorPart.Color -> if (focus.panelIndex != index) focusPanelItem(index)
            ColorPart.Shade ->
                if (focus.panelIndex != index) {
                    focusPanelItem(index)
                } else {
                    // The page opened on this shade without previewing it: a second tap keeps it (A before any move keeps the current colour).
                    if (!custom.cursorMoved) custom.focus(page, index)
                    enter()
                }
            else -> if (focus.panelIndex == index) enter() else focusPanelItem(index)
        }
    }

    /** Left from the leftmost preset or the Color code row goes back as B does, to the row that opened the page. */
    protected fun leaveColourPageToItsRow(page: PanelPage) = onPanelBack(page)

    /** A colour page is done: it goes back to its row in Colors' panel. */
    protected fun closeColourPage(page: PanelPage) {
        val item = ColorPages.kindOf(page)?.let(::colorsRowItemOf) ?: return closePanel()
        moveTo(focus.copy(page = PanelPage.ColorSets, panelIndex = item))
    }

    /**
     * Focus panel item [index]. On a colour page its colour applies at once (a preset, a shade) or the
     * strip stays where it is (Color, the Color code row).
     */
    protected fun focusPanelItem(index: Int) {
        val page = focus.page ?: return
        moveTo(focus.copy(panelIndex = index))
        custom.focus(page, index)
    }

    /** Opens colour page [page] with the cursor on [index]; applies nothing, so A before any move keeps the current colour. */
    protected fun openColourPage(
        page: PanelPage,
        index: Int,
        settings: PlumSettings,
    ) {
        custom.open(page, index, settings)
        moveTo(focus.copy(column = SettingsColumn.Panel, page = page, panelIndex = index))
    }

    private fun openColorsRow(
        option: SettingsOption,
        settings: PlumSettings,
    ) {
        val (page, index) = ColorPages.openingFor(option, custom.withWritten(settings))
        // A tap on the focused row opens it like A does, with the confirm sound.
        cause = Cause.Enter
        openColourPage(page, index, settings)
    }

    /** A on Colors' panel: a row opens its page, a tile uses its set. */
    protected fun onColorSetsEnter(
        index: Int,
        settings: PlumSettings,
    ) {
        val row = colorsRowOption(index)
        if (row != null) openColorsRow(row, settings) else useColorSet(index, settings)
    }

    /** A on a colour page: a preset or a shade or the strip keeps the colour shown and goes back to Colors' row; the Color code row types. */
    protected fun onColourPageEnter(
        page: PanelPage,
        index: Int,
    ) {
        when (custom.partOf(page, index)) {
            null -> Unit
            ColorPart.Hex -> {
                startEditingHex()
                feel.report(Outcome.Activated)
            }
            ColorPart.Preset ->
                if (custom.textBlocked) {
                    // Nothing reads on this background: the panel says so and offers nothing to pick.
                    feel.report(Outcome.Nothing)
                } else if (custom.lastWrite?.refusal != null) {
                    // The colour under the cursor does not read: A stays here, with the reason shown.
                    feel.report(Outcome.Nothing)
                } else {
                    closeColourPage(page)
                }
            ColorPart.Color, ColorPart.Shade -> {
                custom.choose(page, null)
                closeColourPage(page)
            }
        }
    }

    /**
     * Colors' tile [index]: that set (or Custom), through the row's own write ([withColors], so the
     * person's own colours are kept exactly as the row keeps them), then back to the row. The entry
     * is taken from what this panel shows; the write reads the store, so a quick second press
     * picking what is already current changes nothing.
     */
    private fun useColorSet(
        index: Int,
        settings: PlumSettings,
    ) {
        val entries = colorSetsPanelEntries(settings)
        if (index !in entries.indices) return closePanel()
        val entry = entries[index]
        custom.forgetWritten()
        actions.update { stored -> stored.withAppearance { it.withColors(entry) } }
        closePanel()
    }
}
