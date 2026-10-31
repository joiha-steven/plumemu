package com.plumemu.home

import android.os.Looper
import androidx.compose.runtime.LaunchedEffect
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The test host runs effects the way the app does: a coroutine that left for a background thread
 * comes back to the main thread before it touches state. With compose-test's v1 rule it came back
 * on the background thread (an unconfined dispatcher), so state written there raced the
 * composition: search's count once never appeared in HomeFavoriteSearchTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ComposeHostRuleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun anEffectBackFromABackgroundThreadRunsOnTheMainThread() {
        var resumedOn: Thread? = null
        compose.setContent {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) { Thread.sleep(BACKGROUND_WORK_MS) }
                resumedOn = Thread.currentThread()
            }
        }
        compose.waitUntil(TIMEOUT_MS) { resumedOn != null }
        assertSame(Looper.getMainLooper().thread, resumedOn)
    }

    private companion object {
        const val BACKGROUND_WORK_MS = 20L
        const val TIMEOUT_MS = 5_000L
    }
}
