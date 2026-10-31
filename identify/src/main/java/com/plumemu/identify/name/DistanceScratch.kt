package com.plumemu.identify.name

/**
 * Damerau-Levenshtein distance in its optimal-string-alignment form (a swap of two neighbours
 * costs 1; a substring is not edited twice), with rows reused across the candidates of one
 * lookup. Not thread-safe: one per lookup.
 */
internal class DistanceScratch(queryLength: Int) {
    private var twoBack = IntArray(queryLength + 1)
    private var previous = IntArray(queryLength + 1)
    private var current = IntArray(queryLength + 1)

    /**
     * Distance between [query] and `chars[start until end]`, or a value above [maxDistance] as
     * soon as every cell of a row is above it (the exact value then does not matter).
     */
    fun distance(query: String, chars: CharArray, start: Int, end: Int, maxDistance: Int): Int {
        val n = query.length
        val m = end - start
        if (n == 0) return m
        if (m == 0) return n
        for (j in 0..n) previous[j] = j
        for (i in 1..m) {
            val c = chars[start + i - 1]
            current[0] = i
            var rowMin = i
            for (j in 1..n) {
                val q = query[j - 1]
                val cost = if (q == c) 0 else 1
                var d = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && q == chars[start + i - 2] && query[j - 2] == c) {
                    d = minOf(d, twoBack[j - 2] + 1)
                }
                current[j] = d
                if (d < rowMin) rowMin = d
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val recycled = twoBack
            twoBack = previous
            previous = current
            current = recycled
        }
        return previous[n]
    }
}
