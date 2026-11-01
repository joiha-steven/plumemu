package com.plumemu.achievements.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Turns RetroAchievements' answers into the models. Each throws [UnexpectedAnswer] for an answer
 * of the wrong shape. Only the fields the models hold are read.
 */
internal object RaParsers {

    /** The profile, or null for an empty one (`{}`, `[]`, no user or ULID): an unknown user. */
    fun profile(body: String): RaProfile? {
        val root = parseBody(body)
        if (root is JsonArray && root.isEmpty()) return null
        val obj = root as? JsonObject ?: throw UnexpectedAnswer("profile is not an object")
        val user = obj.text("User")?.takeIf { it.isNotBlank() } ?: return null
        val ulid = obj.text("ULID")?.takeIf { it.isNotBlank() } ?: return null
        return RaProfile(
            userName = user,
            ulid = ulid,
            pictureUrl = obj.text("UserPic")?.takeIf { it.isNotBlank() }?.let(RaMedia::url),
            points = obj.int("TotalPoints") ?: 0,
            softcorePoints = obj.int("TotalSoftcorePoints") ?: 0,
        )
    }

    /** The game list: a top-level array. An entry without an id or title is dropped alone. */
    fun gameList(consoleId: Int, body: String): RaGameList {
        val root = parseBody(body) as? JsonArray ?: throw UnexpectedAnswer("game list is not an array")
        val games = root.objects().mapNotNull { game(it) }
        return RaGameList(consoleId, games)
    }

    fun game(obj: JsonObject): RaGame? {
        val id = obj.int("ID") ?: return null
        val title = obj.text("Title") ?: return null
        return RaGame(
            id = id,
            title = title,
            consoleId = obj.int("ConsoleID") ?: 0,
            achievementCount = obj.int("NumAchievements") ?: 0,
            points = obj.int("Points") ?: 0,
            iconPath = obj.text("ImageIcon")?.takeIf { it.isNotBlank() },
            hashes = obj["Hashes"].texts().map { it.lowercase() },
        )
    }

    /** One game's achievements and the user's counts. */
    fun gameProgress(body: String): RaGameProgress {
        val obj = parseBody(body) as? JsonObject ?: throw UnexpectedAnswer("game progress is not an object")
        val id = obj.int("ID") ?: throw UnexpectedAnswer("game progress has no ID")
        val achievements = obj["Achievements"].objects().mapNotNull(::achievement)
            .sortedWith(compareBy({ it.displayOrder }, { it.id }))
        return RaGameProgress(
            id = id,
            title = obj.text("Title").orEmpty(),
            consoleId = obj.int("ConsoleID") ?: 0,
            iconPath = obj.text("ImageIcon")?.takeIf { it.isNotBlank() },
            achievementCount = obj.int("NumAchievements") ?: achievements.size,
            awarded = obj.int("NumAwardedToUser") ?: 0,
            awardedHardcore = obj.int("NumAwardedToUserHardcore") ?: 0,
            highestAward = AwardKind.from(obj.text("HighestAwardKind")),
            achievements = achievements,
        )
    }

    private fun achievement(obj: JsonObject): RaAchievement? {
        val id = obj.int("ID") ?: return null
        return RaAchievement(
            id = id,
            title = obj.text("Title").orEmpty(),
            description = obj.text("Description").orEmpty(),
            points = obj.int("Points") ?: 0,
            badgeName = obj.text("BadgeName")?.takeIf { it.isNotBlank() },
            displayOrder = obj.int("DisplayOrder") ?: 0,
            earnedAt = obj.text("DateEarned")?.takeIf { it.isNotBlank() },
            earnedHardcoreAt = obj.text("DateEarnedHardcore")?.takeIf { it.isNotBlank() },
        )
    }

    /** One page of the completion list. */
    class CompletionPage(val total: Int, val rowCount: Int, val results: List<RaCompletion>)

    fun completionPage(body: String): CompletionPage {
        val obj = parseBody(body) as? JsonObject ?: throw UnexpectedAnswer("completion page is not an object")
        val total = obj.int("Total") ?: throw UnexpectedAnswer("completion page has no Total")
        val rowCount = (obj["Results"] as? JsonArray)?.size ?: obj["Results"].objects().size
        val results = obj["Results"].objects().mapNotNull { row ->
            val gameId = row.int("GameID") ?: return@mapNotNull null
            RaCompletion(
                gameId = gameId,
                consoleId = row.int("ConsoleID") ?: 0,
                maxPossible = row.int("MaxPossible") ?: 0,
                awarded = row.int("NumAwarded") ?: 0,
                awardedHardcore = row.int("NumAwardedHardcore") ?: 0,
                highestAward = AwardKind.from(row.text("HighestAwardKind")),
                title = row.text("Title").orEmpty(),
            )
        }
        return CompletionPage(total, rowCount, results)
    }
}
