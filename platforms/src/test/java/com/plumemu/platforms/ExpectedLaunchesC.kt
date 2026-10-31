package com.plumemu.platforms

// Expected launch specs, part C (psp, ps2, gc, arcade). How they were made: see ExpectedLaunches.kt.

internal val EXPECTED_C: List<ExpectedSystem> = listOf(
    system("psp", "iso") {
        // PPSSPP (Standalone): %EMULATOR_PPSSPP% %ACTION%=android.intent.action.VIEW
        // %CATEGORY%=android.intent.category.DEFAULT %DATA%=%ROMSAF%
        standalone(
            "ppsspp",
            "org.ppsspp.ppssppgold",
            "org.ppsspp.ppsspp.PpssppActivity",
            action = "android.intent.action.VIEW",
            category = "android.intent.category.DEFAULT",
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho psp.org.ppsspp.ppsspplegacy: '-n org.ppsspp.ppsspplegacy/org.ppsspp.ppsspp.PpssppActivity\n
        // -a android.intent.action.VIEW\n -c android.intent.category.DEFAULT\n -d {file.uri}\n -t
        // application/octet-stream\n --activity-clear-task  --activity-clear-top  --activity-no-history'
        standalone(
            "org-ppsspp-ppsspplegacy",
            "org.ppsspp.ppsspplegacy",
            "org.ppsspp.ppsspp.PpssppActivity",
            action = "android.intent.action.VIEW",
            category = "android.intent.category.DEFAULT",
            data = URI,
            mime = "application/octet-stream",
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop, LaunchFlag.NoHistory),
            extras = emptyMap(),
        )
        // Daijisho psp.com.sbro.emucorea: '-n com.sbro.emucorea/.MainActivity\n -a
        // com.sbro.emucorea.extra.GAME_PATH\n -d {file.uri}'
        standalone(
            "com-sbro-emucorea",
            "com.sbro.emucorea",
            "com.sbro.emucorea.MainActivity",
            action = "com.sbro.emucorea.extra.GAME_PATH",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("ppsspp_libretro_android.so")
    },
    system("ps2", "iso") {
        // NetherSX2 (Standalone): %EMULATOR_NETHERSX2% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP%
        // %ACTION%=android.intent.action.MAIN %EXTRA_bootPath%=%ROMSAF%
        standalone(
            "nethersx2",
            "xyz.aethersx2.android",
            "xyz.aethersx2.android.EmulationActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("bootPath" to ExtraValue.Text(URI)),
        )
        // NetherSX2-Turnip (Standalone): %EMULATOR_NETHERSX2-TURNIP% %ACTIVITY_CLEAR_TASK%
        // %ACTIVITY_CLEAR_TOP% %ACTION%=android.intent.action.MAIN %EXTRA_bootPath%=%ROMSAF%
        standalone(
            "nethersx2-turnip",
            "xyz.aethersx2.tturnip",
            "xyz.aethersx2.android.EmulationActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("bootPath" to ExtraValue.Text(URI)),
        )
        // NetherSX2-Turnip Classic (Standalone): %EMULATOR_NETHERSX2-TURNIP-CLASSIC% %ACTIVITY_CLEAR_TASK%
        // %ACTIVITY_CLEAR_TOP% %ACTION%=android.intent.action.MAIN %EXTRA_bootPath%=%ROMSAF%
        standalone(
            "nethersx2-turnip-classic",
            "xyz.aethersx2.cturnip",
            "xyz.aethersx2.android.EmulationActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("bootPath" to ExtraValue.Text(URI)),
        )
        // ARMSX2 (Standalone): %EMULATOR_ARMSX2% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "armsx2",
            "come.nanodata.armsx2",
            "com.armsx2.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // EmuCoreX (Standalone): %EMULATOR_EMUCOREX% %ACTIVITY_CLEAR_TASK% %ACTION%=android.intent.action.VIEW
        // %DATA%=%ROMSAF%
        standalone(
            "emucorex",
            "com.sbro.emucorex",
            "com.sbro.emucorex.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask),
            extras = emptyMap(),
        )
        // Play! (Standalone): %EMULATOR_PLAY!% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "play",
            "com.virtualapplications.play",
            "com.virtualapplications.play.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Daijisho ps2.xyz.aethersx2.custom: '-n xyz.aethersx2.custom/.EmulationActivity\n -a
        // android.intent.action.MAIN\n -e bootPath {file.uri}\n --activity-clear-task\n
        // --activity-clear-top\n'
        standalone(
            "xyz-aethersx2-custom",
            "xyz.aethersx2.custom",
            "xyz.aethersx2.custom.EmulationActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(LaunchFlag.ClearTask, LaunchFlag.ClearTop),
            extras = mapOf("bootPath" to ExtraValue.Text(URI)),
        )
        retroArch("pcee2_libretro_android.so")
        retroArch("pcsx2_libretro_android.so")
        retroArch("play_libretro_android.so")
        retroArch("armsx2_libretro_android.so")
    },
    system("gc", "iso") {
        // Dolphin (Standalone): %EMULATOR_DOLPHIN% %ACTION%=android.intent.action.MAIN
        // %CATEGORY%=android.intent.category.LEANBACK_LAUNCHER %EXTRA_AutoStartFile%=%ROMSAF%
        standalone(
            "dolphin",
            "org.dolphinemu.dolphinemu",
            "org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
            action = "android.intent.action.MAIN",
            category = "android.intent.category.LEANBACK_LAUNCHER",
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Dolphin MMJR (Standalone): %EMULATOR_DOLPHIN-MMJR% %ACTION%=android.intent.action.VIEW
        // %EXTRA_AutoStartFile%=%ROMSAF%
        standalone(
            "dolphin-mmjr",
            "org.mm.jr",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Dolphin MMJR2 (Standalone): %EMULATOR_DOLPHIN-MMJR2% %ACTION%=android.intent.action.VIEW
        // %EXTRA_AutoStartFile%=%ROMSAF%
        standalone(
            "dolphin-mmjr2",
            "org.dolphinemu.mmjr",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Daijisho gc.org.dolphinemu.handheld: '-n
        // org.dolphinemu.handheld/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.uri}'
        standalone(
            "org-dolphinemu-handheld",
            "org.dolphinemu.handheld",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Daijisho gc.org.mm.j: '-n org.mm.j/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.path}'
        standalone(
            "org-mm-j",
            "org.mm.j",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(PATH)),
        )
        // Daijisho gc.org.dolphinemu.mmjr3: '-n
        // org.dolphinemu.mmjr3/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.uri}'
        standalone(
            "org-dolphinemu-mmjr3",
            "org.dolphinemu.mmjr3",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Daijisho gc.org.dolphin.ishiirukadark: '-n
        // org.dolphin.ishiirukadark/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.path}'
        standalone(
            "org-dolphin-ishiirukadark",
            "org.dolphin.ishiirukadark",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(PATH)),
        )
        // Daijisho gc.org.dolphinemu.primehack: '-n
        // org.dolphinemu.primehack/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.path}'
        standalone(
            "org-dolphinemu-primehack",
            "org.dolphinemu.primehack",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(PATH)),
        )
        // Daijisho gc.org.shiiion.primehack: '-n
        // org.shiiion.primehack/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.VIEW\n -e AutoStartFile {file.path}'
        standalone(
            "org-shiiion-primehack",
            "org.shiiion.primehack",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(PATH)),
        )
        // Daijisho gc.org.dolphinemu.dolphinemu.debug: '-n
        // org.dolphinemu.dolphinemu.debug/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.MAIN\n -e AutoStartFile {file.uri}'
        standalone(
            "org-dolphinemu-dolphinemu-debug",
            "org.dolphinemu.dolphinemu.debug",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        // Daijisho gc.com.joeyos.dolphinemu: '-n
        // com.joeyos.dolphinemu/org.dolphinemu.dolphinemu.ui.main.MainActivity\n -a
        // android.intent.action.MAIN\n -e AutoStartFile {file.uri}'
        standalone(
            "com-joeyos-dolphinemu",
            "com.joeyos.dolphinemu",
            "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            action = "android.intent.action.MAIN",
            category = null,
            data = null,
            mime = null,
            flags = setOf(),
            extras = mapOf("AutoStartFile" to ExtraValue.Text(URI)),
        )
        retroArch("dolphin_libretro_android.so")
    },
    system("arcade", "zip") {
        retroArch("mamearcade_libretro_android.so")
        // MAME4droid (Standalone): %EMULATOR_MAME4DROID% %ACTION%=android.intent.action.VIEW
        // %DATA%=%ROMPROVIDER%
        standalone(
            "mame4droid",
            "com.seleuco.mame4droid",
            "com.seleuco.mame4droid.MAME4droid",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // NEO.emu (Standalone): %EMULATOR_NEO-EMU% %DATA%=%ROMSAF%
        standalone(
            "neo-emu",
            "com.explusalpha.NeoEmu",
            "com.imagine.BaseActivity",
            action = null,
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // Flycast (Standalone): %EMULATOR_FLYCAST% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "flycast",
            "com.flycast.emulator",
            "com.flycast.emulator.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        // SUPER3 (Standalone): %EMULATOR_SUPER3% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%
        standalone(
            "super3",
            "com.izzy2lost.super3",
            "com.izzy2lost.super3.MainActivity",
            action = "android.intent.action.VIEW",
            category = null,
            data = URI,
            mime = null,
            flags = setOf(),
            extras = emptyMap(),
        )
        retroArch("mame2010_libretro_android.so")
        retroArch("mame2003_plus_libretro_android.so")
        retroArch("mame2003_libretro_android.so")
        retroArch("mame2000_libretro_android.so")
        retroArch("hbmame_libretro_android.so")
        retroArch("fbneo_libretro_android.so")
        retroArch("fbalpha2012_libretro_android.so")
        retroArch("geolith_libretro_android.so")
        retroArch("flycast_libretro_android.so")
        retroArch("dice_libretro_android.so")
        retroArch("supermodel_libretro_android.so")
    },
)
