package com.plumemu.coredata.db

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/** What one pass cost: the app logs it. */
data class ArtAspectPass(
    val rowsWritten: Int,
    val readMillis: Long,
    val writeMillis: Long,
)

/**
 * Keeps `game.art_aspect` in step with `scraped_game`, so a grid packs at each game's shape before any result is read.
 *
 * One pass over the library, not a write in each place art changes: pictures are saved, replaced, copied to a key, cleared and
 * deleted from many places, a game's key can change, and a new row can meet a stored result. The pass compares what is stored
 * with what `scraped_game` says and writes only the differences, up to [WRITE_CHUNK] rows a transaction. A write to `game` re-runs
 * every list query, so a run that stores a game every few seconds must not write each one. A write racing a newer picture is put
 * right by the pass that picture's own change starts; a pass stopped between chunks leaves the rest to the next one.
 */
object ArtAspects {
    /** While changes keep coming, one pass in this long at most: a stale shape lasts no longer. */
    const val READ_INTERVAL_MILLIS = 15_000L

    /** Changes that stop for this long are over: a person's new picture is stored about this long after it is saved. */
    const val SETTLE_MILLIS = 1_000L

    /** The first pass waits this long, so it does not compete with a cold start. */
    const val FIRST_READ_DELAY_MILLIS = 3_000L

    /** Rows in one transaction: a library's first pass must not keep a backup or a play session's write waiting for the whole of it. */
    const val WRITE_CHUNK = 200

    /**
     * The shape the home screen packs at for [sizes]: the grid picture's, else the large one's, as the app's `artOf`. Float
     * division as `ArtPicture.aspect`, so the stored value is the live one to the bit. A size that does not read is not stored.
     */
    fun aspectOf(sizes: ArtSizes): Float? =
        when {
            sizes.gridWidth > 0 && sizes.gridHeight > 0 -> sizes.gridWidth.toFloat() / sizes.gridHeight
            sizes.largeWidth > 0 && sizes.largeHeight > 0 -> sizes.largeWidth.toFloat() / sizes.largeHeight
            else -> null
        }

    /** One pass: fills, overwrites and clears what differs. 0 rows written writes nothing, so a pass never starts another. */
    suspend fun sync(
        dao: ArtAspectDao,
        gridSizeName: String,
        largeSizeName: String,
    ): ArtAspectPass {
        val started = System.nanoTime()
        val changes =
            dao.artSizes(gridSizeName, largeSizeName).mapNotNull { row ->
                val aspect = aspectOf(row)
                if (aspect == row.artAspect) null else ArtAspectChange(row.id, aspect)
            }
        val read = System.nanoTime()
        for (chunk in changes.chunked(WRITE_CHUNK)) {
            dao.write(chunk)
            yield()
        }
        val written = System.nanoTime()
        return ArtAspectPass(changes.size, (read - started) / NANOS_PER_MILLI, (written - read) / NANOS_PER_MILLI)
    }

    /** A pass after [firstDelayMillis], then after changes to `game` or `scraped_game` as [BoxFrontShapes.readTimes] allows. Emits each pass. */
    fun follow(
        database: PlumDatabase,
        gridSizeName: String,
        largeSizeName: String,
        firstDelayMillis: Long = FIRST_READ_DELAY_MILLIS,
        intervalMillis: Long = READ_INTERVAL_MILLIS,
        settleMillis: Long = SETTLE_MILLIS,
    ): Flow<ArtAspectPass> =
        flow {
            delay(firstDelayMillis)
            val changes = database.invalidationTracker.createFlow(GAME_TABLE, SCRAPED_TABLE)
            emitAll(BoxFrontShapes.readTimes(changes, intervalMillis, settleMillis).map { sync(database.artAspectDao(), gridSizeName, largeSizeName) })
        }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val GAME_TABLE = "game"
    private const val SCRAPED_TABLE = "scraped_game"
}
