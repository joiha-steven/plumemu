package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.settings.ColorWrite
import com.plumemu.settings.checkBackground
import com.plumemu.settings.toBackgroundChoice
import com.plumemu.settings.toRgb
import com.plumemu.settings.toTextSetting
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.writeAccent
import com.plumemu.settings.writeBackground
import com.plumemu.settings.writeText

/** The three colour settings that have a presets page and a palette page (Background, Text color, Accent color). */
internal enum class ColorKind { Background, Text, Accent }

/**
 * The colour panels: presets (the last item is "+", your own colour) and the custom palette, the
 * same for the three settings. A text colour travels as a [ColorSetting.Custom] holding its colour
 * (Light and Dark are `Custom` of their colours here) and is turned into a [TextColorSetting] only
 * when written. The palettes offer only colours that pass [Readability] on the background shown,
 * filled row by row so every cell can take the focus.
 */
internal object ColorPages {
    val backgroundPresets: List<BackgroundPreset> = BackgroundPreset.entries
    val accentPresets: List<AccentPreset> = AccentPreset.entries

    /** A vivid ready background of the first page: stored as the person's own colour, named by this string. */
    class VividBackground(
        val name: Int,
        val color: Rgb,
    )

    /** Six after the eight presets and "+" make four rows of four in the wide panel (its text column, 220 wide); taken from the custom grid so the two always agree. */
    val vividBackgrounds: List<VividBackground> =
        listOf(
            VividBackground(R.string.settings_bg_lemon, CustomPalette.backgroundAt(0, 3)),
            VividBackground(R.string.settings_bg_tangerine, CustomPalette.backgroundAt(0, 2)),
            VividBackground(R.string.settings_bg_sky, CustomPalette.backgroundAt(0, 6)),
            VividBackground(R.string.settings_bg_lavender, CustomPalette.backgroundAt(1, 7)),
            VividBackground(R.string.settings_bg_cobalt, CustomPalette.backgroundAt(3, 6)),
            VividBackground(R.string.settings_bg_wine, CustomPalette.backgroundAt(4, 1)),
        )

    /** Every background swatch of the first page, in order. */
    val backgroundSwatchCount: Int get() = backgroundPresets.size + vividBackgrounds.size

    const val PALETTE_COLUMNS = 8

    fun kindOf(page: PanelPage): ColorKind? =
        when (page) {
            PanelPage.BackgroundPresets, PanelPage.BackgroundCustom -> ColorKind.Background
            PanelPage.TextPresets, PanelPage.TextCustom -> ColorKind.Text
            PanelPage.AccentPresets, PanelPage.AccentCustom -> ColorKind.Accent
            else -> null
        }

    fun kindOf(option: SettingsOption): ColorKind? =
        when (option) {
            SettingsOption.Background -> ColorKind.Background
            SettingsOption.TextColor -> ColorKind.Text
            SettingsOption.Accent -> ColorKind.Accent
            else -> null
        }

    fun presetsOf(kind: ColorKind): PanelPage =
        when (kind) {
            ColorKind.Background -> PanelPage.BackgroundPresets
            ColorKind.Text -> PanelPage.TextPresets
            ColorKind.Accent -> PanelPage.AccentPresets
        }

    fun customOf(kind: ColorKind): PanelPage =
        when (kind) {
            ColorKind.Background -> PanelPage.BackgroundCustom
            ColorKind.Text -> PanelPage.TextCustom
            ColorKind.Accent -> PanelPage.AccentCustom
        }

    // Hand-picked tinted near-blacks and near-whites (shell and ink colours); each is moved in lightness
    // only as far as it must to reach 7:1 on the background, so the same list serves every background. The list is long because on a bright background the first picks all
    // settle to near-black and collapse into each other (#EF8484 kept five); the saturated tail stays distinct.
    private val DARK_TEXT_CANDIDATES =
        listOf(
            0x14213D to R.string.settings_text_navy, 0x3B2A20 to R.string.settings_text_cocoa, 0x4A1D4F to R.string.settings_text_plum,
            0x14452F to R.string.settings_text_pine, 0x0F4C4A to R.string.settings_text_teal, 0x5A1A1A to R.string.settings_text_maroon,
            0x1A1A1A to R.string.settings_text_charcoal, 0x0A2E7A to R.string.settings_text_blue, 0x5C2A00 to R.string.settings_text_brown,
            0x007A3D to R.string.settings_text_green, 0x7A0A3D to R.string.settings_text_berry, 0x3A1F8F to R.string.settings_text_indigo,
        ).map { Rgb(it.first) to it.second }
    private val LIGHT_TEXT_CANDIDATES =
        listOf(
            0xF5EFD6 to R.string.settings_text_cream, 0xF2E2A6 to R.string.settings_text_butter, 0xBDF0D2 to R.string.settings_text_mint,
            0xBFE6FF to R.string.settings_text_sky, 0xF8D0D6 to R.string.settings_text_blush, 0xDAD0F5 to R.string.settings_text_lilac,
            0xFFD9B0 to R.string.settings_text_peach, 0xFFB0B0 to R.string.settings_text_coral, 0xB0FFB0 to R.string.settings_text_lime,
            0xB0C8FF to R.string.settings_text_periwinkle, 0xFFF0A0 to R.string.settings_text_lemon,
            0xFFE600 to R.string.settings_text_yellow, 0x40FFE8 to R.string.settings_text_aqua, 0xFF7FB0 to R.string.settings_text_rose,
            0xFFB347 to R.string.settings_text_amber, 0xC9A0FF to R.string.settings_text_violet,
        ).map { Rgb(it.first) to it.second }
    private const val TEXT_PRESETS_MAX = 8
    // Two swatches closer than this in OKLab (about 4 times the smallest visible step) read as one colour at swatch size.
    private const val NEAR_DUPLICATE_OKLAB = 0.04

