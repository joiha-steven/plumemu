package com.plumemu.achievements.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A RetroAchievements user. Calls after the sign-in name the user by [ulid], which never changes
 * (user names can). [pictureUrl] is a full URL on RetroAchievements' media host.
 */
data class RaProfile(
    val userName: String,
    val ulid: String,
    val pictureUrl: String?,
    val points: Int,
    val softcorePoints: Int,
)

/** One game of a system's list: the MD5 [hashes] RetroAchievements accepts for it. */
data class RaGame(
    val id: Int,
    val title: String,
    val consoleId: Int,
    val achievementCount: Int,
    val points: Int,
    /** Path on the media host, e.g. `/Images/000001.png` ([RaMedia.url]). */
    val iconPath: String?,
    val hashes: List<String>,
)

/** A system's game list, as fetched or as saved. */
data class RaGameList(val consoleId: Int, val games: List<RaGame>)

/** One game's achievements and the user's counts. */
data class RaGameProgress(
    val id: Int,
    val title: String,
    val consoleId: Int,
    val iconPath: String?,
    val achievementCount: Int,
    val awarded: Int,
    val awardedHardcore: Int,
    val highestAward: AwardKind,
    /** Sorted by the site's display order, then id. */
    val achievements: List<RaAchievement>,
)

/** One achievement. The earned dates are RetroAchievements' text, null while it is locked. */
data class RaAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String?,
    val displayOrder: Int,
    val earnedAt: String?,
    val earnedHardcoreAt: String?,
) {
    val isEarned: Boolean get() = earnedAt != null || earnedHardcoreAt != null
}

/** One game the user has progress in, from the completion list. */
data class RaCompletion(
    val gameId: Int,
    val consoleId: Int,
    val maxPossible: Int,
    val awarded: Int,
    val awardedHardcore: Int,
    val highestAward: AwardKind,
    /** The game's title as RetroAchievements writes it; empty when the answer has none. */
    val title: String = "",
)

/** The best award a user has for a game. A value RetroAchievements adds later reads as [Unknown]. */
enum class AwardKind(val raw: String?) {
    None(null),
    BeatenSoftcore("beaten-softcore"),
    BeatenHardcore("beaten-hardcore"),
    Completed("completed"),
    Mastered("mastered"),
    Unknown(null),
    ;

    companion object {
        fun from(raw: String?): AwardKind {
            if (raw.isNullOrBlank()) return None
            return entries.firstOrNull { it.raw != null && it.raw.equals(raw.trim(), ignoreCase = true) } ?: Unknown
        }
    }
}

/** URLs on RetroAchievements' media host. Only URLs: nothing is downloaded here. */
object RaMedia {
    private val BASE: HttpUrl = "https://media.retroachievements.org/".toHttpUrl()

    /** The badge of an earned achievement. */
    fun badge(badgeName: String): String = badgeUrl("$badgeName.png")

    /** The grey badge of a locked achievement. */
    fun badgeLocked(badgeName: String): String = badgeUrl("${badgeName}_lock.png")

    /**
     * A path the API gives (`/UserPic/name.png`, `/Images/000001.png`) as a full URL on the media
     * host; never another host, whatever the path holds.
     */
    fun url(path: String): String {
        val segments = path.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        return BASE.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build().toString()
    }

    private fun badgeUrl(file: String): String =
        BASE.newBuilder().addPathSegment("Badge").addPathSegment(file).build().toString()
}
