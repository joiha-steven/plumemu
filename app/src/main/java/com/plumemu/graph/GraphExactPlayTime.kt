package com.plumemu.graph

import android.content.Context
import com.plumemu.coredata.settings.SettingsRepository
import com.plumemu.launcher.play.ForegroundTime
import com.plumemu.launcher.play.UsageStatsForegroundTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Settings › Statistics › Exact play time: null (the launcher's plain count) unless the person switched it on, else [delegate]
 * (Android's Usage access, which itself answers null without the grant). The switch is read fresh on each ask: the launcher
 * calls from its IO thread when a play ends, never the main thread.
 */
internal class ExactPlayTimeGate(
    private val isOn: () -> Boolean,
    private val delegate: ForegroundTime,
) : ForegroundTime {
    override fun inFrontMs(
        packageName: String,
        fromWallMs: Long,
        toWallMs: Long,
    ): Long? = if (isOn()) delegate.inFrontMs(packageName, fromWallMs, toWallMs) else null
}

/** Blocking read of the switch for [ExactPlayTimeGate]: a failed read counts as off (one log line, kind only); cancellation passes through. */
internal fun exactPlayTimeSwitch(
    read: suspend () -> Boolean,
    log: (String) -> Unit,
): () -> Boolean =
    {
        try {
            runBlocking { read() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Exact play time: the switch could not be read (${e.javaClass.simpleName}); plain count used")
            false
        }
    }

internal fun exactPlayTimeFor(
    app: Context,
    settings: SettingsRepository,
    log: (String) -> Unit,
): ForegroundTime =
    ExactPlayTimeGate(
        isOn = exactPlayTimeSwitch({ settings.readOnce().advanced.exactPlayTime }, log),
        delegate = UsageStatsForegroundTime(app),
    )
