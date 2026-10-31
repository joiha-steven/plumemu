package com.plumemu.library.matching

/**
 * How the library recognises one system: the folder names people give it and the file extensions
 * its games use. Plain data on purpose; the app builds these from the platform data.
 *
 * [emulators] decide whether a file of another system may stay in this system's folder (see
 * [SystemMatcher.matchFile]). [names] are the system's own names (full name, short name, display name); a folder called
 * `Nintendo - 3DS` or `Sony PlayStation 2` is matched against them when no folder alias fits.
 *
 * Folder names are compared without case, spaces or punctuation (`Super Famicom` = `superfamicom`);
 * extensions without the dot and without case.
 */
data class SystemRule(
    val id: String,
    val folders: Set<String>,
    val extensions: Set<String>,
    val names: Set<String> = emptySet(),
    /** Emulator ids of the system; two systems sharing one are close kin (Game Boy and Game Boy Advance). */
    val emulators: Set<String> = emptySet(),
)
