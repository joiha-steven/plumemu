package com.plumemu.library.catalog

import com.plumemu.identify.name.Region

/**
 * Which copy of a game a list shows when the same title exists in several regions. Only copies
 * that differ in region, language or dump-quality marks are grouped (see `CleanTitle.variantKey`);
 * revisions, betas, translations and hacks stay games of their own. Copies rank by the person's
 * region order; a World release beats any region not in that order; then the shortest name wins,
 * which puts `Game (USA)` ahead of `Game (USA) [b]`.
 */
object RegionPreference {
    /** The order used until the person picks one in Settings. */
    val DEFAULT: List<Region> = listOf(Region.USA, Region.WORLD, Region.EUROPE, Region.JAPAN)

    internal fun rank(regions: List<Region>, preferred: List<Region>): Int {
        val listed = regions.map { preferred.indexOf(it) }.filter { it >= 0 }
        return when {
            listed.isNotEmpty() -> listed.min()
            Region.WORLD in regions -> preferred.size
            else -> preferred.size + 1
        }
    }

    /** For each item, whether it is the copy its group shows. Items group by [groupKey]. */
    internal fun <T> primaryCopies(
        items: List<T>,
        preferred: List<Region>,
        groupKey: (T) -> Any,
        regions: (T) -> List<Region>,
        fileName: (T) -> String,
    ): Set<T> = items.groupBy(groupKey).values.mapTo(HashSet()) { copies ->
        copies.minWith(
            compareBy<T>({ rank(regions(it), preferred) }, { fileName(it).length }, { fileName(it) }),
        )
    }
}
