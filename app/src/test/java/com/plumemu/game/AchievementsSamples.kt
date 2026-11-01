package com.plumemu.game

import com.plumemu.achievements.hash.RaCannotHashReason
import com.plumemu.graph.AchievementLine
import com.plumemu.graph.AchievementsError
import com.plumemu.graph.AchievementsState
import com.plumemu.home.GameRowModel
import com.plumemu.home.HomeContent
import com.plumemu.home.SystemGames
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.EntryKind

/** Info's achievements as the tests and screenshots show them. */
internal object AchievementsSamples {
    const val LONG_TITLE = "The Hunter Who Explored Every Tunnel Of The Planet That Never Sleeps And Came Back Alive"
    const val LONG_DESCRIPTION =
        "Collect every item without taking damage from any boss, in a single play session, " +
            "without using a save state, a cheat or a rewind, and without ever returning to the first area."

    private fun line(
        id: Int,
        title: String,
        description: String,
        points: Int,
        earnedAt: String?,
        hardcoreAt: String? = null,
    ) = AchievementLine(
        id,
        title,
        description,
        points,
        badgeUrl = "https://media.example.test/Badge/$id.png",
        lockedBadgeUrl = "https://media.example.test/Badge/${id}_lock.png",
        earnedAt = earnedAt,
        earnedHardcoreAt = hardcoreAt,
    )

    /** Two earned (one in hardcore), three locked, one earned with a very long title and description. */
    fun set(count: Int = 6): AchievementsState.Ready {
        val all =
            listOf(
                line(1, "Morph Ball", "Find the Morph Ball", 5, "2026-10-30 12:00:00", "2026-10-30 12:00:00"),
                line(2, "Bombs Away", "Find the Bombs and use them on the first wall", 10, "2026-10-31 12:00:00"),
                line(3, LONG_TITLE, LONG_DESCRIPTION, 50, "2026-11-01 12:00:00"),
                line(4, "Ice Beam", "Find the Ice Beam", 10, null),
                line(5, "High Jump", "Find the High Jump Boots", 10, null),
                line(6, "Speed Booster", "Find the Speed Booster and break the block", 25, null),
            ) + (7..12).map { line(it, "Item $it", "Find item number $it", it, null) }
        val shown = all.take(count)
        return AchievementsState.Ready("Super Metroid", shown.size, shown.count { it.isEarned }, shown.count { it.earnedHardcoreAt != null }, shown)
    }

    /** Every state a signed-in Info can show besides a set, with the string its quiet line is. */
    val quietStates: List<AchievementsState> =
        listOf(
            AchievementsState.Loading,
            AchievementsState.NoSet,
            AchievementsState.CannotHash(RaCannotHashReason.entries.first()),
        ) + AchievementsError.entries.map { AchievementsState.Error(it) }

    fun facts(state: AchievementsState?): GameFacts =
        sampleFacts(title = "Super Metroid").copy(systemName = "Super Nintendo", regions = "USA", fileName = "Super Metroid.sfc", achievements = state)

    /** The board's Super Nintendo list with database-style ids (1000 up), for the trophy tests. */
    fun contentWithIds(): HomeContent {
        val board = FakeHomeContent.board()
        val systems =
            board.entries.filter { it.kind == EntryKind.System }.map { entry ->
                val rows: List<GameRowModel> = entry.rows.mapIndexed { index, row -> row.copy(id = (FIRST_ID + index).toString()) }
                SystemGames(entry.id, entry.name.orEmpty(), rows, entry.coverShape, code = entry.code)
            }
        fun rows(kind: EntryKind) = board.entries.first { it.kind == kind }.rows
        return HomeContent.of(rows(EntryKind.Recent), rows(EntryKind.Favorites), rows(EntryKind.Apps), systems, hasBoxArt = true)
    }

    const val FIRST_ID = 1000L
}
