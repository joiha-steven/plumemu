package com.plumemu.launcher.play

import com.plumemu.coredata.db.SessionBattery

/**
 * The battery measurement of the play in progress. The app's meter measures while its
 * in-game card is up; the launcher only marks where a play starts and takes what it measured when
 * the play is recorded, so the figures land in the same `play_session` row. Both calls must be
 * quick and must not throw: they run on the launcher's I/O thread.
 */
interface PlayBattery {
    /**
     * A game is about to start: forget anything measured before it. [usualDrawMicroamps] is the
     * game's usual pace (`PlayDao.usualDraw`), null for a game never measured.
     */
    fun onPlayStarting(usualDrawMicroamps: Long?)

    /** What was measured since [onPlayStarting], forgotten by this call; null when nothing counts. */
    fun takeMeasured(): SessionBattery?

    companion object {
        /** Measures nothing: devices and tests without the app's meter. */
        val None: PlayBattery =
            object : PlayBattery {
                override fun onPlayStarting(usualDrawMicroamps: Long?) = Unit

                override fun takeMeasured(): SessionBattery? = null
            }
    }
}
