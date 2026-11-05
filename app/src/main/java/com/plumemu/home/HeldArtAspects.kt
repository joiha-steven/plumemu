package com.plumemu.home

/**
 * The stored shapes one visit of a grid packs at. The pass that keeps `game.art_aspect` runs while a person looks at the grid, and
 * a changed shape at any index moves the row breaks above it, so a value read when the system was entered stands until the next
 * visit. A picture in hand is not held: [coverBoxAspect] takes it over the value given here, which is how replaced art shows at once.
 *
 * Entering copies nothing: a system switch only keeps the rows' reference, and the table is built the first time the rows change
 * during a visit. Touched in composition and in the packing's derived state, on the main thread.
 */
internal class HeldArtAspects(
    private var rows: List<GameRowModel>,
) {
    private var held: HashMap<String, Float?>? = null

    /** The list changed during the visit: known games keep their value, a new game's first value is its held one. */
    fun follow(next: List<GameRowModel>) {
        if (next === rows) return
        val table = held ?: HashMap<String, Float?>(rows.size * 2).also { table -> rows.forEach { table[it.id] = it.artAspect } }
        next.forEach { if (!table.containsKey(it.id)) table[it.id] = it.artAspect }
        held = table
        rows = next
    }

    fun of(row: GameRowModel): Float? {
        val table = held ?: return row.artAspect
        return if (table.containsKey(row.id)) table[row.id] else row.artAspect
    }
}
