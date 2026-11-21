package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.settings.ColorRefusal
import com.plumemu.settings.ColorWrite
import com.plumemu.settings.toRgb
import java.util.concurrent.ConcurrentHashMap

/** The parts of a colour page, top to bottom: the presets, the Color strip, the Shade keys and the Color code row. */
internal enum class ColorPart { Preset, Color, Shade, Hex }

/**
 * The colour pages of [SettingsModel] (presets, then Color, Shade and the Color code row on one
 * page): the colour the cursor came down from the presets with (put back when the page is left from
 * below them without choosing), the colours written so far, the strip stop and shade in use, and the
 * hex code field. Presets, strip stops and shades preview as the focus moves; A or a tap chooses.
 */
@Stable
internal class SettingsCustomColor(
    private val actions: SettingsActions,
    /** A typed code moved the strip, and the shade count with it: the Color code row's new index, where the cursor stays. */
    private val onHexRowMoved: (Int) -> Unit = {},
) : HexFieldState() {
    /** False from opening a custom page until the cursor moves: the ring then sits on a swatch that was never previewed. */
    var cursorMoved: Boolean = false
        private set

    /** What a finger on the strip has brought the page to: shown now, written on lift. */
    private class Dragged(
        val page: PanelPage,
        val color: ColorSetting,
        /** The drag went into a colour that is refused: [color] is the opening one, put back on release. */
        val restoring: Boolean = false,
    )

    private var dragged: Dragged? = null

    /** A finger is down on the strip (a drag shows its colour through [ColorPreview]); set by [showDragged], cleared by [releaseDrag]. */
    private var fingerDown = false

    /** The settings last given: what a preview is laid over. */
    private var seen: PlumSettings = PlumSettings()

    /** The colour a custom page opened with, put back when it is left without choosing; null once chosen. */
    private var origin: ColorSetting? = null

    /** The text colour in place when a background page opened, put back with [origin]. */
    private var originText: TextColorSetting? = null

    /** The text the panel opened with: previews settle from it, so browsing backgrounds never drifts a custom text colour. */
    private var baseText: TextColorSetting = TextColorSetting.Light

    /** Likewise the accent the panel opened with: a preview that moves it to read is settled from this, and cancelling puts it back. */
    internal var baseAccent: ColorSetting = AppearanceSettings.DEFAULT_ACCENT
        private set

    /** The background the Text and Accent palettes read on, taken when the panel opened. */
    private var paletteBackground: Rgb = Rgb(0x000000)

    /** The appearance when the panel opened: an accent preview is checked against its background and text without waiting for the store. */
    private var openAppearance: AppearanceSettings = AppearanceSettings()

    /** What the last colour write did (adjusted the text or accent, or refused the pick), for the panel to tell the person; null before the first. */
    var lastWrite: ColorWrite? by mutableStateOf(null)
        private set

    /**
     * The colours of the last write, by setting (a text colour as the `Custom` of its colour): the store may not have answered yet.
     * Every write records all three as it ended, so a background that moved the accent is not undone by an accent written before it.
     * Cleared by Reset all and when a set is picked.
     */
    private val lastWritten = ConcurrentHashMap<ColorKind, ColorSetting>()

    /** Counts the times [lastWritten] was cleared: a write still waiting for the store then records nothing. */
    @Volatile private var generation = 0

    /** The background is [settings]'s: the Text and Accent palettes read on it. */
    fun followBackground(settings: PlumSettings) {
        seen = settings
        paletteBackground = ColorPages.backgroundOf(withWritten(settings))
    }

    /** Items of a presets page now, on the background the panel opened on. */
    fun presetCount(page: PanelPage): Int = ColorPages.presetCount(page, paletteBackground)

    /** The Text panel opened on a background no text colour reads on ([ColorPages.textBlockedOn]). */
    val textBlocked: Boolean get() = ColorPages.textBlockedOn(paletteBackground)

    /** The colour page last opened (null before one): a model made on a page, in a test or a shot, opens it when it first sees the settings. */
    var openedPage: PanelPage? = null
        private set

    /** The strip stop in effect (an index of [ColorShades]' strip: grey first for Background and Text). */
    var stop: Int by mutableStateOf(0)
        private set

    /** Which shade of the row the cursor last rested on; null until one did (the middle one is meant then). */
    private var shadePlace: Int? by mutableStateOf(null)

    /** The preset the cursor goes back up to from Color. */
    private var lastPreset = 0

    /** The part the cursor was on last (null before a page opens): reaching Color from the presets takes what is in effect. */
    private var lastPart: ColorPart? = null

    private fun roleOf(page: PanelPage?): ShadeRole? = ColorPages.kindOf(page ?: return null)?.let(ColorPages::roleOf)

    /** The shades of the strip stop now, lightest first, each passing the readability check on the background the panel opened on. */
    fun shades(page: PanelPage): List<Rgb> = roleOf(page)?.let { ColorPages.shades(it, stop, paletteBackground) } ?: emptyList()

    /** The shade the cursor leaves for from Color and comes back to from the Color code row: the last one it rested on, else the middle one. */
    fun shadePlaceIn(page: PanelPage): Int = (shadePlace ?: (shades(page).size / 2)).coerceIn(0, (shades(page).size - 1).coerceAtLeast(0))

    /** The Color row's index: right after the presets. */
    fun colorIndex(page: PanelPage): Int = presetCount(page)

    /** The index of shade [i]. */
    fun shadeIndex(
        page: PanelPage,
        i: Int,
    ): Int = presetCount(page) + 1 + i

    /** The Color code row's index: after the last shade. */
    fun hexIndex(page: PanelPage): Int = presetCount(page) + 1 + shades(page).size

    /** The preset the cursor goes up to from Color. */
    fun presetAbove(page: PanelPage): Int = lastPreset.coerceIn(0, presetCount(page) - 1)

    /** Which part of colour [page] item [index] is (a Text page no colour reads on is all presets); null for other pages. */
    fun partOf(
        page: PanelPage?,
        index: Int,
    ): ColorPart? {
        if (page == null || ColorPages.kindOf(page) == null) return null
        val presets = presetCount(page)
        return when {
            index < presets || textBlocked -> ColorPart.Preset
            index == presets -> ColorPart.Color
            index == hexIndex(page) -> ColorPart.Hex
            else -> ColorPart.Shade
        }
    }

    /** [index] of [page] is its hex row. */
    fun isHexRow(
        page: PanelPage,
        index: Int,
    ): Boolean = partOf(page, index) == ColorPart.Hex

    /**
     * Left (-1) or Right (+1) on the Color strip: the stop moves and the shade of the same place in the
     * row on the new hue previews. False at either end: nothing changes.
     */
    fun stepStop(
        page: PanelPage,
        direction: Int,
    ): Boolean = setStop(page, stop + direction)

    /**
     * Strip stop [at] (a tap, a drag): the shade of the same place in the row previews. A [drag] shows it
     * through [ColorPreview] and stores it once on [releaseDrag]. False when the stop did not change.
     */
    fun setStop(
        page: PanelPage,
        at: Int,
        drag: Boolean = false,
    ): Boolean {
        val role = roleOf(page) ?: return false
        val next = at.coerceIn(0, ColorShades.stopCount(role) - 1)
        if (next == stop) return false
        stop = next
        cursorMoved = true
        val list = shades(page)
        if (list.isEmpty()) return true
        val colour = ColorSetting.Custom(list[shadePlaceIn(page)].value)
        if (drag) showDragged(page, colour) else write(page, colour)
        return true
    }

    /** The finger of a drag lifted or was cancelled (or the screen went with it down): the colour it showed is written, once, through the normal path. */
    fun releaseDrag() {
        val d = dragged
        dragged = null
        fingerDown = false
        if (d != null) {
            if (d.restoring) putBack(d.page, keep = lastWrite?.takeIf { it.refusal != null }) else write(d.page, d.color)
        }
        actions.colorPreview.release()
    }

    /** The screen is going: what [leave] puts back is not written twice; a chosen colour is written once. */
    fun leaveAndRelease(
        page: PanelPage,
        onPresets: Boolean,
    ) {
        if (origin != null && !onPresets) dragged = null
        releaseDrag()
        leave(page, onPresets)
    }

    /**
     * Remembers the current colour of [page] and what the panel opened with, with the cursor at [index]:
     * on a preset the cursor is on it; on an own colour the strip is at its nearest stop and the cursor
     * on the nearest shade. Nothing is written, so A before any move keeps the current colour exactly.
     */
    fun open(
        page: PanelPage,
        index: Int,
        settings: PlumSettings,
    ) {
        if (dragged != null) releaseDrag() // an unwritten drag is written first
        val kind = ColorPages.kindOf(page) ?: return
        openedPage = page
        // What was last written, if anything: the store (and so [settings]) may not have it yet.
        val written = withWritten(settings)
        seen = settings
        paletteBackground = ColorPages.backgroundOf(written)
        origin = ColorPages.settingOf(page, written)
        val nearest = ColorShades.nearestStopAndShade(ColorPages.roleOf(kind), ColorPages.drawnColor(kind, written), paletteBackground)
        stop = nearest.first
        lastPart = partOf(page, index)
        shadePlace = if (lastPart == ColorPart.Shade) index - colorIndex(page) - 1 else null
        lastPreset = if (lastPart == ColorPart.Preset) index else presetCount(page) - 1
        dragged = null
        cursorMoved = false
        openAppearance = written.appearance
        // The text as stored, not as drawn: leaving without a pick must not rewrite a stored text the background draws adjusted.
        baseText = written.appearance.textColor
        baseAccent = written.appearance.accent
        originText = baseText
        lastWrite = null
        hexRefusal = null
    }

    /**
     * The focus reached [index] of [page]: a preset, a strip stop or a shade is previewed; the Color
     * code row keeps the colour shown. Coming down from the presets, the colour in effect is what B puts back.
     */
    fun focus(
        page: PanelPage,
        index: Int,
    ) {
        cursorMoved = true
        val before = lastPart
        val part = partOf(page, index) ?: return
        lastPart = part
        if (part == ColorPart.Preset) {
            lastPreset = index
            ColorPages.colorAt(page, index, paletteBackground, baseAccent)?.let { write(page, it) }
            return
        }
        if (before == ColorPart.Preset) enterOwnColour(page)
        if (part != ColorPart.Shade) return
        shadePlace = index - colorIndex(page) - 1
        shades(page).getOrNull(shadePlace ?: return)?.let { write(page, ColorSetting.Custom(it.value)) }
    }

    /** The cursor came down from the presets: the colour in effect now is what B puts back, and the strip goes to its nearest stop. */
    private fun enterOwnColour(page: PanelPage) {
        val kind = ColorPages.kindOf(page) ?: return
        val now = withWritten(seen)
        val setting = ColorPages.settingOf(page, now)
        origin = setting
        if (kind == ColorKind.Background) originText = if (setting == openAppearance.background) baseText else ColorPages.textFor(setting, baseText)
        stop = ColorShades.nearestStopAndShade(ColorPages.roleOf(kind), ColorPages.drawnColor(kind, now), paletteBackground).first
        shadePlace = null
    }

    /** A or a tap chose [color] (null: the one shown now); leaving the page keeps it. */
    fun choose(
        page: PanelPage,
        color: ColorSetting?,
    ) {
        if (color != null && refuses(page, color)) return // nothing stored; leaving puts the opening colour back
        origin = color
        if (color != null && ColorPages.isBackground(page)) originText = ColorPages.textFor(color, baseText)
    }

    /** [page] is being left from [onPresets] (a preset keeps the one reached) or from below them, where without a choice the colour in effect when the cursor came down comes back. */
    fun leave(
        page: PanelPage,
        onPresets: Boolean,
    ) {
        if (!onPresets && origin != null) restore(page)
        origin = null
    }

    /** Reset all ran: what was written before no longer stands. */
    fun forgetWritten() {
        generation++
        lastWritten.clear()
        dragged = null
        fingerDown = false
        actions.colorPreview.clear()
    }

    /**
     * The keyboard's Done (or "Use this colour") on the hex field: a valid code that reads is used as
     * typed. One that does not read is refused: the field stays, the keyboard goes down
     * and the cursor lands on the row that uses [ColorRefusal.nearest] instead, which this takes
     * when it is asked again for the same code.
     */
    fun enterHex(
        page: PanelPage,
        text: String,
    ) {
        val typed = parseHexColor(text)
        if (typed == null) {
            hexError = true
            return
        }
        hexError = false
        val nearest = hexRefusal?.takeIf { hexRefusedText == text }?.nearest
        val rgb = nearest ?: typed
        if (nearest == null) {
            val refused = refusal(page, ColorSetting.Custom(rgb.value))
            if (refused != null) {
                lastWrite = refused
                hexRefusal = refused.refusal
                hexRefusedText = text
                hexOnUse = true
                hexKeyboard.stop()
                return
            }
        }
        hexRefusal = null
        isEditingHex = false
        hexOnUse = false
        hexKeyboard.stop()
        origin = ColorSetting.Custom(rgb.value)
        if (ColorPages.isBackground(page)) originText = ColorPages.textFor(ColorSetting.Custom(rgb.value), baseText)
        write(page, ColorSetting.Custom(rgb.value), check = false)
        ColorShades.nearestStopAndShade(ColorPages.roleOf(ColorPages.kindOf(page) ?: return), rgb, paletteBackground).let {
            stop = it.first
            shadePlace = it.second
        }
        // The cursor is on the Color code row, whose index is after the shades of the new stop.
        onHexRowMoved(hexIndex(page))
    }

    override fun clearHexRefusal() {
        hexRefusal = null
        if (lastWrite?.refusal != null) lastWrite = null
    }

    /**
     * Starts empty, so typing never runs on after the current code (the field shows it as a hint),
     * typed into with [keyboard] (Plumemu's tray opens on its digits, Android's comes up).
     */
    fun startEditingHex(
        page: PanelPage,
        keyboard: KeyboardChoice,
    ) {
        hexText = ""
        hexTyping = HexTyping(this, keyboard) { text -> enterHex(page, text) }
        isEditingHex = true
        hexError = false
        clearHexRefusal()
        askHexKeyboard()
        hexError = false
    }

    /** [settings] with the colours written here, which the store may not have answered with yet. */
    fun withWritten(settings: PlumSettings): PlumSettings =
        settings.withAppearance { a ->
            a.copy(
                background = lastWritten[ColorKind.Background] ?: a.background,
                textColor = lastWritten[ColorKind.Text]?.let(ColorPages::textOf) ?: a.textColor,
                accent = lastWritten[ColorKind.Accent] ?: a.accent,
            )
        }

    private fun restore(page: PanelPage) {
        putBack(page, keep = null)
    }

    /**
     * Writes [color] for [page]'s setting, with no comparison against what is shown (it lags behind
     * quick presses). A pick that does not read ([check]) is refused: nothing is recorded as written
     * and the colour the panel opened with comes back instead. A preview on screen follows the write
     * (a step or a put-back while a finger is down), so what is shown is what the store will hold.
     */
    private fun write(
        page: PanelPage,
        color: ColorSetting,
        text: TextColorSetting? = null,
        check: Boolean = true,
        keep: ColorWrite? = null,
    ) {
        if (check) {
            val refused = refusal(page, color)
            if (refused != null) {
                lastWrite = refused
                if (origin != color) putBack(page, keep = refused)
                return
            }
        }
        val kind = ColorPages.kindOf(page) ?: return
        dragged = null
        lastWritten[kind] = color
        if (actions.colorPreview.isActive) actions.colorPreview.show(previewOf(page, color, text).appearance, fingerDown = fingerDown)
        val asked = generation
        actions.update { stored ->
            val isBackground = kind == ColorKind.Background
            val result = ColorPages.writeColor(page, color, stored, text ?: baseText.takeIf { isBackground }, baseAccent.takeIf { isBackground })
            // Putting the opening colour back after a refusal keeps the refusal to show.
            lastWrite = keep ?: result
            if (asked == generation) recordWritten(result.appearance)
            stored.copy(appearance = result.appearance)
        }
    }

    /**
     * What a finger dragging along the Color strip moved [page] to: shown at once ([ColorPreview]) and remembered in
     * [dragged], but not stored: the store is written once, when the finger lifts or is cancelled ([releaseDrag]).
     * A colour that does not read is refused as a written one is.
     */
    private fun showDragged(
        page: PanelPage,
        color: ColorSetting,
    ) {
        fingerDown = true
        val refused = refusal(page, color)
        if (refused != null) {
            lastWrite = refused
            val opening = origin
            if (opening != null && opening != color) {
                dragged = Dragged(page, opening, restoring = true)
                actions.colorPreview.show(openingAppearance(page, opening), fingerDown = true)
            }
            return
        }
        val kind = ColorPages.kindOf(page) ?: return
        lastWritten[kind] = color
        val result = previewOf(page, color, null)
        lastWrite = result
        recordWritten(result.appearance)
        dragged = Dragged(page, color)
        actions.colorPreview.show(result.appearance, fingerDown = true)
    }

    /** What [putBack] makes of [opening], for the preview. */
    private fun openingAppearance(
        page: PanelPage,
        opening: ColorSetting,
    ): AppearanceSettings =
        if (ColorPages.kindOf(page) == ColorKind.Background && opening == openAppearance.background) {
            withWritten(seen).appearance.copy(background = opening, textColor = baseText, accent = baseAccent)
        } else {
            previewOf(page, opening, originText).appearance
        }

    /** What writing [color] would make of the appearance, worked out on the settings last seen (the store may lag behind them). */
    private fun previewOf(
        page: PanelPage,
        color: ColorSetting,
        text: TextColorSetting?,
    ): ColorWrite {
        val isBackground = ColorPages.kindOf(page) == ColorKind.Background
        return ColorPages.writeColor(page, color, withWritten(seen), text ?: baseText.takeIf { isBackground }, baseAccent.takeIf { isBackground })
    }

    /** The colour the panel opened with (or the one last picked) is written again; [keep] is a refusal to go on showing. */
    private fun putBack(
        page: PanelPage,
        keep: ColorWrite?,
    ) {
        val opening = origin ?: return
        if (ColorPages.kindOf(page) == ColorKind.Background && opening == openAppearance.background) {
            writeOpeningBackground(opening, keep)
        } else {
            write(page, opening, originText, check = false, keep = keep)
        }
    }

    /** The background the panel opened with comes back with the text and accent exactly as stored: nothing is adjusted or rewritten. */
    private fun writeOpeningBackground(
        background: ColorSetting,
        keep: ColorWrite?,
    ) {
        lastWritten[ColorKind.Background] = background
        dragged = null
        if (actions.colorPreview.isActive) {
            actions.colorPreview.show(withWritten(seen).appearance.copy(background = background, textColor = baseText, accent = baseAccent), fingerDown = fingerDown)
        }
        val forgottenSince = generation
        actions.update { stored ->
            val result = stored.appearance.copy(background = background, textColor = baseText, accent = baseAccent)
            lastWrite = keep ?: ColorWrite(result)
            if (forgottenSince == generation) recordWritten(result)
            stored.copy(appearance = result)
        }
    }

    private fun recordWritten(appearance: AppearanceSettings) {
        lastWritten[ColorKind.Background] = appearance.background
        lastWritten[ColorKind.Text] = ColorSetting.Custom(appearance.textColor.toRgb().value)
        lastWritten[ColorKind.Accent] = appearance.accent
    }

    /** [color] refused on [page] with the reason against the appearance the panel opened on, else null. */
    private fun refusal(
        page: PanelPage,
        color: ColorSetting,
    ): ColorWrite? = ColorPages.refusal(page, color, openAppearance)

    /** True (and [lastWrite] says so) when [color] does not read on the background the panel opened on. */
    private fun refuses(
        page: PanelPage,
        color: ColorSetting,
    ): Boolean {
        val refused = refusal(page, color) ?: return false
        lastWrite = refused
        return true
    }

}
