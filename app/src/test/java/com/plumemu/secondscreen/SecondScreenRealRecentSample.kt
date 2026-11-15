package com.plumemu.secondscreen

/** Recent as the library builds it: captions through [coverCaptionOf], with tall PSP and Saturn boxes beside the wider ones. */
internal fun realRecent(): RecentEntry {
    fun look(name: String, short: String, aspect: Float) = EntrySystemLook(name, short, aspect, coverCaptionOf(name, short))
    val looks =
        mapOf(
            "snes" to look("Super Nintendo", "SNES", 1.4f), "psp" to look("PSP", "PSP", 0.575f), "saturn" to look("Sega Saturn", "SAT", 0.65f),
            "md" to look("Mega Drive", "MD", 0.71f), "gba" to look("Game Boy Advance", "GBA", 1.0f),
        )
    val h = 3_600_000L
    val games =
        listOf(
            entryGame(1, "Super Metroid", "snes", 2 * h), entryGame(2, "Persona 3 Portable", "psp", 3 * h), entryGame(3, "Panzer Dragoon", "saturn", 26 * h),
            entryGame(4, "Sonic", "md", 50 * h), entryGame(5, "Metroid Fusion", "gba", 80 * h),
        )
    return BOARD_RECENT.copy(systems = looks, figures = BOARD_RECENT.figures!!.copy(games = games))
}