    private fun lookAlike(
        a: Rgb,
        b: Rgb,
    ): Boolean {
        val x = oklab(a)
        val y = oklab(b)
        return kotlin.math.sqrt((x[0] - y[0]).let { it * it } + (x[1] - y[1]).let { it * it } + (x[2] - y[2]).let { it * it }) < NEAR_DUPLICATE_OKLAB
    }

    private fun oklabChroma(c: Rgb): Double = oklab(c).let { kotlin.math.hypot(it[1], it[2]) }

    private fun oklab(c: Rgb): DoubleArray {
        fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        val r = lin(c.red)
        val g = lin(c.green)
        val b = lin(c.blue)
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /**
     * The text presets that read on [background], 6 to 8 of them: Light and Dark first where they pass,
     * then tinted colours of the side that reads (light text on a dark background, dark on a light one),
     * deduplicated. Colours stored as [TextColorSetting.Custom], except Light and Dark themselves.
     */
    fun textPresetsOn(background: Rgb): List<TextColorSetting> = namedTextPresetsOn(background).map { it.first }

    /** The name (a string resource) of [text] when it is a tinted preset offered on [background]; null for Light, Dark and any other colour. */
    fun textPresetNameOn(
        background: Rgb,
        text: TextColorSetting,
    ): Int? = namedTextPresetsOn(background).firstOrNull { it.first == text }?.second

    private fun namedTextPresetsOn(background: Rgb): List<Pair<TextColorSetting, Int?>> {
        synchronized(lastTextPresets) { lastTextPresets[0]?.takeIf { it.first == background }?.let { return it.second } }
        return buildTextPresets(background).also { synchronized(lastTextPresets) { lastTextPresets[0] = background to it } }
    }

    // The panel asks for the same background on every frame.
    private val lastTextPresets = arrayOfNulls<Pair<Rgb, List<Pair<TextColorSetting, Int?>>>>(1)

    private fun buildTextPresets(background: Rgb): List<Pair<TextColorSetting, Int?>> {
        if (!Readability.backgroundAllowsText(background)) return emptyList()
        val chosen = ArrayList<Pair<Rgb, Int?>>()
        for (mode in TextMode.entries) if (Readability.passes(mode.color, background, ColorRole.Text)) chosen += mode.color to null
        val candidates = if (TextMode.bestOn(background) == TextMode.Light) LIGHT_TEXT_CANDIDATES else DARK_TEXT_CANDIDATES
        // Settle every candidate first, then keep the most saturated of any group that looks alike: on a bright background many
        // candidates are pushed to near-black and only the saturated ones still differ from each other.
        val settled = candidates.mapIndexed { i, (candidate, name) -> Triple(i, Readability.nearestPassing(candidate, background, ColorRole.Text), name) }
        val kept = ArrayList<Triple<Int, Rgb, Int>>()
        for (entry in settled.sortedByDescending { oklabChroma(it.second) }) {
            if (chosen.size + kept.size >= TEXT_PRESETS_MAX) break
            if ((chosen.map { it.first } + kept.map { it.second }).none { lookAlike(it, entry.second) }) kept += entry
        }
        for ((_, rgb, name) in kept.sortedBy { it.first }) chosen += rgb to name
        return chosen.map { (rgb, name) -> rgb.toTextSetting() to name }
    }

    /** No text colour reads on [background] (a middle-lightness background stored by an older version): the Text panel says so instead of offering colours. */
    fun textBlockedOn(background: Rgb): Boolean = !Readability.backgroundAllowsText(background)

    /**
     * Items of a presets page: the presets and "+" (the Text page shows only the presets that read on
     * [background]; on a background nothing reads on it has one item, which does nothing).
     */
    fun presetCount(
        page: PanelPage,
        background: Rgb,
    ): Int =
        if (kindOf(page) == ColorKind.Text && textBlockedOn(background)) {
            1
        } else {
            1 +
                when (kindOf(page)) {
                    ColorKind.Background -> backgroundSwatchCount
                    ColorKind.Text -> textPresetsOn(background).size
                    // The accent's presets are followed by the By system tile, then "+".
                    else -> accentPresets.size + 1
                }
        }

    /** The index of the By system tile on the accent presets page (right after the presets, before "+"). */
    val bySystemIndex: Int get() = accentPresets.size

    /** The swatches of a custom page on [background]: [PaletteCuration]'s 3 rows of 8 out of the full palette; its last item, after them, is the hex code row. */
    fun palette(
        page: PanelPage,
        background: Rgb,
    ): List<Rgb> =
        PaletteCuration.curated(
            when (kindOf(page)) {
                ColorKind.Background -> CustomPalette.backgroundSwatches
                ColorKind.Text -> CustomPalette.textSwatchesOn(background)
                else -> CustomPalette.accentSwatchesOn(background)
            },
        )

    /**
     * Where the cursor lands on [palette] for [rgb]: the swatch of that colour, else the nearest one,
     * never the hex row (the hex code is the last row, reached on purpose). An empty palette has
     * only the hex row, index 0.
     */
    fun indexOfNearest(
        palette: List<Rgb>,
        rgb: Int,
    ): Int {
        val target = Rgb(rgb and RGB_MASK)
        fun distance(a: Rgb) = (a.red - target.red).let { it * it } + (a.green - target.green).let { it * it } + (a.blue - target.blue).let { it * it }
        return palette.indices.minByOrNull { distance(palette[it]) } ?: 0
    }

    /** The colour [kind] is drawn in under [settings], whether it is stored as a preset or as the person's own. */
    fun drawnColor(
        kind: ColorKind,
        settings: PlumSettings,
    ): Rgb =
        settings.toThemeSettings().let {
            when (kind) {
                ColorKind.Background -> it.backgroundColor
                ColorKind.Text -> it.textColor
                ColorKind.Accent -> it.accentColor
            }
        }

    /** The background [settings] draw: what the Text and Accent palettes must read on. */
    fun backgroundOf(settings: PlumSettings): Rgb = settings.toThemeSettings().backgroundColor

    /** The page and item the panel opens at for [option]: the current colour, or the hex row for a typed one. */
    fun openingFor(
        option: SettingsOption,
        settings: PlumSettings,
    ): Pair<PanelPage, Int> {
        val kind = kindOf(option) ?: error("$option has no colour panel")
        val presets = presetsOf(kind)
        val custom = customOf(kind)
        val setting = settingOf(presets, settings)
        val inPalette = { page: PanelPage, rgb: Int -> indexOfNearest(palette(page, backgroundOf(settings)), rgb) }
        return when (kind) {
            ColorKind.Text -> {
                val text = settings.appearance.textColor
                val background = backgroundOf(settings)
                when {
                    textBlockedOn(background) -> presets to 0
                    text is TextColorSetting.Custom && textPresetsOn(background).contains(text) -> presets to textPresetsOn(background).indexOf(text)
                    text is TextColorSetting.Custom -> custom to inPalette(custom, text.rgb)
                    else -> presets to textPresetsOn(background).indexOf(text).coerceAtLeast(0)
                }
            }
            else ->
                when (setting) {
                    is ColorSetting.Preset -> {
                        val names = if (kind == ColorKind.Background) backgroundPresets.map { it.name } else accentPresets.map { it.name }
                        presets to names.indexOf(setting.name).coerceAtLeast(0)
                    }
                    is ColorSetting.Custom -> {
                        val vivid = if (kind == ColorKind.Background) vividBackgrounds.indexOfFirst { it.color.value == setting.rgb and RGB_MASK } else -1
                        if (vivid >= 0) presets to backgroundPresets.size + vivid else custom to inPalette(custom, setting.rgb)
                    }
                    is ColorSetting.BySystem -> presets to bySystemIndex
                }
        }
    }

    /** The colour setting [page] changes, as stored in [settings] (a text colour as the `Custom` of its colour). */
    fun settingOf(
        page: PanelPage,
        settings: PlumSettings,
    ): ColorSetting =
        when (kindOf(page)) {
            ColorKind.Background -> settings.appearance.background
            ColorKind.Text -> ColorSetting.Custom(settings.appearance.textColor.toRgb().value)
            else -> settings.appearance.accent
        }

    /** The colour at [index] of [page] (palettes read on [background]); null for "+", the hex row and pages without colours. */
    fun colorAt(
        page: PanelPage,
        index: Int,
        background: Rgb,
        accent: ColorSetting = ColorSetting.Preset(AppearanceSettings.DEFAULT_ACCENT),
    ): ColorSetting? =
        when (page) {
            PanelPage.BackgroundPresets ->
                if (index < backgroundPresets.size) {
                    backgroundPresets.getOrNull(index)?.let { ColorSetting.Preset(it.name) }
                } else {
                    vividBackgrounds.getOrNull(index - backgroundPresets.size)?.let { ColorSetting.Custom(it.color.value) }
                }
            // By system keeps the accent in place now as its fallback; picking it again keeps the one it had.
            PanelPage.AccentPresets ->
                if (index == bySystemIndex) {
                    ColorSetting.BySystem((accent as? ColorSetting.BySystem)?.fallback ?: accent)
                } else {
                    accentPresets.getOrNull(index)?.let { ColorSetting.Preset(it.name) }
                }
            PanelPage.TextPresets -> textPresetsOn(background).getOrNull(index)?.let { ColorSetting.Custom(it.toRgb().value) }
            PanelPage.BackgroundCustom, PanelPage.AccentCustom, PanelPage.TextCustom -> palette(page, background).getOrNull(index)?.let { ColorSetting.Custom(it.value) }
            else -> null
        }

    fun isBackground(page: PanelPage): Boolean = kindOf(page) == ColorKind.Background

    private class ReachKey(
        val kind: ColorKind?,
        val background: Rgb,
        val reach: SliderReach,
    )

    @Volatile
    private var lastReach: ReachKey? = null

    /**
     * The slider values of [page] that may be stopped on, read on [background] (the one the panel opened on): the same check that refuses a pick.
     * The same object for the same page kind and background, so what it has worked out for a drag (`SliderReach.units`) is shared by the track and the finger.
     */
    fun sliderReach(
        page: PanelPage,
        background: Rgb,
    ): SliderReach {
        val kind = kindOf(page)
        lastReach?.takeIf { it.kind == kind && it.background == background }?.let { return it.reach }
        val reach =
            SliderReach(
                when (kind) {
                    ColorKind.Text -> { c: Rgb -> Readability.passes(c, background, ColorRole.Text) }
                    ColorKind.Accent -> { c: Rgb -> Readability.passes(c, background, ColorRole.Accent) }
                    else -> { c: Rgb -> Readability.backgroundAllowsText(c) }
                },
            )
        lastReach = ReachKey(kind, background, reach)
        return reach
    }

    /** The text [background] leaves of [current]: kept if it still reads, else moved. */
    fun textFor(
        background: ColorSetting,
        current: TextColorSetting,
    ): TextColorSetting = Readability.settleText(current.toRgb(), background.toBackgroundChoice().color).toTextSetting()

    /** [color] as a text colour setting. */
    fun textOf(color: ColorSetting): TextColorSetting = Rgb((color as? ColorSetting.Custom)?.rgb?.and(RGB_MASK) ?: 0).toTextSetting()

    /**
     * [color] on [page]: a background keeps the text and accent while they read and moves them
     * otherwise ([text] and [accent] say which to keep instead of the current ones); a text or accent
     * that does not read is refused. The result says which happened.
     */
    fun writeColor(
        page: PanelPage,
        color: ColorSetting,
        settings: PlumSettings,
        text: TextColorSetting? = null,
        accent: ColorSetting? = null,
    ): ColorWrite =
        when (kindOf(page)) {
            ColorKind.Background -> settings.appearance.writeBackground(color, text ?: settings.appearance.textColor, accent ?: settings.appearance.accent)
            ColorKind.Text -> settings.appearance.writeText(textOf(color))
            else -> settings.appearance.writeAccent(color)
        }

    /** [settings] after [writeColor]; unchanged when the pick was refused. */
    fun withColor(
        page: PanelPage,
        color: ColorSetting,
        settings: PlumSettings,
        text: TextColorSetting? = null,
    ): PlumSettings = settings.copy(appearance = writeColor(page, color, settings, text).appearance)

    private const val RGB_MASK = 0xFFFFFF

    /** [color] refused on [page] with the reason, against the appearance [opened] (the one the panel opened on), else null. */
    fun refusal(
        page: PanelPage,
        color: ColorSetting,
        opened: AppearanceSettings,
    ): ColorWrite? =
        when (kindOf(page)) {
            ColorKind.Background -> opened.checkBackground(color)
            ColorKind.Text -> opened.writeText(textOf(color))
            ColorKind.Accent -> opened.writeAccent(color)
            null -> null
        }?.takeIf { it.refusal != null }
}
