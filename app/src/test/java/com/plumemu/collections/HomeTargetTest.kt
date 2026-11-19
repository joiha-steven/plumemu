package com.plumemu.collections

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTargetTest {
    @Test
    fun landsOnFavoritesFirstGame() {
        assertEquals(HomeTarget.Game("favorites", "42"), homeTarget("42"))
    }

    @Test
    fun noFavoritesLandsOnTheFavoritesEntry() {
        assertEquals(HomeTarget.EmptyFavorites, homeTarget(null))
    }
}
