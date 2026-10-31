package com.plumemu.library.emulator

import com.plumemu.coredata.db.EmulatorChoiceDao

/**
 * The default emulator of each system: the first installed emulator in the platform data's
 * priority list, by emulator id. The installed set comes from launcher's detection; this file only decides.
 */
object DefaultEmulator {
    fun pick(priority: List<String>, installed: Set<String>): String? = priority.firstOrNull { it in installed }
}

/** Stores the automatic defaults; a system whose emulator the person chose keeps that choice. */
class EmulatorDefaults(private val dao: EmulatorChoiceDao) {
    /**
     * For each system in [priorities] (system id → emulator ids, best first), stores the picked
     * default and returns the emulator id in force per system (null when none is installed).
     */
    suspend fun apply(priorities: Map<String, List<String>>, installed: Set<String>): Map<String, String?> =
        priorities.mapValues { (systemId, priority) ->
            dao.setAutomaticSystemDefault(systemId, DefaultEmulator.pick(priority, installed))?.emulatorId
        }
}
