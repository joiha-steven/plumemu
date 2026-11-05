package com.plumemu.graph

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The cores answered No survive Plumemu being killed while the offered core runs: the store is on disk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CoreTriesStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gpsp = CoreToConfirm("com.retroarch", "gpsp_libretro_android.so")
    private val play = AddedSession(sessionId = 5, gameId = 7, endedAt = 1, seconds = 60, previousLastPlayedAt = null)

    @Test
    fun aCoreAnsweredNoIsStillNotOfferedAfterTheProcessIsRecreated() {
        // The first process notes the offer; a second one (new store, new notices) posts the question after the kill.
        LibraryNotices(SharedPreferencesCoreTries(context)).expectCoreTry("7", "retroarch-gpsp", setOf("retroarch-mgba"))
        val reborn = LibraryNotices(SharedPreferencesCoreTries(context))
        reborn.askAboutCore("7", "Advance Wars", "retroarch-gpsp", "RetroArch: gpSP", gpsp, play)
        assertEquals(setOf("retroarch-mgba"), reborn.coreQuestion.value!!.tried)
    }

    @Test
    fun theNoteIsClearedWhenItsQuestionTakesIt() {
        SharedPreferencesCoreTries(context).expect("8", "retroarch-gpsp", setOf("retroarch-mgba"))
        assertEquals(setOf("retroarch-mgba"), SharedPreferencesCoreTries(context).take("8", "retroarch-gpsp"))
        assertEquals(emptySet<String>(), SharedPreferencesCoreTries(context).take("8", "retroarch-gpsp"))
    }

    @Test
    fun anotherCoreOrAnOldNoteIsAFreshAttempt() {
        var clock = 1_000L
        val store = SharedPreferencesCoreTries(context, now = { clock }, maxAgeMs = 100)
        store.expect("9", "retroarch-gpsp", setOf("retroarch-mgba"))
        assertEquals(emptySet<String>(), store.take("9", "retroarch-vbam"))
        store.expect("9", "retroarch-gpsp", setOf("retroarch-mgba"))
        clock += 101
        assertEquals(emptySet<String>(), store.take("9", "retroarch-gpsp"))
    }
}
