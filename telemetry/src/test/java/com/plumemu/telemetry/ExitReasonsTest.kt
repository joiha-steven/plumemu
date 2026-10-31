package com.plumemu.telemetry

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

/**
 * Android's exit records on fixtures: which become reports, the marker of the last one
 * seen, the ANR trace cap. Robolectric's ApplicationExitInfo cannot carry a trace, so the reading
 * of the record itself is left to the emulator.
 */
class ExitReasonsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val header = "Plumemu 0.0.3-beta (version code 321, release)"

    private fun anr(time: Long) = ExitRecord(CrashKind.Anr, time, "Input dispatching timed out", 0, importance = 100) { "\"main\" prio=5 tid=1 Blocked\n  at com.plumemu.a.b(SourceFile:1)" }

    private fun native(time: Long) = ExitRecord(CrashKind.Native, time, "crash", 11, importance = 100) { null }

    private var ids = 0

    private fun ExitReasons.take(report: Boolean = true) = since(header, { "id-${ids++}" }, report).also { taken -> taken.seenUpTo?.let(::markSeen) }

    @Test
    fun theFirstReadReportsNothingAndLaterOnlyNewExitsCount() {
        var records = listOf(anr(1_000), native(2_000))
        val exits = ExitReasons(folder.root.resolve("crashes/exits-seen.txt"), { records }, {})
        assertTrue("exits before this version are not reported", exits.take().items.isEmpty())

        records = listOf(anr(1_000), native(2_000), anr(3_000), native(4_000))
        val items = exits.take().items.map { it.second }
        assertEquals(listOf("anr", "native"), items.map { it.getValue("kind").jsonPrimitive.content })

        val anr = items[0]
        assertEquals("ANR", anr.getValue("exception").jsonPrimitive.content)
        assertEquals("Input dispatching timed out", anr.getValue("message").jsonPrimitive.content)
        assertEquals(Telemetry.isoTime(3_000), anr.getValue("time").jsonPrimitive.content)
        assertTrue(anr.getValue("stack").jsonPrimitive.content.startsWith("$header\nImportance 100 (on screen)\n\"main\" prio=5"))
        val native = items[1]
        assertEquals("Native crash (signal 11)", native.getValue("exception").jsonPrimitive.content)
        assertEquals("$header\nsignal 11", native.getValue("stack").jsonPrimitive.content)

        assertTrue("each exit is reported once", exits.take().items.isEmpty())
    }

    @Test
    fun theMarkerMovesOnlyWhenTheCallerSaysTheReportsAreStored() {
        var records = listOf(anr(1_000))
        val exits = ExitReasons(folder.root.resolve("exits-seen.txt"), { records }, {})
        exits.take()
        records = listOf(anr(1_000), anr(5_000))
        val first = exits.since(header, { "a" }, report = true)
        assertEquals(1, first.items.size)
        // The reports were not stored (a failed write): the next start reports them again.
        assertEquals(1, exits.since(header, { "a" }, report = true).items.size)
        exits.markSeen(first.seenUpTo!!)
        assertTrue(exits.since(header, { "a" }, report = true).items.isEmpty())
    }

    @Test
    fun statisticsOffMoveTheMarkerWithoutReporting() {
        var records = listOf(anr(1_000))
        val exits = ExitReasons(folder.root.resolve("exits-seen.txt"), { records }, {})
        exits.take()
        records = listOf(anr(1_000), anr(5_000))
        assertTrue(exits.take(report = false).items.isEmpty())
        assertTrue("switched on later: the old ANR is not sent", exits.take().items.isEmpty())
    }

    @Test
    fun aFirstReadWithNoExitsStillStartsTheMarker() {
        var records = emptyList<ExitRecord>()
        val exits = ExitReasons(folder.root.resolve("exits-seen.txt"), { records }, {})
        exits.take()
        records = listOf(anr(10))
        assertEquals(1, exits.take().items.size)
    }

    @Test
    fun aReadFailureIsLoggedAndReportsNothing() {
        val logs = mutableListOf<String>()
        val exits = ExitReasons(folder.root.resolve("exits-seen.txt"), { throw SecurityException("no") }, { logs += it })
        assertTrue(exits.take().items.isEmpty())
        assertEquals(1, logs.size)
    }

    @Test
    fun anAnrTraceIsCappedAt64Kb() {
        val big = ByteArray(200 * 1024) { 'a'.code.toByte() }
        val text = ExitReasons.readCapped(ByteArrayInputStream(big))
        assertTrue(text.endsWith("[cut at ${ExitReasons.MAX_TRACE_BYTES} bytes]"))
        assertEquals(ExitReasons.MAX_TRACE_BYTES, text.substringBefore("\n[cut").length)
        assertEquals("short", ExitReasons.readCapped(ByteArrayInputStream("short".toByteArray())))
    }

    /** An ANR Android raised with the screen off must not read like a freeze in use. */
    @Test
    fun aBackgroundAnrSaysSoAndNamesItsImportance() {
        val description = "bg anr: Input dispatching timed out (Application does not have a focused window)"
        val record = ExitRecord(CrashKind.Anr, 5_000, description, 0, importance = 325) { "\"main\" prio=5 tid=1 Native" }

        val item = ExitReasons.item(record, header, "id")

        assertEquals("BackgroundANR", item.getValue("exception").jsonPrimitive.content)
        assertEquals(description, item.getValue("message").jsonPrimitive.content)
        assertTrue(item.getValue("stack").jsonPrimitive.content.startsWith("$header\nImportance 325 (top app, screen off)\n\"main\""))
    }

    @Test
    fun anAnrInUseStaysAnAnrAndAnUnknownImportanceIsGivenAsANumber() {
        val record = ExitRecord(CrashKind.Anr, 5_000, "Input dispatching timed out", 0, importance = 150) { null }

        val item = ExitReasons.item(record, header, "id")

        assertEquals("ANR", item.getValue("exception").jsonPrimitive.content)
        assertEquals("$header\nImportance 150\n(no trace)", item.getValue("stack").jsonPrimitive.content)
    }
}
