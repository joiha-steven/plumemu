package com.plumemu.home

import androidx.annotation.StringRes
import com.plumemu.R

/**
 * The few lines about a system, by system id, for the
 * systems people ask about most. A system not in the map has no about text and its card looks as it did.
 * The strings carry their source in `strings.xml`.
 */
internal object SystemAbouts {
    private val byId: Map<String, Int> =
        mapOf(
            "nes" to R.string.system_about_nes,
            "snes" to R.string.system_about_snes,
            "n64" to R.string.system_about_n64,
            "gc" to R.string.system_about_gc,
            "wii" to R.string.system_about_wii,
            "switch" to R.string.system_about_switch,
            "gb" to R.string.system_about_gb,
            "gbc" to R.string.system_about_gbc,
            "gba" to R.string.system_about_gba,
            "nds" to R.string.system_about_nds,
            "n3ds" to R.string.system_about_n3ds,
            "megadrive" to R.string.system_about_megadrive,
            "mastersystem" to R.string.system_about_mastersystem,
            "gamegear" to R.string.system_about_gamegear,
            "megacd" to R.string.system_about_megacd,
            "sega32x" to R.string.system_about_sega32x,
            "saturn" to R.string.system_about_saturn,
            "dreamcast" to R.string.system_about_dreamcast,
            "psx" to R.string.system_about_psx,
            "ps2" to R.string.system_about_ps2,
            "psp" to R.string.system_about_psp,
            "psvita" to R.string.system_about_psvita,
            "pcengine" to R.string.system_about_pcengine,
            "neogeo" to R.string.system_about_neogeo,
            "ngpc" to R.string.system_about_ngpc,
            "wonderswan" to R.string.system_about_wonderswan,
            "atari2600" to R.string.system_about_atari2600,
            "atarilynx" to R.string.system_about_atarilynx,
            "arcade" to R.string.system_about_arcade,
            "mame" to R.string.system_about_mame,
            "fbneo" to R.string.system_about_fbneo,
        )

    /** The ids that have about text. */
    val ids: Set<String> get() = byId.keys

    /** The about string for system [id], or null. */
    @StringRes
    fun of(id: String): Int? = byId[id]

    /** Systems with no one maker: their facts line says so in place of the maker. */
    val manyMakerIds: Set<String> = setOf("arcade", "mame", "fbneo")
}
