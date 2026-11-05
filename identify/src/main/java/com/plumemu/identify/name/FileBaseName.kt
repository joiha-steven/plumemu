package com.plumemu.identify.name

/** A file name without its folders and extensions. */
internal object FileBaseName {
    /**
     * [fileName] without folders and its extension: a short one of letters and digits with at least
     * one letter ("Tool v1.0" has none: the ".0" is part of the name), and a second one of a game
     * file or archive ("Game v1.0.nkit.iso").
     */
    fun of(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || !isExtension(base.substring(dot + 1))) return base
        val once = base.substring(0, dot)
        val inner = once.lastIndexOf('.')
        return if (inner > 0 && once.substring(inner + 1).lowercase() in INNER_EXTENSIONS) once.substring(0, inner) else once
    }

    private fun isExtension(text: String): Boolean =
        text.length in 1..5 && text.all { it.isLetterOrDigit() } && text.any { it.isLetter() }

    /** Extensions that can sit before the last one (`.nkit.iso`, `.gba.zip`). */
    private val INNER_EXTENSIONS = setOf(
        "nkit", "iso", "bin", "img", "cue", "chd", "rvz", "wbfs", "gcm", "gcz", "3ds", "cia", "nsp", "xci", "nes", "sfc", "smc",
        "gb", "gbc", "gba", "nds", "n64", "z64", "v64", "md", "gen", "sms", "gg", "pce", "a26", "a78", "lnx", "ws", "wsc",
        "ngp", "ngc", "col", "sg", "rom", "tar",
    )
}
