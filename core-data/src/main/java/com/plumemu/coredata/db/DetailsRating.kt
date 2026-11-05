package com.plumemu.coredata.db

/**
 * Reads the rating out of a stored `scraped_game.details_json`, for [PlumMigrations.migration15To16]'s
 * back-fill of `scraped_game.rating`. This module never parses the details (it does not depend on
 * scraper): the app passes the scraper's own reader (`GameDetails.listedRatingOf`), the one every
 * new row's rating comes from, so old and new rows hold the same value.
 *
 * Returns 1 to 20, or null for none; may throw on text it cannot read (the migration then keeps
 * null for that row).
 */
fun interface DetailsRating {
    fun of(detailsJson: String): Int?
}
