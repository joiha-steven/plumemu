package com.plumemu.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.plumemu.R
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [SoundPool] with the three sounds, all on its own thread: creating the pool, loading, playing
 * and releasing never run on the main thread, so neither startup nor input waits for the audio
 * system (on Android 6 `play` can create an AudioTrack on the calling thread). [play] only queues
 * work. A sound asked for before its load finished is skipped (no queue, no error). If the pool
 * cannot be made, one line is logged and the player stays silent. Release it in onStop; nothing
 * stays decoded in memory (about 14 KB).
 */
class SoundPoolPlayer(
    context: Context,
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "plumemu-sounds").apply { isDaemon = true } },
    private val newPool: () -> SoundPool = ::buildPool,
) : SoundPlayer {
    // The pool and ids belong to the sounds thread; the load listener may answer on the main
    // looper (the pool is made on a thread without one), so the ready set is a concurrent one.
    private var pool: SoundPool? = null
    private val ids = HashMap<UiSound, Int>()
    private val ready: MutableSet<Int> = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    init {
        val appContext = context.applicationContext
        thread.execute {
            try {
                val made = newPool()
                made.setOnLoadCompleteListener { _, id, status ->
                    if (status == 0) ready += id else Log.w(TAG, "Sound $id did not load (status $status)")
                }
                ids[UiSound.Move] = made.load(appContext, R.raw.ui_move, 1)
                ids[UiSound.Confirm] = made.load(appContext, R.raw.ui_confirm, 1)
                ids[UiSound.Back] = made.load(appContext, R.raw.ui_back, 1)
                pool = made
            } catch (e: RuntimeException) {
                Log.w(TAG, "Sounds are off: the sound pool could not start (${e.javaClass.simpleName})")
            }
        }
    }

    override fun play(
        sound: UiSound,
        volume: Float,
    ) {
        thread.execute {
            val id = ids[sound] ?: return@execute
            if (id in ready) pool?.play(id, volume, volume, 1, 0, 1f)
        }
    }

    override fun release() {
        thread.execute {
            pool?.release()
            pool = null
            ready.clear()
        }
        thread.shutdown()
    }

    private companion object {
        const val TAG = "SoundPoolPlayer"
        const val MAX_STREAMS = 2

        fun buildPool(): SoundPool =
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // The media volume, not the system stream: Do Not Disturb and the ringer mute
                        // USAGE_ASSISTANCE_SONIFICATION, which made every sound silent on the Thor.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
    }
}
