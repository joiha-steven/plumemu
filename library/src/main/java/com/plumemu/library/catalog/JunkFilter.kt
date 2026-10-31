package com.plumemu.library.catalog

import java.util.Locale

/** Files and folders a scan sets aside before matching: hidden, BIOS, saves, notes, images. */
internal object JunkFilter {
    private val skippedDirectories = setOf(
        "bios", "firmware", "media", "images", "videos", "manuals", "downloaded_media", "downloaded_images",
    )

    private val junkExtensions = setOf(
        "txt", "nfo", "diz", "pdf", "htm", "html", "url", "log", "ini", "cfg", "xml", "json",
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "mp4", "mkv",
        "srm", "sav", "sv", "state", "auto", "oops", "rtc", "bak", "tmp", "db",
        "sfv", "md5", "sha1", "crc",
    )
    private val saveStateExtension = Regex("""(state|st)\d+""")

    /** Documents that come with games or emulators, whatever the extension (`README.md` is not a Mega Drive game). */
    private val documentNames = setOf("readme", "license", "licence", "changelog", "contributing", "notes")

    private val biosFileNames = setOf(
        "gba_bios.bin", "gb_bios.bin", "gbc_bios.bin", "sgb_bios.bin", "bios7.bin", "bios9.bin",
        "firmware.bin", "dsi_bios7.bin", "dsi_bios9.bin", "dsi_firmware.bin", "disksys.rom",
        "syscard1.pce", "syscard2.pce", "syscard3.pce", "gexpress.pce",
        "neogeo.zip", "pgm.zip", "skns.zip", "naomi.zip", "awbios.zip", "qsound.zip", "decocass.zip",
        "dc_boot.bin", "dc_flash.bin", "sega_101.bin", "neocd.bin", "lynxboot.img", "5200.rom",
        "7800 bios (u).rom", "7800 bios (e).rom", "kick34005.a500", "kick40068.a1200", "bios.gg",
        "bios_cd_e.bin", "bios_cd_j.bin", "bios_cd_u.bin", "bios_md.bin", "bios_e.sms", "bios_j.sms",
        "bios_u.sms", "sys_rom.bin", "bios.min", "rom1.bin", "ps1_rom.bin", "psxonpsp660.bin",
    )
    private val biosFilePatterns = listOf(
        Regex("""scph[-_ ]?\d{4,5}[a-z]?(?:\s*[(\[][^()\[\]]*[)\]])*\.bin"""),
        Regex("""openbios(?:[-_.]\w+)*\.bin"""),
        Regex("""kick[-_ ]?\d+.*\.rom"""),
        Regex("""ps[12]?[-_ ]?bios.*\.bin"""),
        Regex("""mpr-\d+[a-z]?\.(bin|ic\d+)"""),
        Regex("""panafz\d+.*\.bin"""),
        Regex("""ps2-\d+.*\.bin"""),
    )

    fun isSkippedDirectory(name: String): Boolean =
        name.startsWith(".") || name.lowercase(Locale.ROOT) in skippedDirectories

    fun isJunkFile(name: String): Boolean {
        if (name.startsWith(".")) return true
        val lower = name.lowercase(Locale.ROOT)
        if (lower == "thumbs.db" || lower == "desktop.ini") return true
        val extension = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (extension in junkExtensions || saveStateExtension.matches(extension)) return true
        if (lower.substringBefore('.') in documentNames) return true
        return isBios(lower)
    }

    private fun isBios(lowerName: String): Boolean =
        lowerName in biosFileNames || biosFilePatterns.any { it.matches(lowerName) }
}
