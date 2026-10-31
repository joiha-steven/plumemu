package com.plumemu.library

import com.plumemu.library.matching.SystemRule

/** A handful of hand-written rules; the real ones come from the platform data. */
internal object TestRules {
    val SNES = SystemRule("snes", setOf("snes", "sfc", "superfamicom", "Super Nintendo"), setOf("sfc", "smc", "zip"), emulators = setOf("kin"))
    val PSX = SystemRule("psx", setOf("psx", "ps1", "playstation"), setOf("cue", "bin", "chd", "m3u", "iso", "pbp"))
    val GBA = SystemRule("gba", setOf("gba"), setOf("gba", "zip"), emulators = setOf("kin"))
    val MEGADRIVE = SystemRule("megadrive", setOf("megadrive", "genesis", "md"), setOf("md", "gen", "bin", "zip"))
    val PS2 = SystemRule("ps2", setOf("ps2"), setOf("iso", "chd"))
    val ARCADE = SystemRule("arcade", setOf("arcade", "mame"), setOf("zip"))
    val FBNEO = SystemRule("fbneo", setOf("arcade", "fbneo"), setOf("zip", "7z"))

    val ALL = listOf(SNES, PSX, GBA, MEGADRIVE, PS2, ARCADE, FBNEO)

    /** Not in [ALL]: only the Switch naming test adds it. */
    val SWITCH = SystemRule("switch", setOf("switch"), setOf("nsp", "nsz", "xci"))
}
