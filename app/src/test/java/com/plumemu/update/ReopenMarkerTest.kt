package com.plumemu.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plumemu reopens after an update only when it installed that very version itself, recently. */
class ReopenMarkerTest {
    private val now = 1_800_000_000_000L

    @Test fun itsOwnRecentUpdateReopens() = assertTrue(ReopenMarker.shouldReopen("1001 ${now - 5_000}", 1001, now))

    @Test fun anotherVersionInstalledFromElsewhereDoesNot() = assertFalse(ReopenMarker.shouldReopen("1001 ${now - 5_000}", 1002, now))

    @Test fun noMarkerDoesNot() = assertFalse(ReopenMarker.shouldReopen(null, 1001, now))

    @Test fun anOldMarkerDoesNot() = assertFalse(ReopenMarker.shouldReopen("1001 ${now - ReopenMarker.MAX_AGE_MS - 1}", 1001, now))

    @Test fun aMarkerFromTheFutureDoesNot() = assertFalse(ReopenMarker.shouldReopen("1001 ${now + 60_000}", 1001, now))

    @Test fun aBrokenMarkerDoesNot() {
        assertFalse(ReopenMarker.shouldReopen("", 1001, now))
        assertFalse(ReopenMarker.shouldReopen("abc def", 1001, now))
        assertFalse(ReopenMarker.shouldReopen("1001", 1001, now))
    }
}
