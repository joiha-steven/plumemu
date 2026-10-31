package com.plumemu.library.scrape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutSteamAppIdTest {
    @Test fun `game colon digits give the id`() {
        assertEquals(413150L, ShortcutSteamAppId.parse("game:413150"))
        assertEquals(2147483647L, ShortcutSteamAppId.parse("game:2147483647"))
    }

    @Test fun `anything else gives null`() {
        listOf("413150", "game:2147483648", "game:0", "game_1", "game:", "game:12ab", "", "0", "game:99999999999999999999", "shortcut:5").forEach { assertNull(it, ShortcutSteamAppId.parse(it)) }
    }
}
