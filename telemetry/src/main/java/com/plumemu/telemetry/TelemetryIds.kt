package com.plumemu.telemetry

import com.plumemu.platforms.Emulator
import com.plumemu.platforms.GameSystem

/**
 * A system as the events send it (`system_id`): made only from the platform data's [GameSystem],
 * so a game title or file name cannot be passed by mistake.
 */
@JvmInline
public value class SystemRef internal constructor(
    public val id: String,
) {
    public companion object {
        public fun of(system: GameSystem): SystemRef = SystemRef(system.id)
    }
}

/** An emulator as the events send it (`emulator_id`): made only from the platform data's [Emulator]. */
@JvmInline
public value class EmulatorRef internal constructor(
    public val id: String,
) {
    public companion object {
        public fun of(emulator: Emulator): EmulatorRef = EmulatorRef(emulator.id)
    }
}

/** A version name of Plumemu (`0.0.3-beta`, `1.2.0`, `1.0.0-rc.2`); anything else is not sent. */
internal val VERSION_NAME = Regex("""\d{1,4}\.\d{1,4}\.\d{1,4}(-[a-z]{1,12}(\.?\d{1,4})?)?""")
