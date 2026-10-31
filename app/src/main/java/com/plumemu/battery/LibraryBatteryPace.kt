package com.plumemu.battery

import com.plumemu.coredata.db.PlayDao
import com.plumemu.graph.AppGraph
import com.plumemu.launcher.play.battery.UsualPace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A game's usual battery pace (the last plays measured: `PlayDao.usualDraw` over this device's full
 * charge, [UsualPace]), or null for a game never measured. Read beside the game's other play facts,
 * off the main thread, once per rested game; what the database throws reaches the caller, which
 * handles it as for the other facts.
 */
internal class LibraryBatteryPace(
    /** The play history; called on [io], so opening the database stays off the main thread. */
    private val plays: () -> PlayDao,
    private val usualPace: UsualPace,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun read(gameId: Long): BatteryPace? =
        withContext(io) {
            val draw = plays().usualDraw(gameId) ?: return@withContext null
            BatteryPace.of(usualPace.of(draw), draw)
        }
}

/**
 * The pace again for a usual draw already read ([BatteryPaceNow]): the counter and level now, on [io]; no
 * database. [UsualPace] is shared and locked inside, so a read here and one for the next game may overlap.
 */
internal class LibraryBatteryPaceNow(
    private val usualPace: UsualPace,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BatteryPaceNow {
    override suspend fun of(usualDrawMicroamps: Long): BatteryPace? = withContext(io) { BatteryPace.of(usualPace.of(usualDrawMicroamps), usualDrawMicroamps) }
}

/** [gameId]'s usual battery pace from [AppGraph]'s database and battery reader. */
internal suspend fun AppGraph.batteryPaceOf(gameId: Long): BatteryPace? = LibraryBatteryPace(plays = { database.playDao() }, usualPace = usualPace).read(gameId)

/** The pace now for the picking line from [AppGraph]'s battery reader (stateless: one per screen is fine). */
internal fun AppGraph.batteryPaceNow(): BatteryPaceNow = LibraryBatteryPaceNow(usualPace)
