package com.plumemu.coreui.components

/**
 * A soft shade is the same along an edge once past the corners and the blur, so the image of a big box is its small one with the
 * middle stretched: what it costs to make no longer grows with the box (a cover grid asks for a new size of shade for every cover).
 */
internal object StretchedPixels {
    /**
     * [small] ([sw] x [sh]) as a [bw] x [bh] image: the first and last [keep] columns and rows are copied, and the middle repeats
     * column and row [keep]. A side that is not made bigger is copied as it is; a side that is needs at least 2 * [keep] + 1 px of small image.
     */
    fun expand(
        small: IntArray,
        sw: Int,
        sh: Int,
        bw: Int,
        bh: Int,
        keep: Int,
    ): IntArray {
        require(bw >= sw && bh >= sh && small.size == sw * sh) { "the small image must fit in the big one" }
        require((bw == sw || sw >= 2 * keep + 1) && (bh == sh || sh >= 2 * keep + 1)) { "a stretched side needs at least ${2 * keep + 1} px of small image" }
        val out = IntArray(bw * bh)
        for (y in 0 until bh) {
            val sy = if (bh == sh) y else if (y < keep) y else if (y >= bh - keep) sh - (bh - y) else keep
            val from = sy * sw
            val to = y * bw
            if (bw == sw) {
                System.arraycopy(small, from, out, to, sw)
                continue
            }
            System.arraycopy(small, from, out, to, keep)
            java.util.Arrays.fill(out, to + keep, to + bw - keep, small[from + keep])
            System.arraycopy(small, from + sw - keep, out, to + bw - keep, keep)
        }
        return out
    }
}
