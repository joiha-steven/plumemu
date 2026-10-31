package com.plumemu.launcher.play.battery

import com.plumemu.coredata.db.SessionBattery
import com.plumemu.launcher.play.PlayBattery

/** What the in-game card's tick reads the battery through ([PlayBatteryMeter]); a test counts the reads. */
interface BatteryReads {
    /** One read; quick, never throws, on the main thread. Returns how long to wait before the next, in milliseconds. */
    fun read(): Long

    /**
     * The reads stop until further notice although the card is still up: the screen went off. The
     * next read starts a new stretch, so the time in between is not measured as play.
     */
    fun stopReading()

    /**
     * What the in-game battery part shows: the estimate, the game's usual pace and the
     * level over this play; null when nothing has been read. Quick, on the main thread.
     */
    fun snapshot(): BatterySnapshot? = null

    companion object {
        /** Reads nothing: a host without a meter (tests, benchmark builds: `MainActivity`). */
        val None: BatteryReads =
            object : BatteryReads {
                override fun read(): Long = Long.MAX_VALUE

                override fun stopReading() = Unit
            }
    }
}

/**
 * The battery of the play in progress as the in-game part draws it: [estimate] (null in the
 * first minute of a game never measured), the game's [usualDrawMicroamps] (null: never measured),
 * [levels], the level over this play, oldest first, timed like [nowMs] (elapsed time), and
 * [readMs], how long this play has been read: the time between reads of one stretch, without the
 * screen off or a sleep. A play read for over a minute whose [estimate] is still null is on a
 * device whose current and counter give no pace at all.
 */
data class BatterySnapshot(
    val estimate: BatteryEstimate?,
    val usualDrawMicroamps: Long?,
    val nowMs: Long,
    val levels: List<LevelPoint>,
    val readMs: Long = 0,
)

/** The level read at [atMs] (elapsed time), and whether the battery was charging then. */
data class LevelPoint(val atMs: Long, val level: Int, val isCharging: Boolean)

/**
 * The battery measurement of the play in progress, one per process (`AppGraph`): the in-game
 * card's tick calls [read] on the main thread while the card is up, the launcher marks a play's
 * start and takes its figures on its I/O thread ([PlayBattery]), and the in-game part asks
 * for [estimate]. Each read is three or four quick calls into the system and allocates nothing; all
 * state is behind one lock, held only for arithmetic.
 */
class PlayBatteryMeter(private val reader: BatteryReader) : PlayBattery, BatteryReads {
    private val lock = Any()
    private var estimator = BatteryEstimator()
    private var levelReadAtMs = Long.MIN_VALUE
    private var usualDraw: Long? = null

    // How long this play has been read (BatterySnapshot.readMs): the last read, and whether the next one continues its stretch.
    private var readMs = 0L
    private var lastReadAtMs = Long.MIN_VALUE
    private var stretchOpen = false

    // The level over this play for the in-game chart: a point when the minute's level read differs
    // from the last point (or charging does), a fixed ring, oldest overwritten.
    private val levelAtMs = LongArray(MAX_LEVEL_POINTS)
    private val levelValue = IntArray(MAX_LEVEL_POINTS)
    private val levelCharging = BooleanArray(MAX_LEVEL_POINTS)
    private var levelCount = 0
    private var levelNext = 0

    /** Reads, and says when to read again: every second at the start of a stretch, every [BatteryTuning.READ_EVERY_MS] after. */
    override fun read(): Long {
        val now = reader.nowMs()
        val current = reader.currentNow()
        val counter = reader.chargeCounter()
        val charging = reader.isCharging()
        synchronized(lock) {
            val levelDue = levelReadAtMs == Long.MIN_VALUE || now - levelReadAtMs >= BatteryTuning.LEVEL_EVERY_MS || now < levelReadAtMs
            val level = if (levelDue) reader.level() else BatteryReader.UNKNOWN_LEVEL
            if (levelDue) levelReadAtMs = now
            noteReadTime(now)
            estimator.add(now, current, counter, level, charging)
            if (level in 0..PERCENT) noteLevel(now, level, charging)
            return if (estimator.isStarting) BatteryTuning.START_READ_EVERY_MS else BatteryTuning.READ_EVERY_MS
        }
    }

    override fun stopReading() {
        synchronized(lock) {
            estimator.endStretch()
            stretchOpen = false
        }
    }

    override fun onPlayStarting(usualDrawMicroamps: Long?) {
        synchronized(lock) {
            estimator = BatteryEstimator()
            levelReadAtMs = Long.MIN_VALUE
            usualDraw = usualDrawMicroamps
            levelCount = 0
            levelNext = 0
            readMs = 0
            lastReadAtMs = Long.MIN_VALUE
            stretchOpen = false
        }
    }

    override fun takeMeasured(): SessionBattery? =
        synchronized(lock) {
            estimator.measured().also { estimator = BatteryEstimator() }
        }

    /**
     * The battery now for the in-game part: [usualDrawMicroamps] is the game's usual pace
     * (`PlayDao.usualDraw`), null for a game never measured. Android's time to full is asked only
     * while charging.
     */
    fun estimate(usualDrawMicroamps: Long?): BatteryEstimate? {
        val toFull = if (synchronized(lock) { estimator.isCharging }) reader.chargeTimeRemainingMs() else BatteryReader.UNKNOWN
        return synchronized(lock) { estimator.estimate(usualDrawMicroamps, toFull) }
    }

    /** The estimate with the usual pace given at [onPlayStarting], and the level over this play; null before the first read. */
    override fun snapshot(): BatterySnapshot? {
        val usual = synchronized(lock) { usualDraw }
        val estimate = estimate(usual)
        val now = reader.nowMs()
        return synchronized(lock) {
            if (levelCount == 0 && estimate == null) return null
            val start = (levelNext - levelCount + MAX_LEVEL_POINTS) % MAX_LEVEL_POINTS
            val levels =
                List(levelCount) { i ->
                    val at = (start + i) % MAX_LEVEL_POINTS
                    LevelPoint(levelAtMs[at], levelValue[at], levelCharging[at])
                }
            BatterySnapshot(estimate, usual, now, levels, readMs)
        }
    }

    /** Under [lock]: the time since the last read counts as read when it continues its stretch (no stop, no gap, not backwards). */
    private fun noteReadTime(nowMs: Long) {
        val since = nowMs - lastReadAtMs
        if (stretchOpen && lastReadAtMs != Long.MIN_VALUE && since in 0..BatteryTuning.GAP_MS) readMs += since
        lastReadAtMs = nowMs
        stretchOpen = true
    }

    /** Under [lock]: a point when the level or charging changed since the last one. */
    private fun noteLevel(nowMs: Long, level: Int, charging: Boolean) {
        if (levelCount > 0) {
            val last = (levelNext - 1 + MAX_LEVEL_POINTS) % MAX_LEVEL_POINTS
            if (levelValue[last] == level && levelCharging[last] == charging) return
        }
        levelAtMs[levelNext] = nowMs
        levelValue[levelNext] = level
        levelCharging[levelNext] = charging
        levelNext = (levelNext + 1) % MAX_LEVEL_POINTS
        if (levelCount < MAX_LEVEL_POINTS) levelCount++
    }

    private companion object {
        const val PERCENT = 100

        /** Level points kept: the level changes at most once a minute's read, so four hours, more than the chart's three. */
        const val MAX_LEVEL_POINTS = 240
    }
}
