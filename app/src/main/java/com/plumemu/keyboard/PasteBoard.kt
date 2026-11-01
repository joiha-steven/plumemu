package com.plumemu.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter

/*
 * Pasting into a field typed with Plumemu's keyboard: whether the clipboard holds text (the paste key
 * and the "R3 Paste" hint show only then) and the text itself, read only when the person pastes.
 * Clipboard text is never logged, shown in a hint or put in a test name.
 */

/** Where pasted text comes from: Android's clipboard on a device, a fake in a test. Main thread only. */
internal interface ClipSource {
    /** Whether the clipboard holds text, from its description only (reading the text would show Android 12's notice). */
    fun hasText(): Boolean

    /** The clipboard's text, or null when it holds none. Only when the person pastes. */
    fun text(): String?

    /** Calls [onChange] whenever the clipboard changes while the app may read it. */
    fun watch(onChange: () -> Unit)
}

/** The app's one clipboard for Plumemu's keyboard; [install]ed by the application. Main thread only. */
internal object PasteBoard {
    private var source: ClipSource? = null

    /** The clipboard holds text: the paste key and its hint are shown. */
    var hasText: Boolean by mutableStateOf(false)
        private set

    fun install(clips: ClipSource) {
        source = clips
        clips.watch(::refresh)
        refresh()
    }

    /**
     * Asks again whether the clipboard holds text. Android 10 and later answer only while the app has
     * the focus, so the activity calls this when its window gets it back (the person copied in a browser).
     */
    fun refresh() {
        hasText = source?.hasText() == true
    }

    /** Android's paste menu a field's long press opened ([CaretLine]); closed by any gamepad action. */
    private var menu: TextToolbar? = null

    /** Who opened [menu] (a field's line): only it may close it, a line whose menu is gone may not close the next. */
    private var menuOwner: Any? = null

    fun menuOpened(
        toolbar: TextToolbar,
        owner: Any,
    ) {
        menu = toolbar
        menuOwner = owner
    }

    fun ownsMenu(owner: Any): Boolean = menuOwner === owner

    /** Closes the long press's paste menu: the person went on with the gamepad, so it no longer points at the field. */
    fun closeMenu() {
        menu?.takeIf { it.status == TextToolbarStatus.Shown }?.hide()
        menu = null
        menuOwner = null
    }

    /** What a paste inserts, or null when there is nothing to paste: the text is not read when the clipboard holds none. */
    fun text(): String? {
        val clips = source ?: return null
        if (!clips.hasText()) return null
        return clips.text()?.takeIf { it.isNotEmpty() }
    }
}

/** Any gamepad action closes the long press's paste menu; taps reach the menu or the field themselves. */
@Composable
internal fun ClosePasteMenuOnActions(input: InputRouter) {
    LaunchedEffect(input) {
        input.actions.collect { if (it !is Action.TapItem && it !is Action.LongPressItem) PasteBoard.closeMenu() }
    }
}

/** Android's clipboard. */
internal class AndroidClipSource(
    private val context: Context,
) : ClipSource {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

    /** Plain text or HTML (a browser's copy): both read as text. A link list or an image is not offered. */
    override fun hasText(): Boolean {
        val manager = clipboard ?: return false
        if (!manager.hasPrimaryClip()) return false
        val description = manager.primaryClipDescription ?: return false
        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    }

    override fun text(): String? {
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        // An HTML clip may carry no plain text; Android turns it (or a note's styled text) into plain text.
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    override fun watch(onChange: () -> Unit) {
        clipboard?.addPrimaryClipChangedListener { onChange() }
    }
}

/**
 * [clip] as it goes into a field (every field Plumemu types into is one line): the line breaks at its
 * ends go and each run of them inside becomes one space (a key copied with its line end, an address on
 * two lines); then only what fits in [room] UTF-16 units is kept, never half of a character.
 */
internal fun pastable(
    clip: String,
    room: Int,
): String {
    val flat = clip.trim { it in LINE_BREAKS }.replace(LINE_BREAK_RUN, " ")
    if (flat.length <= room) return flat
    if (room <= 0) return ""
    // Limits count UTF-16 units (String.take); an emoji's first half alone is no character.
    val cut = if (flat[room - 1].isHighSurrogate()) room - 1 else room
    return flat.substring(0, cut)
}

private const val LINE_BREAKS = "\r\n\u2028\u2029\u0085"

private val LINE_BREAK_RUN = Regex("[$LINE_BREAKS]+")
