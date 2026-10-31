package com.plumemu.scraper.fallback

/**
 * Plumemu system id to the folder libretro's thumbnail server keeps that system's pictures in
 * (RetroArch's playlist names, which are the No-Intro and Redump system names). Facts only. A system
 * not listed here has no libretro box art, or it is not a retro system (Switch, Steam, Android,
 * Windows): [LibretroArt] asks nothing for it. The ids are checked against `platforms.json` by
 * `LibretroSystemsTest`.
 */
internal object LibretroSystems {
    fun playlistOf(systemId: String): String? = PLAYLISTS[systemId]

    val ids: Set<String> get() = PLAYLISTS.keys

    val playlists: Collection<String> get() = PLAYLISTS.values

    private val PLAYLISTS = mapOf(
        "nes" to "Nintendo - Nintendo Entertainment System",
        "fds" to "Nintendo - Family Computer Disk System",
        "snes" to "Nintendo - Super Nintendo Entertainment System",
        "satellaview" to "Nintendo - Satellaview",
        "sufami" to "Nintendo - Sufami Turbo",
        "gb" to "Nintendo - Game Boy",
        "gbc" to "Nintendo - Game Boy Color",
        "gba" to "Nintendo - Game Boy Advance",
        "n64" to "Nintendo - Nintendo 64",
        "n64dd" to "Nintendo - Nintendo 64DD",
        "nds" to "Nintendo - Nintendo DS",
        "ndsi" to "Nintendo - Nintendo DSi",
        "n3ds" to "Nintendo - Nintendo 3DS",
        "virtualboy" to "Nintendo - Virtual Boy",
        "pokemini" to "Nintendo - Pokemon Mini",
        "gc" to "Nintendo - GameCube",
        "wii" to "Nintendo - Wii",
        "wiiu" to "Nintendo - Wii U",
        "megadrive" to "Sega - Mega Drive - Genesis",
        "sega32x" to "Sega - 32X",
        "mastersystem" to "Sega - Master System - Mark III",
        "gamegear" to "Sega - Game Gear",
        "sg-1000" to "Sega - SG-1000",
        "pico" to "Sega - PICO",
        "saturn" to "Sega - Saturn",
        "dreamcast" to "Sega - Dreamcast",
        "megacd" to "Sega - Mega-CD - Sega CD",
        "pcengine" to "NEC - PC Engine - TurboGrafx 16",
        "supergrafx" to "NEC - PC Engine SuperGrafx",
        "pcenginecd" to "NEC - PC Engine CD - TurboGrafx-CD",
        "pcfx" to "NEC - PC-FX",
        "ngp" to "SNK - Neo Geo Pocket",
        "ngpc" to "SNK - Neo Geo Pocket Color",
        "neogeocd" to "SNK - Neo Geo CD",
        "wonderswan" to "Bandai - WonderSwan",
        "wonderswancolor" to "Bandai - WonderSwan Color",
        "atari2600" to "Atari - 2600",
        "atari5200" to "Atari - 5200",
        "atari7800" to "Atari - 7800",
        "atarilynx" to "Atari - Lynx",
        "atarijaguar" to "Atari - Jaguar",
        "atarist" to "Atari - ST",
        "colecovision" to "Coleco - ColecoVision",
        "intellivision" to "Mattel - Intellivision",
        "vectrex" to "GCE - Vectrex",
        "channelf" to "Fairchild - Channel F",
        "odyssey2" to "Magnavox - Odyssey2",
        "supervision" to "Watara - Supervision",
        "msx" to "Microsoft - MSX",
        "msx2" to "Microsoft - MSX2",
        "psx" to "Sony - PlayStation",
        "ps2" to "Sony - PlayStation 2",
        "ps3" to "Sony - PlayStation 3",
        "psp" to "Sony - PlayStation Portable",
        "psvita" to "Sony - PlayStation Vita",
        "xbox" to "Microsoft - Xbox",
        "xbox360" to "Microsoft - Xbox 360",
        "3do" to "The 3DO Company - 3DO",
        "cdimono1" to "Philips - CD-i",
        "c64" to "Commodore - 64",
        "amiga" to "Commodore - Amiga",
        "x68000" to "Sharp - X68000",
    )
}
