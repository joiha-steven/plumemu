package com.plumemu.coredata.db

import androidx.room.ColumnInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One listed game's saved box front in pixels, with its system ([ScrapedGameDao.boxFrontSizes]). */
data class BoxFrontSize(
    @ColumnInfo(name = "system_id") val systemId: String,
    val width: Int,
    val height: Int,
)

/**
 * The shape of each system's box fronts from the person's own covers: the median
 * width over height of the saved box fronts, once a system has [MIN_COVERS]. It follows the region
 * the person scraped in (a Japanese Super Famicom box is tall, a US SNES box wide); below
 * [MIN_COVERS] the app keeps the platform data's ratio. The app maps it to the grid's shape.
 */
object BoxFrontShapes {
    /** Fewer covers than this say too little: one odd box would decide a system. */
    const val MIN_COVERS = 5

    /**
     * While changes keep coming, the shapes are read at most once in this long: a run stores a
     * game every 3 to 5 s and a scan writes batches, and a median a minute late is fine.
     */
    const val READ_INTERVAL_MILLIS = 60_000L

    /** Changes that stop for this long are over: the last read follows (no sooner than [READ_INTERVAL_MILLIS] after the one before). */
    const val SETTLE_MILLIS = 2_000L

    /** Width over height, rounded to this many steps per 1, so one more cover rarely changes the screen. */
    private const val STEPS = 100

    /** Each system with at least [MIN_COVERS] readable sizes in [sizes], to its median width over height. */
    fun medians(sizes: List<BoxFrontSize>): Map<String, Float> =
        sizes
            .filter { it.width > 0 && it.height > 0 }
            .groupBy({ it.systemId }, { it.width.toDouble() / it.height })
            .filterValues { it.size >= MIN_COVERS }
            .mapValues { (_, ratios) -> (median(ratios) * STEPS).roundToInt().toFloat() / STEPS }

    /**
     * [medians] of the box fronts saved at [sizeName]: read when observed, then after changes to
     * `game` or `scraped_game` as [readTimes] allows; equal answers are skipped. One query for the
     * whole library: a focus move or a frame never reads it.
     */
    fun observe(
        database: PlumDatabase,
        sizeName: String,
        intervalMillis: Long = READ_INTERVAL_MILLIS,
        settleMillis: Long = SETTLE_MILLIS,
    ): Flow<Map<String, Float>> =
        readTimes(database.invalidationTracker.createFlow(GAME_TABLE, SCRAPED_TABLE), intervalMillis, settleMillis)
            .map { medians(database.scrapedGameDao().boxFrontSizes(sizeName)) }
            .distinctUntilChanged()

    /**
     * When to read again, given [changes] (whose first value is the state when observed): at once
     * for the first; after that, once changes have been quiet for [settleMillis], but never sooner
     * than [intervalMillis] after the last read, and never later than [intervalMillis] after the
     * first change still unread (changes that never pause, a long scan). So 30 games stored 4 s
     * apart cost about 3 reads, not 30, and the last read comes about [settleMillis] after the
     * last change once a minute has passed since the read before. Ends one read after [changes] end.
     */
    internal fun readTimes(
        changes: Flow<*>,
        intervalMillis: Long,
        settleMillis: Long,
    ): Flow<Unit> =
        channelFlow {
            val pending = Channel<Unit>(Channel.CONFLATED)
            launch {
                try {
                    changes.collect { pending.send(Unit) }
                } finally {
                    pending.close()
                }
            }
            if (pending.receiveCatching().isClosed) return@channelFlow
            send(Unit)
            while (true) {
                val window = async { delay(intervalMillis) }
                if (pending.receiveCatching().isClosed) {
                    window.cancel()
                    return@channelFlow
                }
                var ended = false
                withTimeoutOrNull(intervalMillis) {
                    window.await()
                    if (pending.tryReceive().isClosed) {
                        ended = true
                        return@withTimeoutOrNull
                    }
                    // Quiet: nothing arrived during a whole settle. tryReceive never loses a change.
                    while (true) {
                        delay(settleMillis)
                        val next = pending.tryReceive()
                        if (next.isClosed) ended = true
                        if (next.isClosed || next.isFailure) break
                    }
                }
                window.cancel()
                send(Unit)
                if (ended) return@channelFlow
            }
        }.conflate()

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private const val GAME_TABLE = "game"
    private const val SCRAPED_TABLE = "scraped_game"
}
