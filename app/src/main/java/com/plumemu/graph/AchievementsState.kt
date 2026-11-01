package com.plumemu.graph

import com.plumemu.achievements.api.RaAchievement
import com.plumemu.achievements.api.RaGameProgress
import com.plumemu.achievements.api.RaMedia
import com.plumemu.achievements.api.RaResult
import com.plumemu.achievements.hash.RaCannotHashReason

/** What a game's Info shows about achievements ([GraphAchievements.progress]). */
internal sealed interface AchievementsState {
    /** Being matched or asked. */
    data object Loading : AchievementsState

    /** No RetroAchievements account (or none readable): Info shows nothing about achievements. */
    data object NotSignedIn : AchievementsState

    /** RetroAchievements does not recognise the file, or knows the game without a set. */
    data object NoSet : AchievementsState

    /** The file cannot be given a RetroAchievements hash ([reason]: its format, an archive, an empty file). */
    data class CannotHash(val reason: RaCannotHashReason) : AchievementsState

    /** The game's system has no RetroAchievements console. */
    data object NotSupported : AchievementsState

    /** Nothing to show this time; [kind] says why. */
    data class Error(val kind: AchievementsError) : AchievementsState

    /** The game's set and the person's progress in it. */
    data class Ready(
        val gameTitle: String,
        val total: Int,
        /** Unlocked in either mode (a hardcore unlock counts here too). */
        val awardedSoftcore: Int,
        val awardedHardcore: Int,
        val achievements: List<AchievementLine>,
        /** The game was found by its name, not its file's hash (a format Plumemu cannot hash): Info says so. */
        val matchedByName: Boolean = false,
    ) : AchievementsState
}

internal enum class AchievementsError {
    /** RetroAchievements refused the key (reset on the site, or it cannot be decrypted here): sign in again. */
    WrongKey,
    NoConnection,

    /** RetroAchievements answered with a server error. */
    Busy,
    RateLimited,

    /** An answer that could not be read, or a user RetroAchievements no longer knows. */
    BadAnswer,

    /** The game is gone from the library or its file cannot be read now (card removed, access revoked). */
    GameUnavailable,
}

/** One achievement as Info lists it. Dates are RetroAchievements' text; both null while it is locked. */
internal data class AchievementLine(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val lockedBadgeUrl: String?,
    val earnedAt: String?,
    val earnedHardcoreAt: String?,
) {
    val isEarned: Boolean get() = earnedAt != null || earnedHardcoreAt != null
}

/** A game's stored progress as one line shows it: [earned] of [total] (a hardcore unlock counts in softcore too, as Info's summary takes the larger). */
internal data class AchievementCount(val earned: Int, val total: Int)

/** After a game: [count] achievements unlocked since the last stored progress of [gameTitle]. */
internal data class NewAchievements(val gameId: Long, val count: Int, val gameTitle: String)

internal fun errorOf(failure: RaResult.Failure): AchievementsError = when (failure) {
    RaResult.WrongKey -> AchievementsError.WrongKey
    RaResult.NoConnection -> AchievementsError.NoConnection
    is RaResult.Busy -> AchievementsError.Busy
    is RaResult.RateLimited -> AchievementsError.RateLimited
    RaResult.UnknownUser, is RaResult.BadAnswer -> AchievementsError.BadAnswer
}

/** One short cause for a log line: never the key, the user or a URL (failures carry none). */
internal fun causeOf(failure: RaResult.Failure): String = when (failure) {
    RaResult.WrongKey -> "key refused"
    RaResult.NoConnection -> "no connection"
    is RaResult.Busy -> "server error ${failure.code}"
    is RaResult.RateLimited -> "rate limited"
    RaResult.UnknownUser -> "unknown user"
    is RaResult.BadAnswer -> "unexpected answer"
}

internal fun readyOf(progress: RaGameProgress): AchievementsState.Ready =
    AchievementsState.Ready(
        gameTitle = progress.title,
        total = progress.achievementCount,
        awardedSoftcore = progress.awarded,
        awardedHardcore = progress.awardedHardcore,
        achievements = progress.achievements.map(::lineOf),
    )

private fun lineOf(achievement: RaAchievement) = AchievementLine(
    id = achievement.id,
    title = achievement.title,
    description = achievement.description,
    points = achievement.points,
    badgeUrl = achievement.badgeName?.let(RaMedia::badge),
    lockedBadgeUrl = achievement.badgeName?.let(RaMedia::badgeLocked),
    earnedAt = achievement.earnedAt,
    earnedHardcoreAt = achievement.earnedHardcoreAt,
)
