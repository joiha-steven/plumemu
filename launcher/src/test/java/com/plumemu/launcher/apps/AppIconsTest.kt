package com.plumemu.launcher.apps

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.plumemu.launcher.RecordingLog
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class AppIconsTest {
    private val log = RecordingLog()
    private val asked = mutableListOf<ComponentName>()
    private val askedOn = mutableListOf<Thread>()
    private var missing = emptySet<String>()
    private var broken = emptySet<String>()
    private val alpha = LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")
    private val beta = LaunchableApp("Beta", "com.beta", "com.beta.Main")
    private val ioThread = Executors.newSingleThreadExecutor { Thread(it, "icons-io-test") }

    private fun icons(maxBytes: Int = 1024 * 1024, io: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher()) =
        PackageManagerAppIcons(
            iconOf = { component ->
                asked += component
                askedOn += Thread.currentThread()
                when (component.packageName) {
                    in missing -> throw PackageManager.NameNotFoundException(component.packageName)
                    in broken -> throw IllegalStateException("bad resources")
                    else -> ColorDrawable(Color.RED) as Drawable
                }
            },
            maxBytes = maxBytes,
            log = log,
            io = io,
        )

    @After
    fun stop() {
        ioThread.shutdownNow()
    }

    @Test
    fun loadsOnTheIoDispatcherNotTheCallersThread() = runBlocking {
        val caller = Thread.currentThread()
        val result = icons(io = ioThread.asCoroutineDispatcher()).load(alpha, 48)
        assertTrue(result is AppIcon.Picture)
        assertEquals("icons-io-test", askedOn.single().name)
        assertNotEquals(caller, askedOn.single())
    }

    @Test
    fun theBitmapHasTheAskedSize() = runTest {
        val picture = icons().load(alpha, 72) as AppIcon.Picture
        assertEquals(72, picture.bitmap.width)
        assertEquals(72, picture.bitmap.height)
        val small = icons().load(alpha, 24) as AppIcon.Picture
        assertEquals(24, small.bitmap.width)
    }

    @Test
    fun aSecondAskIsAnsweredFromTheCacheWithoutAskingPackageManager() = runTest {
        val icons = icons()
        val first = icons.load(alpha, 48) as AppIcon.Picture
        val second = icons.load(alpha, 48) as AppIcon.Picture
        assertSame(first.bitmap, second.bitmap)
        assertEquals(1, asked.size)
    }

    @Test
    fun cachedAnswersOnlyWhatIsAlreadyLoadedAndNeverAsksPackageManager() = runTest {
        val icons = icons()
        assertEquals(null, icons.cached(alpha, 48))
        assertEquals(0, asked.size)
        val loaded = icons.load(alpha, 48) as AppIcon.Picture
        assertSame(loaded.bitmap, icons.cached(alpha, 48)?.bitmap)
        assertEquals(null, icons.cached(alpha, 96))
        icons.invalidate("com.alpha")
        assertEquals(null, icons.cached(alpha, 48))
        assertEquals(1, asked.size)
        assertEquals(null, AppIcons.Off.cached(alpha, 48))
    }

    /** A failed icon is not asked of PackageManager again until its package changes; then it is, and may now work. */
    @Test
    fun aFailureIsRememberedUntilThePackageChanges() = runTest {
        missing = setOf("com.alpha")
        val icons = icons()
        assertEquals(AppIcon.None, icons.load(alpha, 48))
        assertEquals(AppIcon.None, icons.load(alpha, 48))
        assertEquals("asked once", 1, asked.size)
        missing = emptySet()
        icons.invalidate("com.beta")
        assertEquals("another package's change keeps it", AppIcon.None, icons.load(alpha, 48))
        assertEquals(1, asked.size)
        icons.invalidate("com.alpha")
        assertTrue("asked again after its package changed", icons.load(alpha, 48) is AppIcon.Picture)
        assertEquals(2, asked.size)
    }

    @Test
    fun everyInvalidateRaisesTheGeneration() = runTest {
        val icons = icons()
        assertEquals(0, icons.generation.value)
        icons.invalidate("com.alpha")
        icons.invalidate()
        assertEquals(2, icons.generation.value)
        assertEquals(0, AppIcons.Off.generation.value)
    }

    @Test
    fun anotherSizeIsAnotherEntry() = runTest {
        val icons = icons()
        val a = icons.load(alpha, 48) as AppIcon.Picture
        val b = icons.load(alpha, 96) as AppIcon.Picture
        assertNotSame(a.bitmap, b.bitmap)
        assertEquals(2, asked.size)
    }

    @Test
    fun invalidatingAPackageDropsItsIconsOnly() = runTest {
        val icons = icons()
        val oldAlpha = icons.load(alpha, 48) as AppIcon.Picture
        val oldBeta = icons.load(beta, 48) as AppIcon.Picture
        icons.invalidate("com.alpha")
        assertNotSame(oldAlpha.bitmap, (icons.load(alpha, 48) as AppIcon.Picture).bitmap)
        assertSame(oldBeta.bitmap, (icons.load(beta, 48) as AppIcon.Picture).bitmap)
        assertEquals(3, asked.size)
    }

    @Test
    fun invalidatingEverythingDropsEveryIcon() = runTest {
        val icons = icons()
        icons.load(alpha, 48)
        icons.load(beta, 48)
        icons.invalidate()
        icons.load(alpha, 48)
        icons.load(beta, 48)
        assertEquals(4, asked.size)
    }

    @Test
    fun anUninstalledAppGivesNoIconAndOneLogLine() = runTest {
        missing = setOf("com.alpha")
        val icons = icons()
        assertEquals(AppIcon.None, icons.load(alpha, 48))
        assertEquals(AppIcon.None, icons.load(alpha, 96))
        assertEquals(listOf("W apps: no icon for com.alpha (android.content.pm.PackageManager\$NameNotFoundException)"), log.lines)
    }

    @Test
    fun aFailureWhileLoadingGivesNoIconAndNeverReachesTheCaller() = runTest {
        broken = setOf("com.beta")
        val icons = icons()
        assertEquals(AppIcon.None, icons.load(beta, 48))
        assertTrue(log.lines.single().contains("java.lang.IllegalStateException"))
        assertTrue(icons.load(alpha, 48) is AppIcon.Picture)
    }

    @Test
    fun aRepairedPackageIsLoggedAgainIfItFailsAgain() = runTest {
        missing = setOf("com.alpha")
        val icons = icons()
        icons.load(alpha, 48)
        icons.invalidate("com.alpha")
        icons.load(alpha, 48)
        assertEquals(2, log.lines.size)
    }

    @Test
    fun theCacheStaysUnderItsByteBoundAndDropsTheLeastRecentlyUsed() = runTest {
        // Room for two 32 px icons (4 KB each) but not three.
        val icons = icons(maxBytes = 2 * 32 * 32 * 4)
        icons.load(alpha, 32)
        icons.load(beta, 32)
        icons.load(alpha, 32) // alpha is now the more recent
        icons.load(LaunchableApp("Gamma", "com.gamma", "com.gamma.Main"), 32) // evicts beta
        val before = asked.size
        icons.load(alpha, 32)
        assertEquals(before, asked.size)
        icons.load(beta, 32)
        assertEquals(before + 1, asked.size)
    }

    @Test
    fun theCacheCapIsASixteenthOfTheMemoryClassBetweenOneAndEightMegabytes() {
        val mb = 1024 * 1024
        assertEquals(8 * mb, AppIcons.cacheBytesFor(256))
        assertEquals(8 * mb, AppIcons.cacheBytesFor(128))
        assertEquals(4 * mb, AppIcons.cacheBytesFor(64))
        assertEquals(mb, AppIcons.cacheBytesFor(0))
        assertEquals(AppIcon.None, runBlocking { AppIcons.Off.load(alpha, 48) })
    }
}
