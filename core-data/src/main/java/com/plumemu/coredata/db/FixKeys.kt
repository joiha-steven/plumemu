package com.plumemu.coredata.db

/**
 * `game.fix_keys` holds a game's fix keys strongest first, one per line: a product code may hold
 * a space, never a line break (header codes are printable text).
 */
object FixKeys {
    private const val SEPARATOR = "\n"

    /** Blank keys and keys holding a control character (a line break would split them) are dropped. */
    fun join(keys: List<String>): String = keys.filter { it.isNotBlank() && it.none(Char::isISOControl) }.joinToString(SEPARATOR)

    fun split(stored: String): List<String> = stored.split(SEPARATOR).filter { it.isNotBlank() }

    /** The keys a fix of [gameId] is looked up under: its own keys, else the row key. */
    fun forGame(gameId: Long, stored: String): List<String> = split(stored).ifEmpty { listOf(NameFixEntity.rowKey(gameId)) }

    /**
     * Of a file's [keys], the ones a person's fix is stored and looked up under: its
     * fingerprint keys (`crc:`, `chd:`, `arcade:`), never a product code (`code:`). Revisions of one
     * game and a hack keeping the header share a code, so a fix under it would rename them all. A
     * file with no fingerprint key (a loose cartridge read at QUICK depth) is fixed under its row
     * key until a whole-file read gives it a `crc:` key (`LibraryScanner.addContentKey`).
     */
    fun fixTargets(keys: List<String>): List<String> =
        keys.filter { !it.startsWith(CODE_PREFIX) && !it.startsWith(NameFixEntity.ROW_PREFIX) }

    /** Whether a fix found under [key] may be applied and copied to the row's other keys: never one under a product code. */
    fun isCopyableFrom(key: String): Boolean = !key.startsWith(CODE_PREFIX)

    const val CODE_PREFIX = "code:"
}
