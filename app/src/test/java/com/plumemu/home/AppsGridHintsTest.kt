package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Apps grid's bar names every key that acts there: START opens the app's menu, X opens Search. */
class AppsGridHintsTest {
    @Test
    fun theAppsGridOffersMenuAndSearch() {
        val content = FakeHomeContent.board()
        val apps = content.entries.first { it.kind == EntryKind.Apps }
        val actions = hintsFor(content, Column.Games, apps, showsGrid = true).map { it.action }
        assertTrue("START Menu missing: $actions", Action.Menu in actions)
        assertTrue("X Search missing: $actions", Action.Search in actions)
    }
}
