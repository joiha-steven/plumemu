package com.plumemu.update

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.plumemu.MainActivity
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * After Plumemu's own update Android 10+ silently refuses the reopen, so [UpdateRestartReceiver] waits two seconds and posts
 * one low-importance "tap to open" notice, unless [MainActivity] resumed meanwhile. Nothing is posted or started for an install
 * that was not Plumemu's own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ReopenNoticeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)

    // versionCode is the only field below Android 9.
    @Before
    fun plumemuIsNotInFront() = ReopenNotice.onPaused()

    @After
    fun leaveNoFlagBehind() = ReopenNotice.onPaused()

    private fun installedCode(): Long = app.packageManager.getPackageInfo(app.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }

    private fun ourUpdate() = ReopenMarker.write(app, installedCode())

    private fun replaced(context: Context = app) = UpdateRestartReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

    private fun waitForTheCheck() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ReopenNotice.WAIT_MS))

    private fun posted() = shadowManager.allNotifications

    @Test
    fun androidRefusedTheReopenSoOneLowNoticeOpensMainActivity() {
        ourUpdate()
        replaced()
        assertEquals("nothing before the wait is over", 0, posted().size)
        waitForTheCheck()
        val notice = posted().single()
        assertEquals(app.getString(R.string.update_notice_title), notice.extras.getString("android.title"))
        assertEquals(app.getString(R.string.update_notice_text), notice.extras.getString("android.text"))
        val channel = manager.getNotificationChannel(ReopenNotice.CHANNEL_ID)!!
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertEquals(false, channel.shouldVibrate())
        assertEquals(ReopenNotice.CHANNEL_ID, notice.channelId)
        assertTrue("taps cancel it", notice.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
        val tap = shadowOf(notice.contentIntent)
        assertTrue(tap.isActivityIntent)
        assertEquals(MainActivity::class.java.name, tap.savedIntent.component?.className)
        assertTrue("immutable", tap.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    @Config(sdk = [28])
    fun belowAndroid10NothingIsWaitedForOrPosted() {
        ourUpdate()
        replaced()
        waitForTheCheck()
        assertEquals(0, posted().size)
    }

    @Test
    fun plumemuAlreadyResumedPostsNothing() {
        ourUpdate()
        ReopenNotice.onResumed()
        replaced()
        waitForTheCheck()
        assertEquals(0, posted().size)
    }

    @Test
    fun theTapCountsAsAnOpenAfterAnUpdate() {
        assertTrue(ReopenNotice.post(app))
        val tap = shadowOf(posted().single().contentIntent)
        assertTrue(tap.savedIntent.getBooleanExtra(com.plumemu.usage.EXTRA_OPENED_AFTER_UPDATE, false))
    }

    @Test
    fun aFailingNotificationServiceNeverThrows() {
        val broken =
            object : ContextWrapper(app) {
                override fun getSystemService(name: String): Any = throw IllegalStateException("boom")
            }
        assertEquals(false, ReopenNotice.post(broken))
    }

    @Test
    fun mainActivityResumedAfterTheAttemptSoNothingIsPosted() {
        ourUpdate()
        replaced()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        ReopenNotice.onResumed()
        waitForTheCheck()
        assertEquals(0, posted().size)
        assertNull("no channel either", manager.getNotificationChannel(ReopenNotice.CHANNEL_ID))
    }

    @Test
    fun aRefusedStartThatThrowsPostsAtOnce() {
        ourUpdate()
        val refusing =
            object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this

                override fun startActivity(intent: Intent) = throw SecurityException("Abort background activity starts")
            }
        replaced(refusing)
        assertEquals(1, posted().size)
    }

    @Test
    fun notificationsTurnedOffPostNothing() {
        ourUpdate()
        shadowManager.setNotificationsEnabled(false)
        replaced()
        waitForTheCheck()
        assertEquals(0, posted().size)
    }

    @Test
    fun anInstallThatWasNotPlumemusOwnStartsAndPostsNothing() {
        // No marker: adb, a file manager or a store.
        ReopenMarker.clear(app)
        replaced()
        waitForTheCheck()
        assertEquals(0, posted().size)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun theMarkerOfAnotherVersionPostsNothing() {
        ReopenMarker.write(app, installedCode() + 1)
        replaced()
        waitForTheCheck()
        assertEquals(0, posted().size)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun cancelRemovesAPostedNotice() {
        assertTrue(ReopenNotice.post(app))
        assertNotNull(posted().singleOrNull())
        ReopenNotice.cancel(app)
        assertEquals(0, posted().size)
    }
}
