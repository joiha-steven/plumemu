package com.plumemu.library.scrape

/**
 * The Steam app id in a pinned shortcut's `shortcut_id`: DroidDeck's `game:<digits>` only. Anything else (GameNative's
 * `game_<id>`, plain digits that may be another launcher's own id, text) is null, so no other game's art is fetched. It lets the Steam art fallback skip the store search, which some networks block.
 */
internal object ShortcutSteamAppId {
    private val FORM = Regex("""game:(\d{1,10})""")

    fun parse(shortcutId: String): Long? = FORM.matchEntire(shortcutId.trim())?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE }
}
