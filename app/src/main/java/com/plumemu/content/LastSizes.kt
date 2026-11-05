package com.plumemu.content

/**
 * The size each picture was last decoded at, for at most [limit] pictures (the least recently used goes first), so a cover can be
 * drawn from the decode of another size while its exact one is made ([com.plumemu.home.CoverImages.cachedAnySize]).
 */
internal class LastSizes(private val limit: Int) {
    private val sizes =
        object : LinkedHashMap<String, Long>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > limit
        }

    /** Notes that [pictureId] was decoded at [widthPx] x [heightPx]. */
    @Synchronized
    fun record(
        pictureId: String,
        widthPx: Int,
        heightPx: Int,
    ) {
        sizes[pictureId] = (widthPx.toLong() shl Int.SIZE_BITS) or heightPx.toLong()
    }

    /** The last size of [pictureId] as (width, height) px, or null when it is not noted (never decoded, or forgotten). */
    @Synchronized
    fun last(pictureId: String): Pair<Int, Int>? = sizes[pictureId]?.let { (it ushr Int.SIZE_BITS).toInt() to it.toInt() }

    val count: Int @Synchronized get() = sizes.size
}
