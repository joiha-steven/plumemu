package com.plumemu.coredata.db

import org.json.JSONObject

/**
 * A stand-in for the scraper's reader (core-data does not depend on scraper): `details.rating` of
 * `{"format":1,"details":{...}}`, 1 to 20; throws on text that is not JSON, as a reader may.
 */
object TestDetailsRating : DetailsRating {
    override fun of(detailsJson: String): Int? {
        val details = JSONObject(detailsJson).optJSONObject("details") ?: return null
        if (!details.has("rating") || details.isNull("rating")) return null
        return details.getInt("rating").takeIf { it in 1..20 }
    }
}
