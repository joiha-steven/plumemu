package com.plumemu.usage

import com.plumemu.telemetry.SwitchPlace
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.TelemetrySink
import java.util.Collections
import org.junit.Assert.assertTrue

/** A fake statistics module: keeps every event tracked, as name and the fields sent. */
internal class RecordingSink : TelemetrySink {
    val events: MutableList<TelemetryEvent> = Collections.synchronizedList(ArrayList())

    /** Each event as `name` to its fields, in order. */
    val sent: List<Pair<String, Map<String, String>>> get() = synchronized(events) { events.map { it.name to it.wireFields() } }

    override fun track(event: TelemetryEvent) {
        events += event
    }

    override fun setEnabled(
        on: Boolean,
        where: SwitchPlace,
    ) = Unit
}

/**
 * The shape of a field value the convention allows: a lower-case word of letters, digits and
 * underscores, a version name (`0.0.3-beta`), or a range (`1–99`, `10k+`, `<2`). It fails on upper
 * case, spaces, dots outside a version and other punctuation, so a title as written ("Super Metroid"),
 * a file name ("zork.sfc") or a typed search with a space fails; a lower-case single word ("tetris")
 * or a colour's hex digits ("ff00ff") pass it. The privacy promise is held by the types, not by this check: the
 * events take only enums, `SystemRef` / `EmulatorRef` made from the platform data, counts turned into
 * ranges, and version names the module validates. This check catches a slip in how a value is built.
 */
private val FIXED_VALUE = Regex("""^(?:[a-z0-9_]{1,64}|[0-9]{1,4}(?:\.[0-9]{1,4}){2}(?:-[a-z]{1,12}(?:\.?[0-9]{1,4})?)?|<2|[0-9]{1,3}k?–[0-9]{1,3}k?|[0-9]{1,3}k?\+)$""")

/** Fails when any field of [events] is not a fixed value (see [FIXED_VALUE]). */
internal fun assertNoFreeText(events: List<TelemetryEvent>) {
    for (event in events) {
        for ((field, value) in event.wireFields()) {
            assertTrue("${event.name}.$field is not a fixed value: \"$value\"", FIXED_VALUE.matches(value))
        }
    }
}
