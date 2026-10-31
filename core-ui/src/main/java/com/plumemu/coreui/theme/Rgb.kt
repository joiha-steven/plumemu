package com.plumemu.coreui.theme

/**
 * An opaque sRGB colour as `0xRRGGBB`. Pure Kotlin, so the colour rules unit-test on the JVM;
 * [toComposeColor] is the only bridge to Compose.
 */
@JvmInline
value class Rgb(val value: Int) {
    init {
        require(value in 0..0xFFFFFF) { "Rgb takes 0xRRGGBB without alpha, got 0x${value.toString(16)}" }
    }

    val red: Int get() = (value shr 16) and 0xFF
    val green: Int get() = (value shr 8) and 0xFF
    val blue: Int get() = value and 0xFF

    /** Opaque ARGB, for Android APIs that take a colour int (system bars). */
    fun toArgb(): Int = (0xFF shl 24) or value

    /** `#RRGGBB` in upper case, as the design board writes it. */
    fun toHex(): String = "#%06X".format(value)

    override fun toString(): String = toHex()

    companion object {
        private val HEX = Regex("^#[0-9a-fA-F]{6}$")

        /** Parses `#RRGGBB`; null for anything else (a hex code the person typed may be wrong). */
        fun fromHexOrNull(hex: String): Rgb? = if (HEX.matches(hex)) Rgb(hex.substring(1).toInt(16)) else null

        /**
         * Builds a colour from channels in 0..255 that may be fractional or out of range, the way
         * the board's `hex()` does: clamp, then `Math.round` (halves round up).
         */
        fun fromChannels(
            red: Double,
            green: Double,
            blue: Double,
        ): Rgb = Rgb((roundChannel(red) shl 16) or (roundChannel(green) shl 8) or roundChannel(blue))

        // JavaScript's Math.round is floor(x + 0.5); kotlin.math.round would round halves to even
        // and drift from the board by one digit.
        private fun roundChannel(v: Double): Int = kotlin.math.floor(v.coerceIn(0.0, 255.0) + 0.5).toInt()
    }
}
