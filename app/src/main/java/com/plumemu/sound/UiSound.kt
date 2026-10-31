package com.plumemu.sound

/** The three interface sounds (res/raw, made by tools/sound/make_sounds.py). */
enum class UiSound { Move, Confirm, Back }

/** Plays [UiSound]s. Must never block: it is called while input is handled. */
interface SoundPlayer {
    /** Starts [sound] at [volume] (0..1 of the pool's volume) and returns at once. A sound that is not ready is skipped. */
    fun play(
        sound: UiSound,
        volume: Float,
    )

    /** Gives back everything held (the pool and its decoded samples). */
    fun release()
}
