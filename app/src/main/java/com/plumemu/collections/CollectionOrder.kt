package com.plumemu.collections

/** What a drop gives back: the state and the new id order, or null when the order is as it was. */
data class Dropped(val state: CollectionOrder, val newOrder: List<Long>?)

/**
 * Settings › Library › Collections, ordering: pick a row up, carry it up or down, drop it (keep) or cancel
 * (put it back), like System order. Idle when [held] is null. [original] is the order at pick-up.
 */
data class CollectionOrder(val order: List<Long>, val held: Long? = null, private val original: List<Long> = order) {
    val isHolding: Boolean get() = held != null

    fun pickUp(id: Long): CollectionOrder = if (held == null && id in order) copy(held = id, original = order) else this

    fun moveUp(): CollectionOrder = move(-1)

    fun moveDown(): CollectionOrder = move(1)

    fun drop(): Dropped {
        if (held == null) return Dropped(this, null)
        val idle = CollectionOrder(order)
        return Dropped(idle, if (order == original) null else order)
    }

    fun cancel(): CollectionOrder = CollectionOrder(if (held == null) order else original)

    /** The list changed under us (a collection made or deleted): the carried row is let go if it is gone. */
    fun refreshed(ids: List<Long>): CollectionOrder =
        if (held != null && held in ids) {
            // keep the carried row where it is, only what it moves among follows the data
            val rest = ids.filter { it != held }
            val at = order.indexOf(held).coerceIn(0, rest.size)
            CollectionOrder(rest.toMutableList().apply { add(at, held) }, held, ids)
        } else {
            CollectionOrder(ids)
        }

    private fun move(by: Int): CollectionOrder {
        val from = order.indexOf(held ?: return this)
        val to = (from + by).coerceIn(0, order.lastIndex)
        if (to == from) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }
}
