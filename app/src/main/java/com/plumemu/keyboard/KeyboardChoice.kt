package com.plumemu.keyboard

/**
 * Which on-screen keyboard the launcher's text fields type with (Settings › Controls › Keyboard):
 * Plumemu's own ([Plumemu], the default) or Android's ([Android], the system keyboard,
 * the path a field takes without Plumemu's own keyboard). Search, the game panel's fields (Fix name…, Use my
 * own name, Fix box art…) and Settings' hex code and Unidentified games › Fix name…
 * read it; the sign-in form follows.
 */
internal enum class KeyboardChoice { Plumemu, Android }
