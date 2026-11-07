package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.withColors
import com.plumemu.sound.Outcome

/**
 * What the colour pages do with presses: Colors' panel (its tiles and the Background and Text color
 * rows under them), the presets pages and the "+" pages. [SettingsPanelModel] adds every other page.
 */
internal abstract class SettingsColorModel(
    protected val actions: SettingsActions,
    initialFocus: SettingsFocus,
) : SettingsFolderModel(initialFocus, SettingsWork(actions)) {
    protected val custom = SettingsCustomColor(actions)

    /** The "+" page's check: the colour it opened with, then the one chosen. */
    val selectedColour: Rgb? get() = custom.selectedColour

    /** The swatches the "+" page draws and the cursor walks, fixed when it opened. */
    fun customPalette(page: PanelPage): List<Rgb> = custom.palette(page)

    /** Text color's page says no colour reads on this background: A has nothing to choose there. */
    val isTextBlocked: Boolean get() = focus.page == PanelPage.TextPresets && custom.textBlocked

    fun isHexRow(
        page: PanelPage,
        index: Int,
    ): Boolean = custom.isHexRow(page, index)

    protected abstract fun onPanelBack(page: PanelPage)

    /** A on the hex row: typing starts, with the keyboard Settings › Controls › Keyboard names now. */
    protected fun startEditingHex() {
        custom.startEditingHex(focus.page ?: return, keyboardChoice)
    }

    /** Colors' panel: the tiles' grid, then Background and Text color under it. Right opens a row, Left leaves for the Colors row. */
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

    /** The "+" page: the swatch grid, then the sliders (Left and Right step the focused one), then the hex row. */
    protected fun moveInCustomPage(
        page: PanelPage,
        direction: Direction,
    ) {
        val index = focus.panelIndex
        val slider = custom.sliderAt(page, index)
        if (slider != null) {
            // A slider keeps Left and Right: at its start Left does nothing, it never leaves the page.
            val steps = if (direction == Direction.Left) -1 else if (direction == Direction.Right) 1 else 0
            if (steps != 0) {
                if (custom.step(page, slider, steps)) feel.report(Outcome.Moved)
                return
            }
        }
        val next = custom.nextIndex(page, index, direction)
        if (next != index) return focusPanelItem(next)
        // Left leaves from the grid's leftmost tiles and from the hex row only.
        val leftmost = index < custom.palette(page).size && index % ColorPages.PALETTE_COLUMNS == 0
        if (direction == Direction.Left && (leftmost || custom.isHexRow(page, index))) leaveColourPageToItsRow(page)
    }

    /**
     * Left from the leftmost item of a colour page goes back as B does, to the row that opened it (a
     * custom page first returns to its presets page): the colour the page opened with is put back
     * unless one was chosen.
     */
    protected fun leaveColourPageToItsRow(page: PanelPage) {
        onPanelBack(page)
        val now = focus.page
        if (now != null && now != page && now != PanelPage.ColorSets && focus.column == SettingsColumn.Panel) onPanelBack(now)
    }

    /** A colour page is done: Background's and Text color's go back to their row in Colors' panel, Accent color's to its row in the list. */
    protected fun closeColourPage(page: PanelPage) {
        val item = ColorPages.kindOf(page)?.let(::colorsRowItemOf)
        if (item == null) closePanel() else moveTo(focus.copy(page = PanelPage.ColorSets, panelIndex = item))
    }

    /**
     * Focus panel item [index]. On a colour page its colour applies at once (the ring marks the
     * current one); on a custom page that is a preview, and the hex row puts back the colour the
     * page opened with.
     */
    protected fun focusPanelItem(index: Int) {
        val page = focus.page ?: return
        moveTo(focus.copy(panelIndex = index))
        custom.focus(page, index)
    }

    /** Opens a custom page with the cursor on the swatch nearest the current colour; applies nothing, so A before any move keeps the current colour. */
    protected fun enterCustomPage(
        page: PanelPage,
        settings: PlumSettings,
    ) {
        val at = custom.open(page, settings)
        moveTo(focus.copy(column = SettingsColumn.Panel, page = page, panelIndex = at))
    }

    /** Opens colour page [page] at [index]. A presets page remembers what it opened with, so browsing settles from it. */
    protected fun openColourPage(
        page: PanelPage,
        index: Int,
        settings: PlumSettings,
    ) {
        if (SettingsCustomColor.isCustomPage(page)) return enterCustomPage(page, settings)
        custom.open(page, settings)
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

    protected fun onPresetsEnter(
        page: PanelPage,
        index: Int,
        settings: PlumSettings,
    ) {
        if (page == PanelPage.TextPresets && custom.textBlocked) {
            // Nothing reads on this background: the panel says so and offers nothing to pick.
            feel.report(Outcome.Nothing)
        } else if (index == custom.presetCount(page) - 1) {
            enterCustomPage(ColorPages.customOf(ColorPages.kindOf(page) ?: return), settings)
        } else if (custom.lastWrite?.refusal != null) {
            // The colour under the cursor does not read: A stays here, with the reason shown.
            feel.report(Outcome.Nothing)
        } else {
            closeColourPage(page)
        }
    }

    protected fun onCustomPageEnter(
        page: PanelPage,
        index: Int,
    ) {
        if (isHexRow(page, index)) {
            startEditingHex()
            feel.report(Outcome.Activated)
        } else {
            // A chooses the swatch: nothing to put back.
            custom.choose(page, null)
            closeColourPage(page)
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
