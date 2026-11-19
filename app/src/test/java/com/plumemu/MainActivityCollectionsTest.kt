package com.plumemu

import androidx.lifecycle.Lifecycle
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.collections
import com.plumemu.home.drainMainLooper
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Collections wiring `MainActivity` gives the home screen (`HomeCallbacks.collections`, which the game menu
 * reads for Add to collection…, New collection… and Remove from a collection), on the real graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityCollectionsTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private val app = activity.application as PlumApplication

    @After
    fun tearDown() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.pause().stop()
        controller.destroy()
        drainMainLooper()
    }

    @Test
    fun theHomeCallbacksCarryTheCollections() {
        assertNotNull("the game menu has collections to offer", activity.homeCallbacks.collections)
    }

    @Test
    fun aCollectionCreatedThroughTheCallbacksIsInTheGraphsOwnCollections() {
        val collections = checkNotNull(activity.homeCallbacks.collections)

        val id = onMainLoop { collections.create("RPG") }

        val own = onMainLoop { app.graph.collections.own().first { rows -> rows.any { it.id == id } } }
        assertEquals(listOf("RPG"), own.map { it.name })
    }
}
