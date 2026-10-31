package com.plumemu.platforms

// Expected launch specs, part B (n64, nds, megadrive, psx). How they were made: see ExpectedLaunches.kt.

internal val EXPECTED_B: List<ExpectedSystem> = listOf(
    system("n64", "z64") {
        // M64Plus FZ (Standalone): %EMULATOR_M64PLUS-FZ% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "m64plus-fz",
            "org.mupen64plusae.v3.fzurita.amazon",
            "paulscode.android.mupen64plusae.SplashActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Mupen64Plus AE (Standalone): %EMULATOR_MUPEN64PLUS-AE% %ACTION%=android.intent.action.VIEW
        // %DATA%=%ROMSAF%
        standalone(
            "mupen64plus-ae",
            "org.mupen64plusae.v3.alpha",
            "paulscode.android.mupen64plusae.SplashActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho n64.paulscode.android.mupen64plusae: '-n paulscode.android.mupen64plusae/.MainActivity\n -a
        // android.intent.action.VIEW\n -d {file.path}'
        standalone(
            "paulscode-android-mupen64plusae",
            "paulscode.android.mupen64plusae",
            "paulscode.android.mupen64plusae.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho n64.org.mupen64plusae.turnip.pwnedbygary: '-n
        // org.mupen64plusae.turnip.pwnedbygary/paulscode.android.mupen64plusae.SplashActivity\n -a
        // android.intent.action.VIEW\n -d {file.uri}'
        standalone(
            "org-mupen64plusae-turnip-pwnedbygary",
            "org.mupen64plusae.turnip.pwnedbygary",
            "paulscode.android.mupen64plusae.SplashActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho n64.io.github.gopher64.gopher64: '-n io.github.gopher64.gopher64/.SlintActivity\n -a
        // android.intent.action.VIEW\n -d {file.uri}'
        standalone(
            "io-github-gopher64-gopher64",
            "io.github.gopher64.gopher64",
            "io.github.gopher64.gopher64.SlintActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("mupen64plus_next_gles3_libretro_android.so")
        retroArch("parallel_n64_libretro_android.so")
        retroArch("mupen64plus_next_gles2_libretro_android.so")
    },
    system("nds", "nds") {
        // melonDS (Standalone): %EMULATOR_MELONDS% %ACTION%=me.magnum.melonds.LAUNCH_ROM %EXTRA_uri%=%ROMSAF%
        standalone(
            "melonds",
            "me.magnum.melonds",
            "me.magnum.melonds.ui.emulator.EmulatorActivity",
            action = "me.magnum.melonds.LAUNCH_ROM",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("uri" to ExtraValue.Text(URI)),
        )
        // melonDS Nightly (Standalone): %EMULATOR_MELONDS-NIGHTLY%
        // %ACTION%=me.magnum.melonds.nightly.LAUNCH_ROM %EXTRA_uri%=%ROMSAF%
        standalone(
            "melonds-nightly",
            "me.magnum.melonds.nightly",
            "me.magnum.melonds.ui.emulator.EmulatorActivity",
            action = "me.magnum.melonds.nightly.LAUNCH_ROM",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("uri" to ExtraValue.Text(URI)),
        )
        // WatermelonDS (Standalone): %EMULATOR_WATERMELONDS% %ACTION%=me.magnum.melonds.LAUNCH_ROM
        // %EXTRA_uri%=%ROMSAF%
        standalone(
            "watermelonds",
            "me.magnum.melondualds",
            "me.magnum.melonds.ui.emulator.EmulatorActivity",
            action = "me.magnum.melonds.LAUNCH_ROM",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("uri" to ExtraValue.Text(URI)),
        )
        // SeedlessDS (Standalone): %EMULATOR_SEEDLESSDS% %EXTRA_rom_path%=%ROM%
        standalone(
            "seedlessds",
            "com.seedlessds.app",
            "com.seedlessds.app.LaunchGame",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("rom_path" to ExtraValue.Text(PATH)),
        )
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
        // DraStic (Standalone): %EMULATOR_DRASTIC% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %DATA%=%ROMSAF%
        standalone(
            "drastic",
            "com.dsemu.drastic",
            "com.dsemu.drastic.DraSticActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = emptyMap(),
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
        // Daijisho nds.me.magnum.melonds.dev: '-a me.magnum.melonds.dev.LAUNCH_ROM\n-n
        // me.magnum.melonds.dev/me.magnum.melonds.ui.emulator.EmulatorActivity\n-e uri {file.uri}'
        standalone(
            "me-magnum-melonds-dev",
            "me.magnum.melonds.dev",
            "me.magnum.melonds.ui.emulator.EmulatorActivity",
            action = "me.magnum.melonds.dev.LAUNCH_ROM",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("uri" to ExtraValue.Text(URI)),
        )
        // Daijisho nds.com.pizzaemulators.pbds: '-n com.pizzaemulators.pbds/.MainActivity\n -e rom_uri
        // {file.uri}\n --activity-clear-task\n --activity-clear-top'
        standalone(
            "com-pizzaemulators-pbds",
            "com.pizzaemulators.pbds",
            "com.pizzaemulators.pbds.MainActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("rom_uri" to ExtraValue.Text(URI)),
        )
        retroArch("melondsds_libretro_android.so")
        retroArch("melonds_libretro_android.so")
        retroArch("desmume_libretro_android.so")
        retroArch("desmume2015_libretro_android.so")
        retroArch("noods_libretro_android.so")
        retroArch("skyemu_libretro_android.so")
    },
    system("megadrive", "md") {
        retroArch("genesis_plus_gx_libretro_android.so")
        // MD.emu (Standalone): %EMULATOR_MD-EMU% %DATA%=%ROMPROVIDER%
        standalone(
            "md-emu",
            "com.explusalpha.MdEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Pizza Boy SC (Standalone): %EMULATOR_PIZZA-BOY-SC% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRA_rom_uri%=%ROMSAF%
        standalone(
            "pizza-boy-sc",
            "it.dbtecno.pizzaboyscpro",
            "it.dbtecno.pizzaboyscpro.MainActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("rom_uri" to ExtraValue.Text(URI)),
        )
        // Daijisho genesis.com.androidemu.gens: '-n com.androidemu.gens/.EmulatorActivity\n -a
        // android.intent.action.VIEW\n -d {file.path}'
        standalone(
            "com-androidemu-gens",
            "com.androidemu.gens",
            "com.androidemu.gens.EmulatorActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = PATH,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("genesis_plus_gx_wide_libretro_android.so")
        retroArch("picodrive_libretro_android.so")
        retroArch("blastem_libretro_android.so")
        retroArch("clownmdemu_libretro_android.so")
    },
    system("psx", "cue") {
        // DuckStation (Standalone): %EMULATOR_DUCKSTATION% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %EXTRABOOL_resumeState%=false %EXTRA_bootPath%=%ROMSAF%
        standalone(
            "duckstation",
            "com.github.stenzek.duckstation",
            "com.github.stenzek.duckstation.EmulationActivity",
            action = null,
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("resumeState" to ExtraValue.Flag(false), "bootPath" to ExtraValue.Text(URI)),
        )
        // ARMSX1 (Standalone): %EMULATOR_ARMSX1% %DATA%=%ROMSAF%
        standalone(
            "armsx1",
            "com.nanodata.armsx",
            "com.armsx2.Main",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // ePSXe (Standalone): %EMULATOR_EPSXE% %ACTION%=android.intent.action.MAIN
        // %EXTRA_com.epsxe.ePSXe.isoName%=%ROM%
        standalone(
            "epsxe",
            "com.epsxe.ePSXe",
            "com.epsxe.ePSXe.ePSXe",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("com.epsxe.ePSXe.isoName" to ExtraValue.Text(PATH)),
        )
        // FPseNG (Standalone): %EMULATOR_FPSE-NG% %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%
        standalone(
            "fpse-ng",
            "com.emulator.fpse64",
            "com.emulator.fpse64.Main",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // FPse (Standalone): %EMULATOR_FPSE% %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%
        standalone(
            "fpse",
            "com.emulator.fpse",
            "com.emulator.fpse.Main",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho psx.com.sbro.emucorer: '-n com.sbro.emucorer/.MainActivity\n -a
        // com.sbro.emucorer.action.LAUNCH_GAME\n -d {file.uri}'
        standalone(
            "com-sbro-emucorer",
            "com.sbro.emucorer",
            "com.sbro.emucorer.MainActivity",
            action = "com.sbro.emucorer.action.LAUNCH_GAME",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("mednafen_psx_libretro_android.so")
        retroArch("mednafen_psx_hw_libretro_android.so")
        retroArch("pcsx_rearmed_libretro_android.so")
        retroArch("swanstation_libretro_android.so")
        retroArch("duckstation_libretro_android.so")
        retroArch("goosestation_libretro_android.so")
    },
)
