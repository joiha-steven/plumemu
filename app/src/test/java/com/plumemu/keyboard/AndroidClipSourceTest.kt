package com.plumemu.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android's clipboard: the paste key is offered only for a clip that reads as text, and then it pastes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AndroidClipSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clips = AndroidClipSource(context)

    @Test
    fun plainTextIsOfferedAndRead() {
        clipboard.setPrimaryClip(ClipData.newPlainText("key", "made-up-key"))
        assertTrue(clips.hasText())
        assertEquals("made-up-key", clips.text())
    }

    @Test
    fun htmlIsOfferedAndReadAsText() {
        clipboard.setPrimaryClip(ClipData.newHtmlText("page", "made up words", "<b>made up</b> words"))
        assertTrue(clips.hasText())
        assertEquals("made up words", clips.text())
    }

    /**
     * An HTML clip whose item has no text part (Android refuses an HTML item without plain text, but an
     * item may carry only a link): the key shows, so a paste must still give text, not nothing.
     */
    @Test
    fun anHtmlClipWithNoTextPartStillPastesText() {
        val description = ClipDescription("page", arrayOf(ClipDescription.MIMETYPE_TEXT_HTML))
        clipboard.setPrimaryClip(ClipData(description, ClipData.Item(Uri.parse("https://made.up/page"))))
        assertTrue(clips.hasText())
        assertEquals("https://made.up/page", clips.text())
    }

    @Test
    fun aLinkListIsNotOffered() {
        clipboard.setPrimaryClip(ClipData.newRawUri("file", Uri.parse("content://made.up/1")))
        assertFalse("the key would show and paste nothing", clips.hasText())
    }

    @Test
    fun anEmptyClipboardOffersNothing() {
        clipboard.clearPrimaryClip()
        assertFalse(clips.hasText())
    }
}
