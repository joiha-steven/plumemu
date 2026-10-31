package com.plumemu.identify.fingerprint.nx

import com.plumemu.identify.fingerprint.TitleKind

/**
 * The Switch title id a file name carries, for code outside this module that must not open the
 * file: the library's catalogue groups a game's base, update and DLC by it during the walk
 *. The same rules as [SwitchReader] (the only place they are written), so the walk and
 * the naming pass agree on which game a file belongs to.
 */
object SwitchTitleIds {
    /** A title id taken from a file name: the id itself, its game's base id and what it is. */
    data class Id(val titleId: String, val baseId: String, val kind: TitleKind)

    /** True when [systemId] is the Switch (`switch` in platforms.json, or one of its other names). */
    fun isSwitch(systemId: String): Boolean = SwitchReader.claims(systemId, "")

    /**
     * The first Switch title id in [fileName] (`Game [0100ABCD00010000].nsp`, `0100ABCD00010800.nsp`),
     * or null. Only the name is read; an NSP's own file table may name another id, which the naming
     * pass reads later.
     */
    fun fromFileName(fileName: String): Id? {
        val titleId = SwitchReader.fromFileName(fileName) ?: return null
        return Id(titleId, SwitchReader.baseOf(titleId), SwitchReader.kindOf(titleId))
    }
}
