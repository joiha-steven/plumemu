package com.plumemu.telemetry

/**
 * The ranges of docs/conventions/telemetry.md, written exactly as there (with an en dash), so the
 * server's allow-list matches them. An exact count of something private is never sent.
 */
internal object Buckets {
    private const val DASH = "–"

    /** `games_bucket`: 0, 1–99, 100–999, 1k–5k, 5k–10k, 10k+. */
    fun games(count: Int): String =
        when {
            count <= 0 -> "0"
            count < 100 -> "1${DASH}99"
            count < 1_000 -> "100${DASH}999"
            count < 5_000 -> "1k${DASH}5k"
            count < 10_000 -> "5k${DASH}10k"
            else -> "10k+"
        }

    /** `query_length_bucket`: 1–2, 3–5, 6+ (an empty search is not a search; it counts as 1–2). */
    fun queryLength(length: Int): String =
        when {
            length <= 2 -> "1${DASH}2"
            length <= 5 -> "3${DASH}5"
            else -> "6+"
        }

    /** `results_bucket`: 0, 1–9, 10+. */
    fun results(count: Int): String =
        when {
            count <= 0 -> "0"
            count < 10 -> "1${DASH}9"
            else -> "10+"
        }

    /**
     * `ram_bucket` from the device's total memory: <2, 2–4, 4–8, 8+ (GB). Android reports a little
     * less than the nominal size (a 4 GB device says about 3.6 GiB), so the size is rounded to whole
     * GiB first: the bucket is the one its box would name.
     */
    fun ram(totalBytes: Long): String {
        val gib = Math.round(totalBytes / (1024.0 * 1024.0 * 1024.0))
        return when {
            gib < 2 -> "<2"
            gib < 4 -> "2${DASH}4"
            gib < 8 -> "4${DASH}8"
            else -> "8+"
        }
    }
}
