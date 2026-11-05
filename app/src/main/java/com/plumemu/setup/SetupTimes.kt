package com.plumemu.setup

import com.plumemu.telemetry.SetupAction
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.TelemetrySink

/** The statistics of setup's steps: when each was left and when setup ended (null sink: nothing is sent). */
internal class SetupTimes(
    private val telemetry: TelemetrySink?,
    private val clock: () -> Long,
) {
    private val setupSince = clock()
    private var stepSince = setupSince

    /** `setup_step`: [step] was left by [action] after the time since the last step was left. */
    fun stepLeft(
        step: SetupStep,
        action: SetupAction,
    ) {
        val now = clock()
        val seconds = ((now - stepSince) / MILLIS_PER_SECOND).coerceAtLeast(0)
        stepSince = now
        setupStepIdOf(step)?.let { telemetry?.track(TelemetryEvent.SetupStep(it, seconds, action)) }
    }

    /** Start playing: the last step and `setup_completed` (counts only, never a folder's or a system's name). */
    fun completed(now: SetupUiState) {
        stepLeft(SetupStep.Ready, SetupAction.Next)
        val seconds = ((clock() - setupSince) / MILLIS_PER_SECOND).coerceAtLeast(0)
        telemetry?.track(
            TelemetryEvent.SetupCompleted(
                seconds = seconds,
                artwork = now.isBoxArtComing,
                folders = now.folders.size,
                systemsFound = now.systems.size,
                emulatorsPicked = now.emulators?.systemsWithEmulator ?: 0,
            ),
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
