package com.plumemu.restart

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/** Whether this process runs the code Android has compiled for the app now. */
internal sealed interface StaleCode {
    /** Not known: nothing is done. */
    data class Unknown(val why: String) : StaleCode

    /** Every compiled-code file the process mapped is still the one on disk. */
    data object Fresh : StaleCode

    /** [path] was mapped by this process and has since been replaced or removed ([why]). */
    data class Stale(val path: String, val why: String) : StaleCode
}

/**
 * Reads [maps] (the text of `/proc/self/maps`) for the app's compiled code under `<appDir>/oat/`
 * (`base.odex`, `base.vdex`, on some devices `base.art`).
 *
 * ART never swaps code into a running process: when Android compiles the app after the process
 * started (the nightly idle dexopt of a sideloaded APK, `cmd package compile`), the process keeps
 * the old, mostly interpreted code. The old files are then unlinked, so a mapping of them is:
 * - marked ` (deleted)` (a dexopt temp file such as `base.odex.8WfHre.tmp (deleted)`), or
 * - at a path that no longer exists (a `base.vdex.backup` mapped mid-compile, removed after), or
 * - at a path whose inode now ([statOf]) is not the inode mapped (maps' fifth column).
 *
 * Only "no such file" counts as gone; a path that cannot be read for another reason (denied, an
 * I/O error) is [StaleCode.Unknown], never stale.
 *
 * Any one of these is [StaleCode.Stale]. When the maps cannot be read ([maps] null) or name no
 * file under `<appDir>/oat/` at all (Android 6 compiles at install and maps no such file on some
 * devices; others run from the APK), the answer is [StaleCode.Unknown]: the safe default is to do
 * nothing. Another app's `oat/` paths (a shared library, a WebView provider) are ignored.
 */
internal fun staleCodeOf(
    maps: String?,
    appDir: String,
    statOf: (String) -> PathNow,
): StaleCode {
    if (maps == null) return StaleCode.Unknown("maps not readable")
    val oatDir = appDir.trimEnd('/') + "/oat/"
    val mapped = maps.lineSequence().mapNotNull(::mappingOf).filter { it.path.startsWith(oatDir) }.distinct().toList()
    if (mapped.isEmpty()) return StaleCode.Unknown("no compiled code mapped under $oatDir")
    // A sure sign on any mapping wins over another mapping that could not be read.
    var unknown: StaleCode.Unknown? = null
    for (each in mapped) {
        if (each.isDeleted) return StaleCode.Stale(each.path, "deleted")
        when (val now = statOf(each.path)) {
            PathNow.Gone -> return StaleCode.Stale(each.path, "gone")
            is PathNow.Unreadable -> unknown = unknown ?: StaleCode.Unknown("${each.path} not readable (${now.why})")
            is PathNow.Inode -> if (now.inode != each.inode) return StaleCode.Stale(each.path, "replaced")
        }
    }
    return unknown ?: StaleCode.Fresh
}

/** What `stat` says of a mapped path now. */
internal sealed interface PathNow {
    data class Inode(val inode: Long) : PathNow

    /** No such file (ENOENT). */
    data object Gone : PathNow

    /** Any other failure: not known. */
    data class Unreadable(val why: String) : PathNow
}

/** One line of `/proc/<pid>/maps` that maps a file. */
internal data class Mapping(val inode: Long, val path: String, val isDeleted: Boolean)

/** `address perms offset dev inode path`, the path last and possibly with spaces; null for anonymous mappings. */
internal fun mappingOf(line: String): Mapping? {
    val match = MAPS_LINE.matchEntire(line.trimEnd()) ?: return null
    val inode = match.groupValues[1].toLongOrNull() ?: return null
    val raw = match.groupValues[2]
    if (!raw.startsWith("/")) return null
    val isDeleted = raw.endsWith(DELETED)
    return Mapping(inode, if (isDeleted) raw.removeSuffix(DELETED) else raw, isDeleted)
}

private val MAPS_LINE = Regex("""^\S+\s+\S+\s+\S+\s+\S+\s+(\d+)\s+(.+)$""")
private const val DELETED = " (deleted)"

/** The device's answer: this process's maps, and `stat` on the exact path (the oat folder is not listable). */
internal fun staleCodeNow(sourceDir: String): StaleCode {
    val appDir = File(sourceDir).parent ?: return StaleCode.Unknown("no app folder")
    val maps =
        try {
            File("/proc/self/maps").readText()
        } catch (e: Exception) {
            null
        }
    return staleCodeOf(maps, appDir, ::statNow)
}

private fun statNow(path: String): PathNow =
    try {
        PathNow.Inode(Os.stat(path).st_ino)
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) PathNow.Gone else PathNow.Unreadable("errno ${e.errno}")
    } catch (e: Exception) {
        PathNow.Unreadable(e.javaClass.simpleName)
}
