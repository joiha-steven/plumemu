package com.plumemu.graph

/**
 * A game's RetroAchievements as last stored, for the second screen's game card: the counts of [AchievementCount], the points when
 * stored (both or neither), and the latest unlocked achievements ([recent], newest first). Read from the database only.
 */
internal data class StoredAchievements(
    val earned: Int,
    val total: Int,
    val pointsEarned: Int? = null,
    val pointsTotal: Int? = null,
    val recent: List<StoredBadge> = emptyList(),
) {
    val count: AchievementCount get() = AchievementCount(earned, total)

    /** "410 / 680": null unless both points are stored. */
    val points: Pair<Int, Int>? get() = if (pointsEarned != null && pointsTotal != null) pointsEarned to pointsTotal else null
}

/** One unlocked achievement: its [title], its badge address (null when the list gave none) and when it was unlocked (epoch ms). */
internal data class StoredBadge(
    val title: String,
    val url: String?,
    val earnedAt: Long,
)
