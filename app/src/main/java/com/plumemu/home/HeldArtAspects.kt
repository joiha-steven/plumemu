package com.plumemu.home

/**
 * The stored shapes one visit of a grid packs at. The pass that keeps `game.art_aspect` runs while a person looks at the grid, and
 * a changed shape at any index moves the row breaks above it, so a value read when the system was entered stands until the next
 * visit. A picture in hand is not held: [coverBoxAspect] takes it over the value given here, which is how replaced art shows at once.
 *
 * Entering copies nothing: a system switch only keeps the rows' reference, and the table is built the first time the rows change
 * during a visit. A grid kept built beside the shown system ends its visit when it stops being shown ([endVisit]). Touched in composition and in the packing's derived state, on the main thread.
 */
internal class HeldArtAspects(
    private var rows: List<GameRowModel>,
) {
    private var held: HashMap<String, Float?>? = null

    /** Raised when a visit ends, so a packing that read [of] packs again at the shapes stored by then. */
    private val visits = androidx.compose.runtime.mutableIntStateOf(0)

    /** The visit is over (a grid kept built while another system is shown): the next one starts from the stored shapes. */
    fun endVisit() {
        held = null
        visits.intValue++
    }

    /** The list changed during the visit: known games keep their value, a new game's first value is its held one. */
    fun follow(
        next: List<GameRowModel>,
        /** The grid is not shown: nothing is on screen to hold still, so the next visit starts from these rows. */
        fresh: Boolean = false,
    ) {
        if (next === rows) return
        if (fresh) {
            held = null
            rows = next
            return
        }
        val table = held ?: HashMap<String, Float?>(rows.size * 2).also { table -> rows.forEach { table[it.id] = it.artAspect } }
        next.forEach { if (!table.containsKey(it.id)) table[it.id] = it.artAspect }
        held = table
        rows = next
    }

    fun of(row: GameRowModel): Float? {
        visits.intValue
        val table = held ?: return row.artAspect
        return if (table.containsKey(row.id)) table[row.id] else row.artAspect
    }
}
