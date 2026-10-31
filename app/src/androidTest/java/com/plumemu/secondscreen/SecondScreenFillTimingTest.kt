package com.plumemu.secondscreen

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.PlumApplication
import com.plumemu.assumeEmulator
import com.plumemu.game.emulatorToStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the second screen's fill on a device: the
 * real [SecondScreenPlayFacts.fill] with the app graph's play history, the home screen's result
 * read ([cardResult]) and the emulator A would start (`emulatorToStart`, for every system of the
 * platform data, so `EmulatorChoices.resolve` and its installed-package checks run as for a
 * settled game), against [SecondScreenFeed.FILL_WAIT_MS]. The game ids are not in the library (the
 * play read finds nothing, as for a game never played). Writes nothing it asserts; the numbers go
 * to logcat under [TAG]. Emulator only: `resolve` may store an automatic choice.
 */
@RunWith(AndroidJUnit4::class)
class SecondScreenFillTimingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val graph = (context.applicationContext as PlumApplication).graph

    @Before
    fun onlyOnAnEmulator() = assumeEmulator()

    @Test
    fun fillTimePerSettledGame() =
        runBlocking {
            withContext(Dispatchers.IO) {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val catalog = checkNotNull(graph.catalog()) { "no platform data" }
                val catalogMs = msSince(startedAt)
                val systems = catalog.systems
                val facts =
                    SecondScreenPlayFacts(
                        plays = { graph.database.playDao() },
                        resultOf = graph.homeArt::cardResult,
                        emulatorOf = { id -> graph.emulatorToStart(id, systems[(id % systems.size).toInt()]) },
                    )
                Log.i(TAG, "catalog ${"%.1f".format(catalogMs)} ms, ${systems.size} systems")
                for (round in 1..ROUNDS) {
                    val times =
                        systems.indices.map { i ->
                            val id = FIRST_ID + i
                            val card = SecondScreenContent.Game("Game $id", systems[i].name, gameId = id, isPlayKnown = false, scrapeKey = "timing:$id")
                            val t0 = SystemClock.elapsedRealtimeNanos()
                            facts.fill(card)
                            msSince(t0)
                        }
                    val sorted = times.sorted()
                    Log.i(
                        TAG,
                        "round $round: first ${"%.1f".format(times.first())} ms, median ${"%.1f".format(sorted[sorted.size / 2])}, " +
                            "p90 ${"%.1f".format(sorted[(sorted.size * 9) / 10])}, max ${"%.1f".format(sorted.last())}, " +
                            "over ${SecondScreenFeed.FILL_WAIT_MS} ms: ${times.count { it > SecondScreenFeed.FILL_WAIT_MS }} of ${times.size}",
                    )
                }
            }
        }

    private fun msSince(t0: Long): Double = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

    private companion object {
        const val TAG = "PlumFillTiming"
        const val ROUNDS = 3
        const val FIRST_ID = 900_000L
    }
}
