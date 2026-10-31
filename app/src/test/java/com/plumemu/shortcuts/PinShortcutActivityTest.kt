package com.plumemu.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.plumemu.PinShortcutActivity
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The real confirm Activity: any app can send it an explicit intent, so a forged extra must end in
 * "can no longer be added", never a crash of the home app; and a recreate in the middle of an add
 * keeps the add and closes when it is done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PinShortcutActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [28])
    fun aBundleWhereAndroidPutsItsRequestIsRefusedWithoutACrash() {
        val forged =
            Intent(CONFIRM_PIN_SHORTCUT)
                .setClass(context, PinShortcutActivity::class.java)
                .putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, Bundle().apply { putString("package", "app.gamenative") })
        val controller = Robolectric.buildActivity(PinShortcutActivity::class.java, forged).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val state = ViewModelProvider(controller.get())[PinConfirmModel::class.java].state
        assertEquals(PinScreen.Message(PinMessage.Expired), state.screen)
        state.onAction(Action.Enter)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun aRecreateDuringTheAddKeepsItAndClosesWhenItIsDone() {
        val answer = CompletableDeferred<PinAddOutcome>()
        FakePinActivity.actions = FakeActions(answer)
        val controller = Robolectric.buildActivity(FakePinActivity::class.java, Intent(CONFIRM_PIN_SHORTCUT)).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val before = ViewModelProvider(controller.get())[PinConfirmModel::class.java].state
        assertEquals(PinScreen.Asking("Broforce", "Steam"), before.screen)
        before.onAction(Action.Enter)

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        val after = ViewModelProvider(controller.get())[PinConfirmModel::class.java].state
        assertSame("the new Activity shows the same screen", before, after)
        assertEquals(PinScreen.Asking("Broforce", "Steam", isAdding = true), after.screen)
        assertEquals("the request was read once", 1, (FakePinActivity.actions as FakeActions).offers.get())
        assertFalse(controller.get().isFinishing)

        answer.complete(PinAddOutcome.Added(7, isNew = true))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(after.isClosed)
        assertTrue("the new Activity closes", controller.get().isFinishing)
        assertEquals(1, (FakePinActivity.actions as FakeActions).adds.get())
    }

    private class FakeActions(private val answer: CompletableDeferred<PinAddOutcome>) : PinConfirmActions {
        val offers = AtomicInteger()
        val adds = AtomicInteger()

        override suspend fun offer(): PinOffer {
            offers.incrementAndGet()
            return PinOffer.Ask(FakePinRequest(gameNativeShortcut()), gameNativeShortcut(), "Broforce", "steam", "Steam")
        }

        override suspend fun add(offer: PinOffer.Ask): PinAddOutcome {
            adds.incrementAndGet()
            return answer.await()
        }
    }

    private companion object {
        const val CONFIRM_PIN_SHORTCUT = "android.content.pm.action.CONFIRM_PIN_SHORTCUT"
    }
}

/** The confirm Activity with the test's actions (a recreated one is made by Robolectric, so they live here). */
class FakePinActivity : PinShortcutActivity() {
    override fun pinActions(request: Intent): PinConfirmActions = actions

    companion object {
        lateinit var actions: PinConfirmActions
    }
}
