package com.plumemu.settings.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings

/**
 * The colours a finger on a Hue, Saturation or Lightness slider shows while it moves (while dragging only the screen's colour changes, the store is written on release): the screen's theme lays
 * [appearance] over the stored settings ([over]) and the store is written once, when the finger
 * lifts. After that the overlay stays until the store holds the same colours ([settleAgainst]) or
 * [SETTLE_LIMIT_MS] passes ([isReleased] starts that wait), so the screen never flashes back to the
 * old colour and never keeps an overlay that no write will match.
 */
@Stable
internal open class ColorPreview {
    /** The appearance to show instead of the stored one; null: the stored one. */
    open var appearance: AppearanceSettings? by mutableStateOf(null)
        protected set

    /** The finger is up and the final colours were written: waiting for the store to show them. */
    open var isReleased: Boolean by mutableStateOf(false)
        protected set

    /** The preview is on screen (a finger holds a slider, or its write has not landed yet). */
    open val isActive: Boolean get() = appearance != null

    /**
     * Shows [shown] in place of the stored appearance. [fingerDown]: a finger still holds a slider;
     * without one the overlay counts as released at once, so a write made after the lift (a step,
     * a put-back) never leaves it waiting for a lift that already happened.
     */
    open fun show(
        shown: AppearanceSettings,
        fingerDown: Boolean,
    ) {
        appearance = shown
        isReleased = !fingerDown
    }

    /** The finger lifted and its colours were written: keep showing them until the store has them. */
    open fun release() {
        if (appearance != null) isReleased = true
    }

    /** The overlay goes; the stored settings show. */
    open fun clear() {
        appearance = null
        isReleased = false
    }

    /** The store now holds [stored]: a released overlay that matches it has done its job. */
    fun settleAgainst(stored: AppearanceSettings) {
        val shown = appearance ?: return
        if (isReleased && shown.sameColours(stored)) clear()
    }

    private fun AppearanceSettings.sameColours(other: AppearanceSettings) = background == other.background && textColor == other.textColor && accent == other.accent

    /** [settings] with the overlay's three colours (every other appearance setting stays the stored one), or [settings] itself without one. */
    fun over(settings: PlumSettings): PlumSettings =
        appearance?.let { shown -> settings.withAppearance { it.copy(background = shown.background, textColor = shown.textColor, accent = shown.accent) } } ?: settings

    /** No overlay at all, for what has no theme to preview in (and the test fakes that never drag). */
    object None : ColorPreview() {
        override var appearance: AppearanceSettings?
            get() = null
            set(_) = Unit

        override var isReleased: Boolean
            get() = false
            set(_) = Unit

        override fun show(
            shown: AppearanceSettings,
            fingerDown: Boolean,
        ) = Unit

        override fun release() = Unit

        override fun clear() = Unit
    }

    companion object {
        /** A written colour that the store does not show by then (a refused or failed write) is given up on. */
        const val SETTLE_LIMIT_MS = 4000L
    }
}

/** The preview the option rows' dots read while drawing: with an overlay on they show the theme's colour, else the stored one. */
internal val LocalColorPreview = androidx.compose.runtime.staticCompositionLocalOf<ColorPreview> { ColorPreview.None }
