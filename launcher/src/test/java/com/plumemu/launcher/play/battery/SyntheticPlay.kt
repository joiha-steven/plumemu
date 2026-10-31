package com.plumemu.launcher.play.battery

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A made-up play, second by second, shaped like an AYN Thor's: the current
 * is noisy (29% per read), the counter is the charge really used ([counterRatio] times what the
 * current says; 1.31 over the Thor's trace), held in steps of 625 uAh and updated every 22.7 s. [drawAt] is
 * the mean draw in microamperes at each millisecond of the play. Read through [reader] on a test
 * clock, or [play] it through a [PlayBatteryMeter] as the in-game card's tick would.
 */
internal class SyntheticPlay(
    private val minutes: Int,
    private val seed: Int = 1,
    private val counterRatio: Double = THOR_COUNTER_RATIO,
    private val startCharge: Double = 2_000_000.0,
    /** What the device reports for a true current in microamperes (positive: discharging). */
    private val asDevice: (Double) -> Long = { -it.toLong() },
    private val counterAsDevice: (Long) -> Long = { it },
    /** Asleep (screen off): no reads, and the charge drops at [sleepDraw]. */
    private val asleep: (Long) -> Boolean = { false },
    private val sleepDraw: Double = 20_000.0,
    private val charging: (Long) -> Boolean = { false },
    private val chargeRate: Double = 1_500_000.0,
    private val drawAt: (Long) -> Double,
) {
    private val seconds = minutes * 60
    private val current = LongArray(seconds)
    private val counter = LongArray(seconds)
    private val level = IntArray(seconds)
    val trueCharge = DoubleArray(seconds)

    init {
        val random = Random(seed)
        var charge = startCharge
        var shown = (charge / STEP).toLong() * STEP
        var nextUpdateMs = random.nextDouble(0.0, UPDATE_MS)
        for (second in 0 until seconds) {
            val ms = second * 1_000L
            val noisy = drawAt(ms) * (1 + random.gaussian() * NOISE)
            val net = when {
                asleep(ms) -> sleepDraw
                charging(ms) -> noisy - chargeRate
                else -> noisy
            }
            charge -= net * counterRatio / SECONDS_PER_HOUR
            if (ms >= nextUpdateMs) {
                shown = (charge / STEP).toLong() * STEP
                nextUpdateMs += UPDATE_MS
            }
            current[second] = asDevice(net)
            counter[second] = counterAsDevice(shown)
            level[second] = (charge / FULL * 100).toInt().coerceIn(0, 100)
            trueCharge[second] = charge
        }
    }

    /** The device as [BatteryReader], at [FakeBatteryReader.nowMs] since the play began (plus [START_MS]). */
    fun reader(): FakeBatteryReader = FakeBatteryReader(this)

    /**
     * Plays to [untilMs] through [meter] as the card's tick does: read, wait what it asks; a sleep
     * stops the reads (the tick's wait does not run in deep sleep) and the next read comes on waking.
     * With [toldScreenOff] the meter is told the reads stop as a sleep begins, as the card is when
     * the screen goes off ([BatteryReads.stopReading]).
     */
    fun play(meter: PlayBatteryMeter, reader: FakeBatteryReader, untilMs: Long = seconds * 1_000L - 1, toldScreenOff: Boolean = false) {
        var wasAsleep = false
        while (reader.playMs <= untilMs) {
            if (asleep(reader.playMs)) {
                if (toldScreenOff && !wasAsleep) meter.stopReading()
                wasAsleep = true
                reader.playMs += 1_000
                continue
            }
            wasAsleep = false
            reader.playMs += meter.read()
        }
    }

    /** The charge really left at [playMs], in microampere-hours. */
    fun chargeAt(playMs: Long) = trueCharge[(playMs / 1_000).toInt().coerceIn(0, seconds - 1)]

    class FakeBatteryReader(private val play: SyntheticPlay) : BatteryReader {
        var playMs = 0L
        var reads = 0
        var levelReads = 0
        private val second get() = (playMs / 1_000).toInt().coerceIn(0, play.seconds - 1)

        override fun nowMs() = START_MS + playMs

        /** What the device returns at its [reads]-th current read (counting from 1), given the made-up one: a test breaks some. */
        var currentAt: (read: Int, device: Long) -> Long = { _, device -> device }

        override fun currentNow(): Long {
            reads++
            return currentAt(reads, play.current[second])
        }

        /** The counter the last read got. */
        var lastCounter = BatteryReader.UNKNOWN

        override fun chargeCounter() = play.counter[second].also { lastCounter = it }

        override fun level(): Int {
            levelReads++
            return play.level[second]
        }

        override fun isCharging() = play.charging(playMs)

        var toFullMs = BatteryReader.UNKNOWN

        override fun chargeTimeRemainingMs() = toFullMs
    }

    companion object {
        const val THOR_COUNTER_RATIO = 1.31
        const val STEP = 625L
        const val UPDATE_MS = 22_700.0
        const val NOISE = 0.29
        const val FULL = 6_254_000.0
        const val START_MS = 1_000_000L
        private const val SECONDS_PER_HOUR = 3_600

        private fun Random.gaussian(): Double {
            // Box-Muller: kotlin.random has no Gaussian.
            val u = nextDouble(1e-12, 1.0)
            return sqrt(-2 * ln(u)) * cos(2 * Math.PI * nextDouble())
        }
    }
}
