package com.plumemu.achievements.match

import com.plumemu.achievements.hash.RaConsoles

/**
 * Plumemu's system ids (the `id` of each system in the platforms module's `platforms.json`) to
 * RetroAchievements console ids ([RaConsoles]). A system absent here has no achievements in
 * Plumemu: the matcher answers [RaMatch.NotSupported] without reading the file.
 *
 * Several Plumemu systems share one RetroAchievements console when RetroAchievements lists their
 * games together: Neo Geo Pocket and its Color model, WonderSwan and Color, MSX1 and MSX2, PC
 * Engine and SuperGrafx, the two names of PC Engine CD, Odyssey 2 and Videopac, and the Arcade
 * systems whose sets RetroAchievements hashes by file name (FinalBurn and MAME folders, CPS, Neo
 * Geo). Game Boy covers Super Game Boy; Super Nintendo covers its add-ons and MSU-1 hacks, which
 * are SNES images; Mega Drive covers its MSU hacks.
 *
 * Kept as plain strings so this module does not depend on the platforms module; a test reads
 * `platforms.json` and checks every id here exists there.
 */
public object RaSystems {
    private val consoleBySystem: Map<String, Int> = mapOf(
        // Nintendo
        "nes" to RaConsoles.NINTENDO,
        "fds" to RaConsoles.FAMICOM_DISK_SYSTEM,
        "snes" to RaConsoles.SUPER_NINTENDO,
        "satellaview" to RaConsoles.SUPER_NINTENDO,
        "sufami" to RaConsoles.SUPER_NINTENDO,
        "snesmsu1" to RaConsoles.SUPER_NINTENDO,
        "n64" to RaConsoles.NINTENDO_64,
        "gb" to RaConsoles.GAMEBOY,
        "sgb" to RaConsoles.GAMEBOY,
        "gbc" to RaConsoles.GAMEBOY_COLOR,
        "gba" to RaConsoles.GAMEBOY_ADVANCE,
        "nds" to RaConsoles.NINTENDO_DS,
        "ndsi" to RaConsoles.NINTENDO_DSI,
        "n3ds" to RaConsoles.NINTENDO_3DS,
        "virtualboy" to RaConsoles.VIRTUAL_BOY,
        "pokemini" to RaConsoles.POKEMON_MINI,
        "gc" to RaConsoles.GAMECUBE,
        "wii" to RaConsoles.WII,
        // Sega
        "sg-1000" to RaConsoles.SG1000,
        "mastersystem" to RaConsoles.MASTER_SYSTEM,
        "gamegear" to RaConsoles.GAME_GEAR,
        "megadrive" to RaConsoles.MEGA_DRIVE,
        "genesismsu" to RaConsoles.MEGA_DRIVE,
        "sega32x" to RaConsoles.SEGA_32X,
        "megacd" to RaConsoles.SEGA_CD,
        "saturn" to RaConsoles.SATURN,
        "dreamcast" to RaConsoles.DREAMCAST,
        // Sony
        "psx" to RaConsoles.PLAYSTATION,
        "ps2" to RaConsoles.PLAYSTATION_2,
        "ps3" to RaConsoles.PLAYSTATION_3,
        "psp" to RaConsoles.PSP,
        "pspminis" to RaConsoles.PSP,
        // NEC
        "pcengine" to RaConsoles.PC_ENGINE,
        "supergrafx" to RaConsoles.PC_ENGINE,
        "pcenginecd" to RaConsoles.PC_ENGINE_CD,
        "tg-cd" to RaConsoles.PC_ENGINE_CD,
        "pcfx" to RaConsoles.PCFX,
        "pc88" to RaConsoles.PC8800,
        // Atari
        "atari2600" to RaConsoles.ATARI_2600,
        "atari7800" to RaConsoles.ATARI_7800,
        "atarilynx" to RaConsoles.ATARI_LYNX,
        "atarijaguar" to RaConsoles.ATARI_JAGUAR,
        "atarijaguarcd" to RaConsoles.ATARI_JAGUAR_CD,
        // SNK
        "ngp" to RaConsoles.NEOGEO_POCKET,
        "ngpc" to RaConsoles.NEOGEO_POCKET,
        "neogeocd" to RaConsoles.NEO_GEO_CD,
        // Arcade (hashed by file name)
        "arcade" to RaConsoles.ARCADE,
        "fbneo" to RaConsoles.ARCADE,
        "fba" to RaConsoles.ARCADE,
        "mame" to RaConsoles.ARCADE,
        "neogeo" to RaConsoles.ARCADE,
        "cps" to RaConsoles.ARCADE,
        "cps1" to RaConsoles.ARCADE,
        "cps2" to RaConsoles.ARCADE,
        "cps3" to RaConsoles.ARCADE,
        // Other consoles and handhelds
        "3do" to RaConsoles.THREE_DO,
        "colecovision" to RaConsoles.COLECOVISION,
        "intellivision" to RaConsoles.INTELLIVISION,
        "vectrex" to RaConsoles.VECTREX,
        "odyssey2" to RaConsoles.MAGNAVOX_ODYSSEY2,
        "videopac" to RaConsoles.MAGNAVOX_ODYSSEY2,
        "channelf" to RaConsoles.FAIRCHILD_CHANNEL_F,
        "arcadia" to RaConsoles.ARCADIA_2001,
        "vc4000" to RaConsoles.INTERTON_VC_4000,
        "wonderswan" to RaConsoles.WONDERSWAN,
        "wonderswancolor" to RaConsoles.WONDERSWAN,
        "supervision" to RaConsoles.SUPERVISION,
        "megaduck" to RaConsoles.MEGADUCK,
        "arduboy" to RaConsoles.ARDUBOY,
        "uzebox" to RaConsoles.UZEBOX,
        "tic80" to RaConsoles.TIC80,
        "wasm4" to RaConsoles.WASM4,
        // Computers
        "msx" to RaConsoles.MSX,
        "msx1" to RaConsoles.MSX,
        "msx2" to RaConsoles.MSX,
        "c64" to RaConsoles.COMMODORE_64,
        "amstradcpc" to RaConsoles.AMSTRAD_PC,
        "apple2" to RaConsoles.APPLE_II,
        "zxspectrum" to RaConsoles.ZX_SPECTRUM,
        "dos" to RaConsoles.MS_DOS,
    )

    /** The RetroAchievements console of Plumemu system [systemId]; null when RetroAchievements has none for it. */
    public fun consoleOf(systemId: String): Int? = consoleBySystem[systemId]

    /** Every Plumemu system id with a RetroAchievements console. */
    public val systemIds: Set<String> get() = consoleBySystem.keys
}
