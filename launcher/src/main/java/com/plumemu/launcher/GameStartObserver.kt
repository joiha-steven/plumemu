package com.plumemu.launcher

/**
 * Told when an emulator (or an app of the Apps entry, `InstalledApps.launch`) is really being
 * started: [beforeStart] right before `startActivity` (after the emulator, the file and the Intent
 * are settled), [startFailed] when that call threw. The app's second screen uses it to tell a stop
 * for a game from leaving Plumemu. Called on the launcher's I/O thread for games and on the
 * caller's thread for apps; must be quick and must not throw.
 */
interface GameStartObserver {
    fun beforeStart()

    fun startFailed()

    companion object {
        val None: GameStartObserver =
            object : GameStartObserver {
                override fun beforeStart() = Unit

                override fun startFailed() = Unit
            }
    }
}

/** [this] starter, telling [observer] just before each start and when a start throws. */
internal fun ActivityStarter.observedBy(observer: GameStartObserver): ActivityStarter =
    ActivityStarter { intent ->
        observer.beforeStart()
        try {
            start(intent)
        } catch (e: Throwable) {
            observer.startFailed()
            throw e
        }
    }
