package com.plumemu.collections

import com.plumemu.home.HomeContent

/** Where Home, a cold start and the end of setup land. Never Recent or a system. */
sealed interface HomeTarget {
    /** Favorites' first game. */
    data class Game(val entryId: String, val gameId: String) : HomeTarget

    /** The Favorites entry on its empty line. */
    data object EmptyFavorites : HomeTarget
}

fun homeTarget(favoritesFirstGameId: String?): HomeTarget =
    if (favoritesFirstGameId == null) HomeTarget.EmptyFavorites else HomeTarget.Game(HomeContent.FAVORITES_ID, favoritesFirstGameId)
