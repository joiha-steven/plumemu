package com.plumemu.sound

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SoundLevel

/**
 * What screens call to make the interface sounds. Owns the player only between [start] and [stop]
 * (the Activity's onStart and onStop), so nothing is held in the background or while an emulator
 * runs. [level] and [vibration] follow Settings › Advanced › Sounds and Vibration ([follow]). Calls come
 * from the main thread and cost nothing there: the player works on its own thread. The haptic tick for
 * a move and the click for a confirm are made here too, so what is heard and what is felt stay in one place.
 */
class UiSounds(
    private val open: () -> SoundPlayer,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val haptics: Haptics = Haptics.None,
) {
    @Volatile
    var level: SoundLevel = SoundLevel.Loud

    @Volatile
    var vibration: Boolean = true

    fun follow(advanced: AdvancedSettings) {
        level = advanced.sounds
        vibration = advanced.vibration
    }

    private var player: SoundPlayer? = null
    private var lastMoveAt = -MOVE_GAP_MS
    private var lastTickAt = -TICK_GAP_MS

    fun start() {
        if (player != null) return
        // Never fatal: a crash in onStart would loop while Plumemu is the home screen.
        player =
            try {
                open()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Sounds are off: the player could not start (${e.javaClass.simpleName})")
                null
            }
    }

    fun stop() {
        player?.release()
        player = null
    }

    fun play(sound: UiSound) {
        feel(sound)
        val current = player ?: return
        if (level == SoundLevel.Off) return
        if (sound == UiSound.Move) {
            // A held D-pad repeats faster than a tick can be told apart; one tick per gap.
            val now = clock()
            if (now - lastMoveAt < MOVE_GAP_MS) return
            lastMoveAt = now
        }
        current.play(sound, level.volume)
    }

    /** The haptic for [sound]: a light tick for a move (at most one per [TICK_GAP_MS]), a click for a confirm; none for back. */
    private fun feel(sound: UiSound) {
        if (!vibration) return
        when (sound) {
            UiSound.Move -> {
                val now = clock()
                if (now - lastTickAt < TICK_GAP_MS) return
                lastTickAt = now
                haptics.tick()
            }
            UiSound.Confirm -> haptics.click()
            UiSound.Back -> Unit
        }
    }

    fun playFor(outcome: Outcome) {
        outcome.sound?.let(::play)
    }

    companion object {
        private const val TAG = "UiSounds"

        /** At most one move tick per this many milliseconds. */
        const val MOVE_GAP_MS = 50L

        /** At most one haptic tick per this many milliseconds, so a held D-pad does not buzz continuously. */
        const val TICK_GAP_MS = 35L

        /** The real thing: the sound pool and the vibrator, both made on first use from [context] (an Activity has none while its fields are made). */
        internal fun onDevice(context: () -> Context): UiSounds =
            UiSounds(open = { SoundPoolPlayer(context()) }, haptics = lazyHaptics { VibratorHaptics(context()) })

        /** Never started: every call is a no-op. The default for screens hosted without sound (tests). */
        val Silent = UiSounds(open = { error("Silent UiSounds is never started") })
    }
}

val LocalUiSounds = staticCompositionLocalOf { UiSounds.Silent }
