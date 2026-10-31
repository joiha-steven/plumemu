package com.plumemu.sound

/** A [SoundPlayer] that writes down what it was asked to play, and a clock that moves 100 ms per tick. */
internal class Heard {
    val sounds = mutableListOf<UiSound>()
    val volumes = mutableListOf<Float>()

    /** "tick" and "click" in the order the haptics were asked for. */
    val felt = mutableListOf<String>()
    var released = 0
    var now = 1_000L
    val player =
        object : SoundPlayer {
            override fun play(
                sound: UiSound,
                volume: Float,
            ) {
                sounds += sound
                volumes += volume
            }

            override fun release() {
                released++
            }
        }
    private val haptics =
        object : Haptics {
            override fun tick() {
                felt += "tick"
            }

            override fun click() {
                felt += "click"
            }
        }
    val uiSounds = UiSounds(open = { player }, clock = { now }, haptics = haptics).also { it.start() }

    /** Moves the clock past the move throttle, so the next press is not swallowed by it. */
    fun tick() {
        now += 100
    }

    /** What was played since the last call. */
    fun drain(): List<UiSound> = sounds.toList().also { sounds.clear(); volumes.clear() }

    /** The haptics asked for since the last call. */
    fun drainFelt(): List<String> = felt.toList().also { felt.clear() }
}

/** The outcomes a model reported, for tests that look at the model rather than the player. */
internal class Reported {
    val outcomes = mutableListOf<Outcome>()

    fun listenTo(feel: Feel) {
        feel.sink = { outcomes += it }
    }

    fun drain(): List<Outcome> = outcomes.toList().also { outcomes.clear() }
}
