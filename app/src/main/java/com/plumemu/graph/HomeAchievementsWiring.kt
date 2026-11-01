package com.plumemu.graph

import com.plumemu.home.HomeAchievements
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

/**
 * What the main screen shows of RetroAchievements. The flows reach [AppGraph.achievements] only when
 * collected, and signed out they end at the account check: no client, hasher, database query or request.
 */
internal fun AppGraph.homeAchievements(): HomeAchievements =
    HomeAchievements(
        mastered = flow { emitAll(achievements.masteredGames) },
        newAchievements = flow { emitAll(achievements.newAchievements) },
        badges = achievementBadges,
    )
