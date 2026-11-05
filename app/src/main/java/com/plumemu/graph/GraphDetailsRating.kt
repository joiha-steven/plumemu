package com.plumemu.graph

import com.plumemu.coredata.db.DetailsRating
import com.plumemu.scraper.GameDetails

/**
 * The scraper's reader of a stored details JSON's rating, given to core-data for the schema 16
 * back-fill of `scraped_game.rating` (opening the app's database and preparing a restored one):
 * the same reader the details and every newly stored row use.
 */
internal val GraphDetailsRating = DetailsRating { json -> GameDetails.listedRatingOf(json) }
