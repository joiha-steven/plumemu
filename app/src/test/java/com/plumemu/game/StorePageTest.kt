package com.plumemu.game

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Get it": the store app first, the Play page in a browser next, and a false answer when neither opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorePageTest {
    private val tried = mutableListOf<String>()

    private fun opener(vararg failing: String): (Intent) -> Unit = { intent ->
        val uri = intent.data.toString()
        tried += uri
        if (failing.any { uri.startsWith(it) }) throw ActivityNotFoundException(uri)
    }

    @Test
    fun theStoreAppOpensFirst() {
        assertTrue(openStorePage("com.github.stenzek.duckstation", opener()))
        assertEquals(listOf("market://details?id=com.github.stenzek.duckstation"), tried)
    }

    @Test
    fun withoutAStoreTheWebPageOpens() {
        assertTrue(openStorePage("org.ppsspp.ppsspp", opener("market:")))
        assertEquals(storePageUris("org.ppsspp.ppsspp"), tried)
        assertEquals("https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp", tried.last())
    }

    @Test
    fun withNeitherTheAnswerIsFalse() {
        assertFalse(openStorePage("org.ppsspp.ppsspp", opener("market:", "https:")))
        assertEquals(2, tried.size)
    }
}
