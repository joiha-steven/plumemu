package com.plumemu.library.scrape

import androidx.room.withTransaction
import com.plumemu.coredata.db.FixKeys
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.SerialHashMatch
import com.plumemu.coredata.db.HashMatchGame
import com.plumemu.identify.arcade.ArcadeSystems
import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.scraper.StoredAnswers

/**
 * Scrape rules 3: a lookup that sent a serial with a CRC was trusted like a CRC
 * lookup, so a fan translation (its CRC in no database, its SNES game code also a 3DS game's) was
 * stored as a hash match of the other system's game ("Fish Eyes 3D" for Star Ocean). The scraper
 * now checks the answer's system whenever a serial is sent; this forgets the stored matches that
 * same check refuses (`StoredAnswers.serialAnswerFitsSystem`).
 *
 * Exact on what the database keeps: the answer's system is in `details_json`; a game's lookup sent
 * a serial when its `fix_keys` has a `code:<kind>:` line (what `ScrapeHintsFromFingerprint` sends),
 * and the kind names the format the code was read from, whose system counts as the live check's
 * does (a `.gbc` in `gba/`, an `.sfc` in `snesmsu1/`). A row is forgotten only when its answer is
 * wrong for every game sharing its key: a game with no code (no serial sent) or whose system takes
 * the answer keeps it. An answer that names no system cannot be judged and is kept. Forgotten rows
 * read as never asked (not `fresh`): the next run judges the cached answer by the new rule and falls
 * back to the name search. Their games show the file's name again until the naming pass names them
 * (`ScrapedGameDao.forgetHashMatches`); a game with no stored file-name title gets one written
 * first ([FileNameTitles]), or the wrong title would stay under `FILE_NAME`.
 *
 * Scrape rules 4 ([forgetCodeDroppedMatches]): identify drops the cartridge code of a
 * hack or translation (`HackNames`: a tag in the file name, or the file title and the code's name
 * each having a word the other lacks, "Pokemon Radical Red" against the original), so a library
 * named again before version 3 ran had lost the `code:` line of exactly the games version 3 was for,
 * and kept "Fish Eyes 3D" on an AYN Thor. The word rule leaves no trace in the database, so
 * every cartridge game with no code left (not arcade, where a CRC answer may sit under a child
 * system; a format `CodeKind.ofLooseCartridge` reads a code from: a reader of the folder's system
 * claiming the file's own extension, so an archive, whose content may be another format, is kept) is judged as if its lookup had sent its format's code.
 *
 * The trade-off: a file really looked up by CRC alone that rightly got an answer on another system
 * (a Satellaview `.bs` in `snes/`) is forgotten too, and shows its file name until the run after the
 * next scan asks it again. That lookup sends the same parameters as before, so within the scraper's
 * cache life (`ResponseCache.TTL_MILLIS`, 30 days) the cached answer is reused (no quota, saved
 * pictures) and matched again; an older lookup costs one API ask. A game whose code was dropped sends
 * no serial now, a different request, so its wrong answer is not reused.
 */
internal object WrongSystemMatches {
    /** @return the stored results forgotten. */
    suspend fun forget(database: PlumDatabase): Int = database.withTransaction {
        val wrong = database.scrapedGameDao().hashMatchesAskedWithSerial()
            .groupBy { it.scrapeKey }
            .filterValues { games -> games.all(::isWrongFor) }
            .keys.toList()
        forgetShowingFileNames(database, wrong)
    }

    /** Version 4: the wrong-system matches of cartridge games whose code the naming pass dropped. @return the rows forgotten. */
    suspend fun forgetCodeDroppedMatches(database: PlumDatabase): Int = database.withTransaction {
        val dao = database.scrapedGameDao()
        // Keys whose every game can be judged; a game that cannot (arcade, no code format) keeps the row.
        val judged = dao.hashMatchGamesWithoutCode().groupBy { it.scrapeKey }.filterValues { games -> games.all { formatOf(it) != null } }
        val wrong = dao.detailsOf(judged.keys.toList())
            .filter { stored -> judged.getValue(stored.scrapeKey).all { isWrongWithoutCode(it, stored.detailsJson) } }
            .map { it.scrapeKey }
        forgetShowingFileNames(database, wrong)
    }

    private suspend fun forgetShowingFileNames(database: PlumDatabase, keys: List<String>): Int {
        if (keys.isEmpty()) return 0
        val dao = database.scrapedGameDao()
        FileNameTitles.fill(dao, dao.gamesWithoutFileTitle(keys))
        return dao.forgetHashMatches(keys)
    }

    private fun isWrongFor(game: SerialHashMatch): Boolean {
        val code = FixKeys.split(game.fixKeys).firstOrNull { it.startsWith(FixKeys.CODE_PREFIX) } ?: return false
        val format = CodeKind.named(code.removePrefix(FixKeys.CODE_PREFIX).substringBefore(':'))?.nativeSystemId
        return StoredAnswers.serialAnswerFitsSystem(game.detailsJson, game.systemId, format) == false
    }

    /** The system of the format [game]'s code is (or would be) read from; null when it cannot be judged. */
    private fun formatOf(game: HashMatchGame): String? {
        val code = FixKeys.split(game.fixKeys).firstOrNull { it.startsWith(FixKeys.CODE_PREFIX) }
        if (code != null) return CodeKind.named(code.removePrefix(FixKeys.CODE_PREFIX).substringBefore(':'))?.nativeSystemId
        if (ArcadeSystems.isArcade(game.systemId)) return null
        return CodeKind.ofLooseCartridge(game.systemId, game.fileName.substringAfterLast('.', ""))?.nativeSystemId
    }

    private fun isWrongWithoutCode(game: HashMatchGame, detailsJson: String): Boolean =
        StoredAnswers.serialAnswerFitsSystem(detailsJson, game.systemId, formatOf(game)) == false
}
