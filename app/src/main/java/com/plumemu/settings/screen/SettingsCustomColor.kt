package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Direction
import com.plumemu.game.KeyboardAsk
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.ColorRefusal
import com.plumemu.settings.ColorWrite
import com.plumemu.settings.toRgb
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The custom palette pages of [SettingsModel]: the colour a page opened with (put back when it is
 * left without choosing), the colours written so far, and the hex code field. Swatches preview as
 * the focus moves; A or a tap chooses.
 */
@Stable
internal class SettingsCustomColor(
    private val actions: SettingsActions,
) {
    /** False from opening a custom page until the cursor moves: the ring then sits on a swatch that was never previewed. */
    var cursorMoved: Boolean = false
        private set

    /** The hex code field has the keyboard. */
    var isEditingHex: Boolean by mutableStateOf(false)
        private set

    /**
     * The asks for the hex keyboard (starting to type, A on the field, a tap on it): [hexKeyboardAsks]
     * counts them, so A brings Android's keyboard back after the system's Back closed it; while
     * [isTypingHex] Plumemu's tray is up ([HexTyping]).
     */
    val hexKeyboard = KeyboardAsk()

    val hexKeyboardAsks: Int get() = hexKeyboard.asks

    /**
     * The field as Plumemu's keyboard types into it, made at each start of editing with the
     * keyboard Settings › Controls › Keyboard named then; null before the first.
     */
    var hexTyping: HexTyping? by mutableStateOf(null)
        private set

    /** The cursor is on "Use this colour" under the field (keyboard down); false: on the field. */
    var hexOnUse: Boolean by mutableStateOf(false)
        private set

    /** A on the field or a tap on it: the cursor is on the field and its keyboard comes up (again). */
    fun askHexKeyboard() {
        hexOnUse = false
        hexKeyboard.ask()
    }

    /** The hex keyboard is wanted: asked for and the cursor has not left the field since. */
    val isTypingHex: Boolean get() = hexKeyboard.isTyping

    /** D-pad Down / Up between the field and "Use this colour"; neither opens the keyboard. */
    fun moveInHex(toUse: Boolean) {
        hexOnUse = toUse
        hexKeyboard.stop()
    }

    /** What the hex code field holds while it has the keyboard. */
    var hexText: String by mutableStateOf("")
        private set

    /** The last typed hex code could not be read. */
    var hexError: Boolean by mutableStateOf(false)
        private set

    /**
     * The typed code [hexRefusedText] was refused as unreadable and [ColorRefusal.nearest] is the
     * colour offered instead: "Use" on the row below the field takes it. Null otherwise.
     */
    var hexRefusal: ColorRefusal? by mutableStateOf(null)
        private set

    private var hexRefusedText: String = ""

    /** What a finger on a slider has brought the page to: shown now, written on lift. */
    private class Dragged(
        val page: PanelPage,
        val color: ColorSetting,
        /** The drag went into a colour that is refused: [color] is the opening one, put back on release. */
        val restoring: Boolean = false,
    )

    private var dragged: Dragged? = null

    /** The settings last given: what a preview is laid over. */
    private var seen: PlumSettings = PlumSettings()

    /** The colour a custom page opened with, put back when it is left without choosing; null once chosen. */
    private var origin: ColorSetting? = null

    /** The panel index [focus] saw last (-1 after [open]): the Color code row keeps the colour tuned when reached from a slider. */
    private var lastFocused = -1

    /** The colour of the setting when its page opened, drawn (a preset resolved), for the sliders when the opening colour comes back. */
    private var openColour = 0

    /**
     * The colour the page marks with a check: the one it opened with, then the one chosen; never the previewed one (the ring's).
     * Null until a page opens, so a page shown without [open] marks the colour in use, not black.
     */
    var selectedColour: Rgb? by mutableStateOf(null)
        private set

    /** The text colour in place when a background page opened, put back with [origin]. */
    private var originText: TextColorSetting? = null

    /** The text the panel opened with: previews settle from it, so browsing backgrounds never drifts a custom text colour. */
    private var baseText: TextColorSetting = TextColorSetting.Light

    /** Likewise the accent the panel opened with: a preview that moves it to read is settled from this, and cancelling puts it back. */
    internal var baseAccent: ColorSetting = ColorSetting.Preset(AppearanceSettings.DEFAULT_ACCENT)
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

    /** The swatches of custom [page] now: only the colours that read on the background the panel opened on. */
    fun palette(page: PanelPage): List<Rgb> = ColorPages.palette(page, paletteBackground)

    /** [index] of custom [page] is its hex row: after the swatches and the three sliders. */
    fun isHexRow(
        page: PanelPage,
        index: Int,
    ): Boolean = isCustomPage(page) && index == palette(page).size + ColorSlider.entries.size

    /** The slider at [index] of custom [page] (right after the swatches: Hue, Saturation, Lightness), else null. */
    fun sliderAt(
        page: PanelPage,
        index: Int,
    ): ColorSlider? = if (isCustomPage(page)) ColorSlider.entries.getOrNull(index - palette(page).size) else null

    /** The colour the sliders edit, as hue, saturation and lightness: the current colour on opening, then what the sliders and the swatches set. */
    var hsl: Hsl by mutableStateOf(Hsl(0, 0, 0))
        private set

    /** Which slider values of [page] can be stopped on (they read on the background the panel opened on). */
    fun sliderReach(page: PanelPage): SliderReach = ColorPages.sliderReach(page, paletteBackground)

    /** Where the cursor goes from [index] of custom [page] in [direction] ([nextCustomIndex]). */
    fun nextIndex(
        page: PanelPage,
        index: Int,
        direction: Direction,
    ): Int = nextCustomIndex(palette(page).size, index, direction)

    /**
     * Left (-1) or Right (+1) on [slider]: the thumb goes to the next value that reads, previewed
     * like a swatch. False at the end of the track (or when nothing further reads): nothing changes.
     */
    fun step(
        page: PanelPage,
        slider: ColorSlider,
        direction: Int,
    ): Boolean {
        val next = sliderReach(page).stepped(hsl, slider, direction) ?: return false
        move(page, next)
        return true
    }

    /**
     * The slider a finger holds and where on its track (0..1, the finger's own place): its thumb is
     * drawn there, whatever value is previewed (the thumb follows the finger,
     * also over a stretch that is skipped). Null when no finger is down; the thumb then settles on the value.
     */
    var fingerOn: Pair<ColorSlider, Float>? by mutableStateOf(null)
        private set

    /**
     * The finger lifted or was cancelled (or the screen went with it down): the thumb settles on
     * the value in effect and the colour it showed is written, once, through the normal path.
     */
    fun releaseSlider() {
        fingerOn = null
        soundedAt = null
        val d = dragged
        dragged = null
        if (d != null) {
            if (d.restoring) putBack(d.page, keep = lastWrite?.takeIf { it.refusal != null }) else write(d.page, d.color)
        }
        actions.colorPreview.release()
    }

    /** The screen is going: what [leave] puts back is not written twice; a chosen colour is written once. */
    fun leaveAndRelease(page: PanelPage) {
        if (origin != null) dragged = null
        releaseSlider()
        leave(page)
    }

    /** The slider value the touch last sounded at; null before its first sound. */
    private var soundedAt: Int? = null

    /**
     * A tap or drag at [fraction] of [slider]'s track: the thumb goes to the finger, and the value in
     * effect is the finger's at fine grain, moved to the nearest one that reads. True when the value changed
     * and it is time to sound: at most once per D-pad step of travel, so a fast drag does not machine-gun.
     */
    fun setSlider(
        page: PanelPage,
        slider: ColorSlider,
        fraction: Float,
    ): Boolean {
        fingerOn = slider to fraction.coerceIn(0f, 1f)
        val next = sliderReach(page).nearest(hsl, slider, fraction) ?: return false
        if (next[slider] == hsl[slider]) return false
        move(page, next, drag = true)
        if (soundedAt?.let { abs(next[slider] - it) < slider.step } == true) return false
        soundedAt = next[slider]
        return true
    }

    private fun move(
        page: PanelPage,
        next: Hsl,
        drag: Boolean = false,
    ) {
        cursorMoved = true
        hsl = next
        val color = ColorSetting.Custom(next.rgb().value)
        if (drag) showDragged(page, color) else write(page, color)
    }

    /**
     * Remembers the current colour of [page] (a presets or a custom page of one setting) and what the
     * panel opened with, and returns where the focus starts on the custom page: the swatch of the current colour or the nearest one, never the hex row.
     */
    fun open(
        page: PanelPage,
        settings: PlumSettings,
    ): Int {
        if (dragged != null) releaseSlider() // an unwritten drag is written first
        val kind = ColorPages.kindOf(page) ?: return 0
        // What was last written, if anything: the store (and so [settings]) may not have it yet.
        val written = withWritten(settings)
        seen = settings
        paletteBackground = ColorPages.backgroundOf(written)
        val current = ColorPages.settingOf(page, written)
        val palette = palette(ColorPages.customOf(kind))
        val at = ColorPages.indexOfNearest(palette, ColorPages.drawnColor(kind, written).value)
        origin = current
        openColour = ColorPages.drawnColor(kind, written).value
        selectedColour = Rgb(openColour)
        lastFocused = -1
        hsl = Hsl.of(Rgb(openColour))
        fingerOn = null
        dragged = null
        soundedAt = null
        cursorMoved = false
        openAppearance = written.appearance
        // The text as stored, not as drawn: leaving without a pick must not rewrite a stored text the background draws adjusted.
        baseText = written.appearance.textColor
        baseAccent = written.appearance.accent
        originText = baseText
        lastWrite = null
        hexRefusal = null
        return at
    }

    /** The focus reached [index] of [page]: a swatch is previewed; a slider or the hex row keeps the colour shown. */
    fun focus(
        page: PanelPage,
        index: Int,
    ) {
        cursorMoved = true
        val previousIndex = lastFocused
        lastFocused = index
        when {
            // Onto a slider the colour shown stays (the sliders were set from it).
            sliderAt(page, index) != null -> Unit
            // The Color code row from a slider keeps the colour tuned (its code is shown); from a swatch (a tap, or the grid) the opening colour comes back, as before the sliders.
            isHexRow(page, index) ->
                if (sliderAt(page, previousIndex) == null) {
                    restore(page)
                    hsl = Hsl.of(Rgb(((origin as? ColorSetting.Custom)?.rgb ?: openColour) and RGB_MASK))
                }
            else ->
                ColorPages.colorAt(page, index, paletteBackground, baseAccent)?.let {
                    write(page, it)
                    (it as? ColorSetting.Custom)?.let { custom -> hsl = Hsl.of(Rgb(custom.rgb and RGB_MASK)) }
                }
        }
    }

    /** A or a tap chose [color] (null: the one shown now); leaving the page keeps it. */
    fun choose(
        page: PanelPage,
        color: ColorSetting?,
    ) {
        if (color != null && refuses(page, color)) return // nothing stored; leaving puts the opening colour back
        origin = color
        // A with no colour (the panel's A) takes the one shown: what the ring last previewed.
        (color ?: ColorPages.kindOf(page)?.let { lastWritten[it] })?.let { if (it is ColorSetting.Custom) selectedColour = Rgb(it.rgb and RGB_MASK) }
        if (color != null && ColorPages.isBackground(page)) originText = ColorPages.textFor(color, baseText)
    }

    /** [page] is being left: without a choice, the colour it opened with comes back. */
    fun leave(page: PanelPage) {
        fingerOn = null
        soundedAt = null
        if (origin == null) return
        restore(page)
        origin = null
    }

    /** Reset all ran: what was written before no longer stands. */
    fun forgetWritten() {
        generation++
        lastWritten.clear()
        dragged = null
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
        selectedColour = rgb
        if (ColorPages.isBackground(page)) originText = ColorPages.textFor(ColorSetting.Custom(rgb.value), baseText)
        write(page, ColorSetting.Custom(rgb.value), check = false)
        hsl = Hsl.of(rgb)
    }

    fun onHexTextChange(text: String) {
        hexText = text.take(HEX_TEXT_MAX)
        if (hexRefusal != null && hexText != hexRefusedText) clearHexRefusal()
    }

    private fun clearHexRefusal() {
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

    fun stopEditingHex() {
        isEditingHex = false
        hexOnUse = false
        hexKeyboard.stop()
        hexError = false
        clearHexRefusal()
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
        if (actions.colorPreview.isActive) actions.colorPreview.show(previewOf(page, color, text).appearance, fingerDown = fingerOn != null)
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
     * What the slider's finger moved [page] to: shown at once ([ColorPreview]) and remembered in
     * [dragged], but not stored: the store is written once, when the finger lifts ([releaseSlider]).
     * A colour that does not read is refused as a written one is.
     */
    private fun showDragged(
        page: PanelPage,
        color: ColorSetting,
    ) {
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
            actions.colorPreview.show(withWritten(seen).appearance.copy(background = background, textColor = baseText, accent = baseAccent), fingerDown = fingerOn != null)
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

    companion object {
        fun isCustomPage(page: PanelPage?): Boolean = page == PanelPage.BackgroundCustom || page == PanelPage.AccentCustom || page == PanelPage.TextCustom

        /** "#" and six digits. */
        private const val HEX_TEXT_MAX = 7
        private const val RGB_MASK = 0xFFFFFF
    }
}
