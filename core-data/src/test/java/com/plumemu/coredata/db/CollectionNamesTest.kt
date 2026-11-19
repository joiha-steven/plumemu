package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionNamesTest {
    private val db = inMemoryDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun theRuleIsOneToFortyCodePointsTrimmed() {
        assertEquals("RPG", CollectionNames.checked("  RPG "))
        assertFalse(CollectionNames.isValid("   "))
        assertTrue(CollectionNames.isValid("a".repeat(40)))
        assertFalse(CollectionNames.isValid("a".repeat(41)))
        assertTrue("40 surrogate pairs are 40 characters", CollectionNames.isValid("\uD83D\uDE00".repeat(40)))
        assertThrows(IllegalArgumentException::class.java) { CollectionNames.checked("") }
    }

    @Test
    fun aNameInUseGetsANumberAndStillFits() {
        assertEquals("RPG", CollectionNames.unique("RPG", listOf("Hacks")))
        assertEquals("RPG 2", CollectionNames.unique("RPG", listOf("rpg")))
        assertEquals("RPG 3", CollectionNames.unique("RPG", listOf("RPG", "RPG 2")))
        val long = "a".repeat(40)
        val made = CollectionNames.unique(long, listOf(long))
        assertEquals(40, made.length)
        assertTrue(made.endsWith(" 2"))
    }

    @Test
    fun createWithMakesTheCollectionAndHoldsTheGameOrMakesNothing() = runTest {
        val library = ScannedLibrary(db)
        library.pickFolder()
        val id = library.scanIn(game(library.folderId, "a.sfc")).getValue("a.sfc")
        val made = checkNotNull(db.collectionDao().createWith(" Backlog ", id, now = 5))
        assertEquals(listOf(made), db.collectionDao().observeCollectionsOf(id).first())
        assertEquals(listOf("Backlog"), db.collectionDao().observeCollections().first().map { it.name })
        assertNull(db.collectionDao().createWith("Ghost", id + 100))
        assertEquals(1, db.collectionDao().observeCollections().first().size)
        val blank = runCatching { db.collectionDao().createWith(" ", id) }.exceptionOrNull()
        assertTrue(blank is IllegalArgumentException)
        assertEquals(1, db.collectionDao().observeCollections().first().size)
    }
}
