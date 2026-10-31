package com.plumemu.secondscreen

import com.plumemu.home.GameArt
import com.plumemu.home.HomeArtSource
import kotlinx.coroutines.flow.first

/**
 * What the details card shows of the result saved under [key]: its large box front (the picture
 * the details column draws), its facts, its English synopsis and rating; null when there is none. The
 * home screen's own source, so the card shows what the details column shows: one read of the
 * result, and the synopsis and rating read once more, as the details column reads them for the focused game.
 * The caller reads it off the main thread ([SecondScreenFeed]).
 */
internal suspend fun HomeArtSource.cardResult(key: String): GameArt? {
    val art = observe(listOf(key)).first()[key] ?: return null
    val extras = extras(key)
    return art.copy(details = art.details.copy(description = extras.synopsis, rating = extras.rating))
}
