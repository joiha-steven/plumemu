package com.plumemu.graph

import com.plumemu.identify.arcade.ArcadeName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The old-name hint never breaks the core list: any failure is "no hint", logged by class name. */
class OldArcadeNameTest {
    private fun name(old: Boolean) = ArcadeName("xmen2p", "X-Men", null, null, null, false, false, false, false, false, oldName2003Plus = old)

    @Test
    fun aLookupThatThrowsGivesFalseAndLogsOnlyTheClassName() = runBlocking {
        val lines = ArrayList<String>()
        val result = oldNameOrFalse(7, { lines += it }) { throw IndexOutOfBoundsException("secret/path/xmen2p.zip") }
        assertFalse(result)
        assertEquals(1, lines.size)
        assertTrue(lines.single(), "IndexOutOfBoundsException" in lines.single() && "secret" !in lines.single())
    }

    @Test
    fun theFlagIsReadAndNoSetGivesFalse() = runBlocking {
        assertTrue(oldNameOrFalse(1, {}) { name(true) })
        assertFalse(oldNameOrFalse(1, {}) { name(false) })
        assertFalse(oldNameOrFalse(1, {}) { null })
    }

    @Test
    fun aCancellationGoesOn() {
        try {
            runBlocking { oldNameOrFalse(1, {}) { throw CancellationException("gone") } }
            fail("the cancellation was swallowed")
        } catch (_: CancellationException) {
        }
    }
}
