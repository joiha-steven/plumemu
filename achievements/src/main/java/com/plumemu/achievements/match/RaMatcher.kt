package com.plumemu.achievements.match

import com.plumemu.achievements.api.RaGameList
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.api.RetroAchievementsClient
import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.achievements.hash.RaHashResult
import com.plumemu.achievements.hash.RaHasher
import com.plumemu.achievements.hash.RaRomSource
import java.io.IOException
import java.util.Locale

/** Which RetroAchievements game one ROM is ([RaMatcher.match]). */
public sealed interface RaMatch {
    /** RetroAchievements lists game [raGameId] under the file's hash [md5]. */
    public data class Matched(val raGameId: Int, val md5: String) : RaMatch

    /**
     * The file cannot be hashed because its format needs a decoder Plumemu lacks, but exactly one
     * game of the console has the file's name: RetroAchievements game [raGameId]. Less certain than
     * [Matched] (a name is not a hash); Info says so. No hash is known.
     */
    public data class MatchedByName(val raGameId: Int) : RaMatch

    /** The file hashes to [md5], and RetroAchievements lists no game with it for the system (or no game at all). */
    public data class NoSet(val md5: String) : RaMatch

    /** The file cannot be given a hash; [reason] says why, [detail] is for the log. Asks nothing of RetroAchievements. */
    public data class CannotHash(val reason: RaCannotHashReason, val detail: String? = null) : RaMatch

    /** Reading the file failed (card removed, permission gone); try again later, store nothing. */
    public data class Unreadable(val cause: IOException) : RaMatch

    /** The Plumemu system has no RetroAchievements console ([RaSystems]); the file is not read. */
    public data object NotSupported : RaMatch

    /**
     * The game list could not be had and no saved copy answers: [failure] says why
     * ([RaResult.WrongKey], [RaResult.NoConnection]...), so the caller can show it. Not an answer
     * about the game: store nothing.
     */
    public data class Failed(val failure: RaResult.Failure) : RaMatch
}

/**
 * Finds the RetroAchievements game of one ROM: the system's console ([RaSystems]), the file's
 * hash, then the console's game list (saved by the client for days, so most matches send no
 * request).
 *
 * The file is hashed before the list is asked for, so a file that cannot be hashed costs no
 * request. A list returned stale (RetroAchievements could not be asked, an older saved copy came
 * back) still gives [RaMatch.Matched] when it lists the hash: a game's hashes are only ever added
 * to. A stale list without the hash gives [RaMatch.Failed] with the reason, never
 * [RaMatch.NoSet]: the game may have been added since the copy was saved, and a stored "no set"
 * would hide it.
 *
 * A file that cannot be hashed only because its format needs a decoder
 * ([RaCannotHashReason.NeedsDecoder]) and comes with its names is looked for by name instead: the
 * console's game list, already downloaded, is searched for the one game whose title (each part of a
 * "A | B" title counts) reads like one of the names under [nameKey]. Hacks, homebrew, prototypes,
 * unlicensed releases and subsets are never candidates. One game id gives [RaMatch.MatchedByName];
 * none, or several different ids (no guessing), keeps [RaMatch.CannotHash]. A stale list without a
 * name match gives [RaMatch.Failed], as for a hash. Names never leave the device: nothing is sent,
 * the names are only compared with the list on the device.
 *
 * Callers cache the hash by file, not the answer: a stored hash goes through [matchHash] again,
 * so a set RetroAchievements adds later is found without hashing the file again.
 */
public class RaMatcher(
    private val gameList: suspend (consoleId: Int) -> RaResult<RaGameList>,
    /** Turns a game name into the key two names are compared by; the app passes its title matching (`MatchKey`, `TitleCleaner`). */
    private val nameKey: (String) -> String = ::plainNameKey,
    private val hash: suspend (consoleId: Int, rom: RaRomSource) -> RaHashResult = RaHasher::hash,
) {
    /** A matcher on [client]'s game lists and the real hasher. */
    public constructor(client: RetroAchievementsClient) : this(client::gameList)

    /**
     * The match of [rom], a game of Plumemu system [systemId]; [names] are the library game's titles
     * (empty: no name matching). Never throws for a file or network failure.
     */
    public suspend fun match(systemId: String, rom: RaRomSource, names: List<String> = emptyList()): RaMatch {
        val consoleId = RaSystems.consoleOf(systemId) ?: return RaMatch.NotSupported
        val md5 = when (val hashed = hash(consoleId, rom)) {
            is RaHashResult.Hashed -> hashed.md5.lowercase(Locale.ROOT)
            is RaHashResult.CannotHash -> {
                val cannot = RaMatch.CannotHash(hashed.reason, hashed.detail)
                return if (hashed.reason == RaCannotHashReason.NeedsDecoder) lookUpByName(consoleId, names, cannot) else cannot
            }
            is RaHashResult.Unreadable -> return RaMatch.Unreadable(hashed.cause)
        }
        return lookUp(consoleId, md5)
    }

    /**
     * The match of a hash already known ([md5], from a stored match whose file has not changed),
     * without reading the file: only the system's game list is asked for (the client's saved copy
     * when fresh). A stored "no set" becomes [RaMatch.Matched] once RetroAchievements lists the
     * hash. Never [RaMatch.CannotHash] or [RaMatch.Unreadable].
     */
    public suspend fun matchHash(systemId: String, md5: String): RaMatch {
        val consoleId = RaSystems.consoleOf(systemId) ?: return RaMatch.NotSupported
        return lookUp(consoleId, md5.lowercase(Locale.ROOT))
    }

    private suspend fun lookUp(consoleId: Int, md5: String): RaMatch =
        when (val list = gameList(consoleId)) {
            is RaResult.Failure -> RaMatch.Failed(list)
            is RaResult.Ok -> {
                val game = list.value.games.firstOrNull { game -> game.hashes.any { it.equals(md5, ignoreCase = true) } }
                when {
                    game != null -> RaMatch.Matched(game.id, md5)
                    list.stale != null -> RaMatch.Failed(list.stale.reason)
                    else -> RaMatch.NoSet(md5)
                }
            }
        }

    private suspend fun lookUpByName(consoleId: Int, names: List<String>, cannot: RaMatch.CannotHash): RaMatch {
        val wanted = names.map(nameKey).filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return cannot
        return when (val list = gameList(consoleId)) {
            is RaResult.Failure -> RaMatch.Failed(list)
            is RaResult.Ok -> {
                val ids = list.value.games
                    .filter { game -> !isNotPlainRelease(game.title) && nameParts(game.title).any { nameKey(it) in wanted } }
                    .map { it.id }
                    .distinct()
                when {
                    ids.size == 1 -> RaMatch.MatchedByName(ids.single())
                    ids.size > 1 -> cannot
                    list.stale != null -> RaMatch.Failed(list.stale.reason)
                    else -> cannot
                }
            }
        }
    }

    private companion object {
        val NOT_PLAIN_MARKS = listOf("~hack~", "~homebrew~", "~prototype~", "~unlicensed~", "[subset")

        /** A hack, homebrew, prototype, unlicensed release or subset: never a name match. */
        fun isNotPlainRelease(title: String): Boolean {
            val lower = title.lowercase(Locale.ROOT)
            return NOT_PLAIN_MARKS.any { it in lower }
        }

        /** "A | B" holds two names. */
        fun nameParts(title: String): List<String> = title.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** The key names are compared by when none is given: lowercase letters and digits only. */
private fun plainNameKey(name: String): String = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
