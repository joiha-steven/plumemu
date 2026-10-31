package com.plumemu.platforms

// Expected launch specs, part A (nes, snes, gb, gbc, gba). How they were made: see ExpectedLaunches.kt.

internal val EXPECTED_A: List<ExpectedSystem> = listOf(
    system("nes", "nes") {
        retroArch("mesen_libretro_android.so")
        // NES.emu (Standalone): %EMULATOR_NES-EMU% %DATA%=%ROMPROVIDER%
        standalone(
            "nes-emu",
            "com.explusalpha.NesEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // iNES (Standalone): %EMULATOR_INES% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "ines",
            "com.fms.ines.free",
            "com.fms.emulib.TVActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = emptyMap(),
        )
        // Nesoid (Standalone): %EMULATOR_NESOID% %ACTION%=android.intent.action.VIEW %DATA%=%ROM%
        standalone(
            "nesoid",
            "com.androidemu.nes",
            "com.androidemu.nes.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("mesen2_libretro_android.so")
        retroArch("nestopia_libretro_android.so")
        retroArch("fceumm_libretro_android.so")
        retroArch("quicknes_libretro_android.so")
        retroArch("rustynes_libretro_android.so")
        retroArch("bnes_libretro_android.so")
    },
    system("snes", "sfc") {
        retroArch("snes9x_libretro_android.so")
        // Snes9x EX+ (Standalone): %EMULATOR_SNES9X-EXPLUS% %DATA%=%ROMSAF%
        standalone(
            "snes9x-explus",
            "com.explusalpha.Snes9xPlus",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho snes.com.zsnes.superzsnes: '-n
        // com.zsnes.superzsnes/com.unity3d.player.UnityPlayerActivity\n-d {file.uri}'
        standalone(
            "com-zsnes-superzsnes",
            "com.zsnes.superzsnes",
            "com.unity3d.player.UnityPlayerActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("snes9x2010_libretro_android.so")
        retroArch("snes9x2005_plus_libretro_android.so")
        retroArch("bsnes_libretro_android.so")
        retroArch("bsnes_hd_beta_libretro_android.so")
        retroArch("bsnes-jg_libretro_android.so")
        retroArch("bsnes_mercury_accuracy_libretro_android.so")
        retroArch("mednafen_supafaust_libretro_android.so")
        retroArch("mesen-s_libretro_android.so")
        retroArch("mesen2_libretro_android.so")
        retroArch("higan_sfc_libretro_android.so")
        retroArch("higan_sfc_balanced_libretro_android.so")
        retroArch("mednafen_snes_libretro_android.so")
        retroArch("snes9x2002_libretro_android.so")
        retroArch("snes9x2005_libretro_android.so")
        retroArch("bsnes2014_accuracy_libretro_android.so")
        retroArch("bsnes2014_balanced_libretro_android.so")
        retroArch("bsnes2014_performance_libretro_android.so")
        retroArch("bsnes_cplusplus98_libretro_android.so")
        retroArch("bsnes_mercury_balanced_libretro_android.so")
        retroArch("bsnes_mercury_performance_libretro_android.so")
    },
    system("gb", "gb") {
        retroArch("gambatte_libretro_android.so")
        // SkyEmu (Standalone): %EMULATOR_SKYEMU% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%
        standalone(
            "skyemu",
            "com.sky.SkyEmu",
            "com.sky.SkyEmu.EnhancedNativeActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = emptyMap(),
        )
        // GBC.emu (Standalone): %EMULATOR_GBC-EMU% %DATA%=%ROMPROVIDER%
        standalone(
            "gbc-emu",
            "com.explusalpha.GbcEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // My OldBoy! (Standalone): %EMULATOR_MY-OLDBOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "my-oldboy",
            "com.fastemulator.gbc",
            "com.fastemulator.gbc.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Linkboy (Standalone): %EMULATOR_LINKBOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "linkboy",
            "com.pixelrespawn.linkboy",
            "com.pixelrespawn.linkboy.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Pizza Boy GBC (Standalone): %EMULATOR_PIZZA-BOY-GBC% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRA_rom_uri%=%ROMSAF%
        standalone(
            "pizza-boy-gbc",
            "it.dbtecno.pizzaboypro",
            "it.dbtecno.pizzaboypro.MainActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("rom_uri" to ExtraValue.Text(URI)),
        )
        // Daijisho gb.com.fastemulator.gbcfree: '-n com.fastemulator.gbcfree/.EmulatorActivity\n -a
        // android.intent.action.VIEW\n -d {file.path}'
        standalone(
            "com-fastemulator-gbcfree",
            "com.fastemulator.gbcfree",
            "com.fastemulator.gbcfree.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("sameboy_libretro_android.so")
        retroArch("gearboy_libretro_android.so")
        retroArch("tgbdual_libretro_android.so")
        retroArch("DoubleCherryGB_libretro_android.so")
        retroArch("irogb_libretro_android.so")
        retroArch("mesen-s_libretro_android.so")
        retroArch("mesen2_libretro_android.so")
        retroArch("bsnes_libretro_android.so")
        retroArch("mgba_libretro_android.so")
        retroArch("vbam_libretro_android.so")
        retroArch("skyemu_libretro_android.so")
    },
    system("gbc", "gbc") {
        retroArch("gambatte_libretro_android.so")
        // SkyEmu (Standalone): %EMULATOR_SKYEMU% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%
        standalone(
            "skyemu",
            "com.sky.SkyEmu",
            "com.sky.SkyEmu.EnhancedNativeActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = emptyMap(),
        )
        // GBC.emu (Standalone): %EMULATOR_GBC-EMU% %DATA%=%ROMPROVIDER%
        standalone(
            "gbc-emu",
            "com.explusalpha.GbcEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // My OldBoy! (Standalone): %EMULATOR_MY-OLDBOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "my-oldboy",
            "com.fastemulator.gbc",
            "com.fastemulator.gbc.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Linkboy (Standalone): %EMULATOR_LINKBOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "linkboy",
            "com.pixelrespawn.linkboy",
            "com.pixelrespawn.linkboy.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Pizza Boy GBC (Standalone): %EMULATOR_PIZZA-BOY-GBC% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRA_rom_uri%=%ROMSAF%
        standalone(
            "pizza-boy-gbc",
            "it.dbtecno.pizzaboypro",
            "it.dbtecno.pizzaboypro.MainActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("rom_uri" to ExtraValue.Text(URI)),
        )
        // Daijisho gbc.com.fastemulator.gbcfree: '-n com.fastemulator.gbcfree/.EmulatorActivity\n -a
        // android.intent.action.VIEW\n -d {file.path}'
        standalone(
            "com-fastemulator-gbcfree",
            "com.fastemulator.gbcfree",
            "com.fastemulator.gbcfree.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("sameboy_libretro_android.so")
        retroArch("gearboy_libretro_android.so")
        retroArch("tgbdual_libretro_android.so")
        retroArch("DoubleCherryGB_libretro_android.so")
        retroArch("irogb_libretro_android.so")
        retroArch("mesen-s_libretro_android.so")
        retroArch("mesen2_libretro_android.so")
        retroArch("bsnes_libretro_android.so")
        retroArch("mgba_libretro_android.so")
        retroArch("vbam_libretro_android.so")
        retroArch("skyemu_libretro_android.so")
    },
    system("gba", "gba") {
        retroArch("mgba_libretro_android.so")
        // NooDS (Standalone): %EMULATOR_NOODS% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRA_LaunchPath%=%ROM%
        standalone(
            "noods",
            "com.hydra.noods",
            "com.hydra.noods.FileBrowser",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("LaunchPath" to ExtraValue.Text(PATH)),
        )
        // SkyEmu (Standalone): %EMULATOR_SKYEMU% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%
        standalone(
            "skyemu",
            "com.sky.SkyEmu",
            "com.sky.SkyEmu.EnhancedNativeActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = emptyMap(),
        )
        // GBA.emu (Standalone): %EMULATOR_GBA-EMU% %DATA%=%ROMPROVIDER%
        standalone(
            "gba-emu",
            "com.explusalpha.GbaEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // My Boy! (Standalone): %EMULATOR_MY-BOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "my-boy",
            "com.fastemulator.gba",
            "com.fastemulator.gba.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Linkboy (Standalone): %EMULATOR_LINKBOY% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "linkboy",
            "com.pixelrespawn.linkboy",
            "com.pixelrespawn.linkboy.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Pizza Boy GBA (Standalone): %EMULATOR_PIZZA-BOY-GBA% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRA_rom_uri%=%ROMSAF%
        standalone(
            "pizza-boy-gba",
            "it.dbtecno.pizzaboygbapro",
            "it.dbtecno.pizzaboygbapro.MainActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("rom_uri" to ExtraValue.Text(URI)),
        )
        // Daijisho gba.com.fastemulator.gbafree: '-n
        // com.fastemulator.gbafree/com.fastemulator.gba.EmulatorActivity\n -a android.intent.action.VIEW\n -d
        // {file.path}'
        standalone(
            "com-fastemulator-gbafree",
            "com.fastemulator.gbafree",
            "com.fastemulator.gba.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("vbam_libretro_android.so")
        retroArch("vba_next_libretro_android.so")
        retroArch("gpsp_libretro_android.so")
        retroArch("noods_libretro_android.so")
        retroArch("skyemu_libretro_android.so")
        retroArch("mesen2_libretro_android.so")
        retroArch("mednafen_gba_libretro_android.so")
        retroArch("meteor_libretro_android.so")
    },
)
