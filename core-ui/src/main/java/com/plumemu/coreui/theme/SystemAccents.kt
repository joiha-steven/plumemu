package com.plumemu.coreui.theme

/**
 * Accent color › By system: the accent while a system is in view is that
 * system's own colour, held to the same 3:1 on the background as any accent
 * ([Readability.nearestPassing]: lightness only, same hue).
 *
 * Built once per background from the platform data's colours ([systemColors]: system id to colour)
 * and read with [colorOf]; never per recomposition. A system with no colour, or no system in view
 * (null), has none here: the caller uses the fallback accent ([accentFor]).
 */
class SystemAccents(
    val background: Rgb,
    systemColors: Map<String, Rgb>,
) {
    private val settled: Map<String, Rgb> =
        systemColors.mapValues { (_, colour) -> Readability.nearestPassing(colour, background, ColorRole.Accent) }

    /** The accent for [system] on this background, or null where it has no colour (or [system] is null). */
    fun colorOf(system: String?): Rgb? = system?.let(settled::get)

    companion object {
        /** Four systems with well-apart colours (a purple, a blue, an orange, a green) for the By system tile. */
        val SAMPLE_SYSTEMS: List<String> = listOf("snes", "megadrive", "dreamcast", "xbox")

        /**
         * Their colours as the platform data has them, drawn as the tile's four-segment dot without
         * loading the data; the app's `SystemColorsTest` pins them to it.
         */
        val SAMPLE: List<Rgb> = listOf(Rgb(0x7B5EA7), Rgb(0x0060A8), Rgb(0xF26B21), Rgb(0x107C10))
    }
}

/**
 * The accent to draw while [viewedSystem] is in view: its colour when the accent is By system and
 * the system has one, else the accent as settings draw it ([PlumThemeSettings.accentColor]).
 * [accents] must be the table built for this settings' background. Callers with no system in view
 * (Settings, until the viewed system is passed) give null.
 */
fun PlumThemeSettings.accentFor(
    viewedSystem: String?,
    accents: SystemAccents,
): Rgb =
    if (accent is AccentChoice.BySystem) accents.colorOf(viewedSystem) ?: accentColor else accentColor
