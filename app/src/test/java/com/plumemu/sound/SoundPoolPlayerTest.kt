package com.plumemu.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where the sound pool lives: never on the calling (main) thread, and never fatal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SoundPoolPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aPoolThatCannotBeMadeLeavesThePlayerSilentAndNothingThrows() {
        val thread = Executors.newSingleThreadExecutor()
        val player = SoundPoolPlayer(context, thread) { throw IllegalStateException("no audio") }
        player.play(UiSound.Move, 1f)
        player.play(UiSound.Confirm, 1f)
        player.release()
        assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun aPlayerThatCannotStartLeavesUiSoundsSilentAndTheAppRunning() {
        val broken = UiSounds(open = { throw IllegalStateException("no audio") })
        broken.start()
        broken.playFor(Outcome.Activated)
        broken.stop()
    }

    @Test
    fun creatingAndPlayingRunOnTheSoundsThreadNotOnTheCaller() {
        val caller = Thread.currentThread()
        val ranOn = mutableListOf<Thread>()
        val recording: ExecutorService = Executors.newSingleThreadExecutor()
        val onThread = Executor { task -> recording.execute { ranOn += Thread.currentThread(); task.run() } }
        val player =
            SoundPoolPlayer(
                context,
                object : java.util.concurrent.AbstractExecutorService() {
                    override fun execute(command: Runnable) = onThread.execute(command)

                    override fun shutdown() = recording.shutdown()

                    override fun shutdownNow(): MutableList<Runnable> = recording.shutdownNow()

                    override fun isShutdown() = recording.isShutdown

                    override fun isTerminated() = recording.isTerminated

                    override fun awaitTermination(timeout: Long, unit: TimeUnit) = recording.awaitTermination(timeout, unit)
                },
            ) { throw IllegalStateException("stop after the thread was used") }
        player.play(UiSound.Move, 1f)
        player.release()
        assertTrue(recording.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals("creation, play and release all queued", 3, ranOn.size)
        assertTrue(ranOn.none { it === caller })
    }
}
