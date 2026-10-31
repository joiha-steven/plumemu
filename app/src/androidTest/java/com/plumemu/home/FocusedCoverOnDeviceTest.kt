package com.plumemu.home

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.MainActivity
import com.plumemu.assumeEmulator
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The grid's focused cover drawn by the device's own renderer (Robolectric draws in software and
 * cannot show this). Seen on an AYN Thor: covers vanished when the cursor moved onto
 * them. On the low-end emulator every cover focused by a move was blank (27 of 27) until the cover
 * was drawn in place as well as into the grid's focus layer (HomeGrid). Reads real screenshots.
 * Emulator only:
 * `adb shell am instrument -w -e class com.plumemu.home.FocusedCoverOnDeviceTest com.plumemu.test/androidx.test.runner.AndroidJUnitRunner`;
 * the counts go to logcat, tag `FocusedCoverMeasure`.
 */
@RunWith(AndroidJUnit4::class)
class FocusedCoverOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun onlyOnAnEmulator() = assumeEmulator()

    /**
     * The real path: D-pad moves through the grid, rows scroll into view and each newly shown
     * picture decodes for [DECODE_MS] (a slow card), so the focus often lands on a cover still
     * decoding. After each move the focused cover (found inside its accent ring) shows its picture.
     */
    @Test
    fun movingThroughTheGridEveryFocusedCoverShowsItsPicture() {
        val content =
            FakeHomeContent.withArtwork().let { board ->
                board.withEntries(board.entries.map { entry -> entry.copy(rows = entry.rows.map { it.copy(details = null, scrapeKey = "crc:${it.id}") }) })
            }
        val snesIndex = FakeHomeContent.BOARD_SNES_INDEX
        val rows = content.entries[snesIndex].rows
        val art = rows.associate { row -> "crc:${row.id}" to GameArt(ArtPicture("/slow/${row.id}", 560, 400), ArtPicture("/slow/${row.id}", 560, 400), GameDetails(1994, "", "", "", "")) }
        val images = SlowImages()
        val source =
            object : HomeArtSource {
                override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flowOf(art.filterKeys { it in keys })

                override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

                override val images: CoverImages = images
            }
        var state: StripState? = null
        var accent = 0
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                PlumTheme(PlumThemeSettings.Default) {
                    accent = PlumTheme.colors.accent.toArgb()
                    ProvideHomeArt(source, hasBoxArt = true) {
                        val strip = rememberHomeStripState(content, setOf("snes"), isMotionOn = true)
                        // Applied once, before the first frame, as the unit tests' HomeAt does.
                        remember(strip) {
                            val opening = listOf(Action.TapItem(Column.Systems, snesIndex), Action.Enter, Action.TapItem(Column.Games, 0))
                            Snapshot.withoutReadObservation { opening.forEach(strip::dispatch) }
                            opening.size
                        }
                        state = strip
                        HomeLayout(content, strip, onAction = {}, clock = { null }, battery = { null }, gridIds = setOf("snes"))
                    }
                }
            }
        }
        SystemClock.sleep(SETTLE_MS)
        val first = instrumentation.uiAutomation.takeScreenshot()
        Log.i(TAG, "first focused cover: ${ringCentre(first, accent)?.let { (x, y) -> Integer.toHexString(first.getPixel(x, y)) }}")
        val moves = List(8) { Direction.Down } + List(3) { Direction.Right } + List(8) { Direction.Up } + List(2) { Direction.Left } + List(6) { Direction.Down }
        val blank = mutableListOf<String>()
        moves.forEachIndexed { step, direction ->
            instrumentation.runOnMainSync { checkNotNull(state).dispatch(Action.Move(direction)) }
            SystemClock.sleep(SETTLE_MS)
            val shot = instrumentation.uiAutomation.takeScreenshot()
            val inside = ringCentre(shot, accent)
            val pixel = inside?.let { (x, y) -> shot.getPixel(x, y) }
            if (pixel == null || !near(pixel, PICTURE)) {
                blank += "step $step ($direction): ring centre $inside pixel ${pixel?.let(Integer::toHexString)}"
                val folder = instrumentation.targetContext.getExternalFilesDir(null)
                java.io.File(folder, "focused-step-$step.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        Log.i(TAG, "moves=${moves.size} blank=${blank.size} $blank")
        scenario.close()
        assertTrue("every focused cover shows its picture: $blank", blank.isEmpty())
    }

    /** The centre of the accent ring's bounding box on [shot], or null when no ring is drawn. */
    private fun ringCentre(
        shot: Bitmap,
        accent: Int,
    ): Pair<Int, Int>? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        val pixels = IntArray(shot.width * shot.height)
        shot.getPixels(pixels, 0, shot.width, 0, 0, shot.width, shot.height)
        // Right of the systems column, whose current count is also in the accent.
        for (y in 0 until shot.height) {
            for (x in shot.width / 8 until shot.width) {
                if (pixels[y * shot.width + x] == accent) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < 0) return null
        // A quarter up from the bottom: the picture stands on the ring's bottom edge.
        return (left + right) / 2 to (bottom - (bottom - top) / 4)
    }

    /** Pictures decoded in [DECODE_MS] each, then kept in memory, as Coil's cache does. */
    private class SlowImages : CoverImages {
        private val decoded = java.util.Collections.synchronizedSet(HashSet<String>())

        private fun picture(
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply { eraseColor(PICTURE) }.asImageBitmap()

        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? = if ("${picture.path}@$widthPx" in decoded) picture(widthPx, heightPx) else null

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? {
            kotlinx.coroutines.delay(DECODE_MS)
            decoded += "${picture.path}@$widthPx"
            return picture(widthPx, heightPx)
        }
    }

    private fun near(
        a: Int,
        b: Int,
    ): Boolean =
        Math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < 12 &&
            Math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < 12 &&
            Math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < 12

    private companion object {
        const val TAG = "FocusedCoverMeasure"
        const val SETTLE_MS = 800L
        const val PICTURE = 0xFFE0218A.toInt()
        const val DECODE_MS = 150L
    }
}
