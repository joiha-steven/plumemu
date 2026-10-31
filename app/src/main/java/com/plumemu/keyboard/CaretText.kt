package com.plumemu.keyboard

import androidx.compose.runtime.Immutable
import com.plumemu.coreinput.TypedKey

/**
 * A field's [text] and where typing goes in it ([caret]: 0 before the first character,
 * `text.length` after the last), with the edits Plumemu's keyboard and a hardware keyboard make.
 * One character is one code point, so an emoji typed on a hardware keyboard is deleted whole.
 * Shared by Search and the game panel's fields.
 */
@Immutable
internal data class CaretText(
    val text: String,
    val caret: Int,
) {
    fun insert(typed: String): CaretText {
        val at = caret.coerceIn(0, text.length)
        return CaretText(text.substring(0, at) + typed + text.substring(at), at + typed.length)
    }

    /** The character before the caret goes; nothing at the start. */
    fun deleteBefore(): CaretText {
        val at = caret.coerceIn(0, text.length)
        if (at == 0) return this
        val from = text.offsetByCodePoints(at, -1)
        return CaretText(text.removeRange(from, at), from)
    }

    /** The character after the caret goes; nothing at the end. */
    fun deleteAfter(): CaretText {
        val at = caret.coerceIn(0, text.length)
        if (at == text.length) return this
        return CaretText(text.removeRange(at, text.offsetByCodePoints(at, 1)), at)
    }

    /** One character left or right; the caret stops at the text's ends. */
    fun moveBy(step: Int): CaretText {
        val at = caret.coerceIn(0, text.length)
        val next =
            when {
                step < 0 && at > 0 -> text.offsetByCodePoints(at, -1)
                step > 0 && at < text.length -> text.offsetByCodePoints(at, 1)
                else -> at
            }
        return copy(caret = next)
    }

    fun toStart(): CaretText = copy(caret = 0)

    fun toEnd(): CaretText = copy(caret = text.length)

    /** A hardware keyboard's edit; null for Enter, which is the field's action, not an edit. */
    fun typed(key: TypedKey): CaretText? =
        when (key) {
            is TypedKey.Text -> insert(key.text)
            TypedKey.Backspace -> deleteBefore()
            TypedKey.ForwardDelete -> deleteAfter()
            TypedKey.CaretLeft -> moveBy(-1)
            TypedKey.CaretRight -> moveBy(1)
            TypedKey.Enter -> null
        }
}
