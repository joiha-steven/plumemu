package com.plumemu.coreui.theme

/** The Background setting: one of the presets or the person's own colour. */
sealed interface BackgroundChoice {
    val color: Rgb

    data class Preset(
        val preset: BackgroundPreset,
    ) : BackgroundChoice {
        override val color: Rgb get() = preset.color
    }

    /** From the custom palette or a typed hex code; used exactly as picked. */
    data class Custom(
        override val color: Rgb,
    ) : BackgroundChoice
}

/**
 * The Accent setting: a preset, which resolves from the set that matches the text mode (Mono is
 * the text colour), the person's own colour, which never changes, or [BySystem], the colour of
 * the system in view ([SystemAccents]).
 */
sealed interface AccentChoice {
    fun resolve(textMode: TextMode): Rgb

    data class Preset(
        val preset: AccentPreset,
    ) : AccentChoice {
        override fun resolve(textMode: TextMode): Rgb = preset.colorFor(textMode)
    }

    data class Custom(
        val color: Rgb,
    ) : AccentChoice {
        override fun resolve(textMode: TextMode): Rgb = color
    }

    /**
     * The accent is the colour of the system in view. [fallback] (a preset or own colour, the accent
     * the person had before) is what shows where no system is in view and for a system without a
     * colour; [resolve] is that fallback.
     */
    data class BySystem(
        val fallback: AccentChoice,
    ) : AccentChoice {
        init {
            require(fallback !is BySystem) { "By system cannot fall back to itself" }
        }

        override fun resolve(textMode: TextMode): Rgb = fallback.resolve(textMode)
    }
}
