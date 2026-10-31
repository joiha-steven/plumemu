package com.plumemu.library.scrape

import androidx.room.withTransaction
import com.plumemu.coredata.db.GameFileName
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.identify.name.TitleCase
import com.plumemu.identify.name.TitleCleaner

/**
 * The name a file's name gives, for scrape-rules repairs that show a game's file name again when its
 * row has no stored one (`file_title` ''). Made as the identifier's own fallback does
 * (`Identifier.fallbackTitle`: the base name cleaned, title case for console systems) and with the
 * catalogue's sort name and regions; a game whose folder named it (a generic `Disc 1` file) gets its
 * file's words here, and the naming pass after the repair names it properly.
 *
 * Why it matters: `ScrapedGameDao.forgetHashMatches` sets `FILE_NAME` and keeps the shown title when
 * `file_title` is empty, and `NameDecision` never replaces a `FILE_NAME` title with a file-name result
 * (a tie keeps the stored name), so the wrong title would stay for good.
 */
internal object FileNameTitles {
    data class FileName(val title: String, val sortName: String, val regions: String)

    fun of(fileName: String, systemId: String): FileName {
        val dot = fileName.lastIndexOf('.')
        val cleaned = TitleCleaner.clean(if (dot > 0) fileName.substring(0, dot) else fileName)
        return FileName(TitleCase.forSystem(cleaned.title, systemId), cleaned.sortName, cleaned.regions.joinToString(",") { it.code })
    }

    /** Writes the file name's title into each of [games] that still has none. */
    suspend fun fill(dao: ScrapedGameDao, games: List<GameFileName>) {
        for (game in games) {
            val name = of(game.fileName, game.systemId).takeIf { it.title.isNotBlank() } ?: continue
            dao.fillFileName(game.id, name.title, name.sortName, name.regions)
        }
    }

    /**
     * Scrape rules 4: games the version 3 repair left showing the wrong title under `FILE_NAME`
     * because they had no file-name title. Every `FILE_NAME` game with none whose title is not its
     * file's name gets that name and is named again (`needs_identify`); a person's name (another
     * trust) and a game already showing its file's name are left as they are.
     *
     * @return the games reset.
     */
    suspend fun showWhereMissing(database: PlumDatabase): Int = database.withTransaction {
        val dao = database.scrapedGameDao()
        var reset = 0
        for (game in dao.gamesShownWithoutFileTitle()) {
            val name = of(game.fileName, game.systemId)
            if (name.title.isBlank() || name.title == game.title) continue
            dao.fillFileName(game.id, name.title, name.sortName, name.regions)
            reset += dao.showFileNameAgain(game.id)
        }
        reset
    }
}
