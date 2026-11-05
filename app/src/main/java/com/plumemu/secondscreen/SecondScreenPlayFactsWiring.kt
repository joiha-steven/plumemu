package com.plumemu.secondscreen

import com.plumemu.battery.batteryPaceOf
import com.plumemu.game.emulatorToStart
import com.plumemu.game.launchedFileName
import com.plumemu.graph.AppGraph

/**
 * The second screen's play facts over [AppGraph], read off the main thread after the settle.
 * Benchmark builds read play history only: no art, emulator, file, battery pace or achievements.
 */
internal fun AppGraph.secondScreenPlayFacts(
    isBenchmarkBuild: Boolean,
    hasArtRoom: () -> Boolean,
): SecondScreenPlayFacts =
    SecondScreenPlayFacts(
        plays = { database.playDao() },
        // As the home screen's.
        resultOf = if (isBenchmarkBuild) null else homeArt::cardResult,
        // The emulator A would start.
        emulatorOf = if (isBenchmarkBuild) null else { id -> emulatorToStart(id) },
        // The file Play launches (a chosen version's), as Info's "File" row.
        fileNameOf = if (isBenchmarkBuild) null else { id -> launchedFileName(id) },
        // The game's usual battery pace for the battery line.
        paceOf = if (isBenchmarkBuild) null else { id -> batteryPaceOf(id) },
        // The stored RetroAchievements progress, points and latest unlocks for the game card: the database only.
        achievementsOf = if (isBenchmarkBuild) null else { id -> achievements.storedFactsOf(id) },
        // The system column's cards: stored figures only.
        entries = if (isBenchmarkBuild) null else secondScreenEntryReads(),
        isArtShown = hasArtRoom,
    )
