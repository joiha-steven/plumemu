package com.plumemu.secondscreen

import com.plumemu.coredata.settings.SecondScreenSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which display is the second screen,
 * over fake displays: the choice, the hidden type that cannot be read, and the listener's updates.
 */
class SecondScreenDisplaysTest {
    private fun display(
        id: Int,
        type: Int?,
        isPresentation: Boolean = true,
        name: String = "Screen-$id",
    ) = DisplayFacts(id, name, isPresentation, type)

    @Test
    fun aBuiltInScreenAloneIsChosenAsBuiltIn() {
        assertEquals(SecondScreen(4, "Screen-2", ScreenKind.BuiltIn), chooseSecondScreen(listOf(display(4, TYPE_INTERNAL, name = "Screen-2"))))
    }

    @Test
    fun anHdmiCastOrVirtualScreenIsNeverUsed() {
        assertNull("HDMI", chooseSecondScreen(listOf(display(2, TYPE_EXTERNAL))))
        assertNull("Wi-Fi display", chooseSecondScreen(listOf(display(3, TYPE_WIFI))))
        assertNull("virtual (a screen recorder)", chooseSecondScreen(listOf(display(5, TYPE_VIRTUAL))))
    }

    @Test
    fun theOverlayTestDisplayIsUnsure() {
        assertEquals(ScreenKind.Unsure, chooseSecondScreen(listOf(display(2, TYPE_OVERLAY)))?.kind)
    }

    @Test
    fun aTypeThatCannotBeReadOrIsUnknownIsUnsure() {
        assertEquals(ScreenKind.Unsure, chooseSecondScreen(listOf(display(3, type = null)))?.kind)
        assertEquals(ScreenKind.Unsure, chooseSecondScreen(listOf(display(3, type = 42)))?.kind)
    }

    @Test
    fun builtInComesBeforeUnsureWhateverTheIds() {
        val chosen = chooseSecondScreen(listOf(display(2, TYPE_OVERLAY), display(7, TYPE_INTERNAL), display(1, null)))
        assertEquals(7, chosen?.displayId)
        assertEquals(ScreenKind.BuiltIn, chosen?.kind)
    }

    @Test
    fun amongEqualsTheLowestIdWins() {
        assertEquals(3, chooseSecondScreen(listOf(display(9, TYPE_INTERNAL), display(3, TYPE_INTERNAL), display(5, TYPE_INTERNAL)))?.displayId)
        assertEquals(2, chooseSecondScreen(listOf(display(6, null), display(2, TYPE_OVERLAY)))?.displayId)
    }

    @Test
    fun theDefaultDisplayAndNonPresentationDisplaysAreSkipped() {
        assertNull("the default display, even if it says presentation", chooseSecondScreen(listOf(display(0, TYPE_INTERNAL))))
        assertNull("not a presentation display", chooseSecondScreen(listOf(display(4, TYPE_INTERNAL, isPresentation = false))))
        assertEquals(6, chooseSecondScreen(listOf(display(0, TYPE_INTERNAL), display(4, TYPE_INTERNAL, isPresentation = false), display(6, null)))?.displayId)
    }

    @Test
    fun noDisplaysNoSecondScreen() {
        assertNull(chooseSecondScreen(emptyList()))
    }

    @Test
    fun theSwitchFollowsThePersonThenTheKind() {
        val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
        val unsure = SecondScreen(2, "Overlay #1", ScreenKind.Unsure)
        assertTrue("undecided: on for built-in", isSecondScreenOn(SecondScreenSettings(), builtIn))
        assertFalse("undecided: off when unsure", isSecondScreenOn(SecondScreenSettings(), unsure))
        assertFalse(isSecondScreenOn(SecondScreenSettings(enabled = false), builtIn))
        assertTrue(isSecondScreenOn(SecondScreenSettings(enabled = true), unsure))
        assertFalse("never on without a screen", isSecondScreenOn(SecondScreenSettings(enabled = true), null))
    }

    @Test
    fun aHiddenTypeCallThatThrowsReadsAsUnknownAndWarnsOnce() {
        val warnings = mutableListOf<String>()
        val types = HiddenDisplayType(warn = { warnings += it })
        assertNull(types.read { throw NoSuchMethodException("getType") })
        assertNull("an Error counts too", types.read { throw NoSuchMethodError("getType") })
        assertNull(types.read { 1 })
        assertEquals("one warning, with the class only: $warnings", 1, warnings.size)
        assertTrue(warnings.single(), "NoSuchMethodException" in warnings.single())
    }

    @Test
    fun aHiddenTypeThatIsNotANumberReadsAsUnknown() {
        val warnings = mutableListOf<String>()
        assertNull(HiddenDisplayType(warn = { warnings += it }).read { "internal" })
        assertEquals(1, warnings.size)
        assertEquals(TYPE_INTERNAL, HiddenDisplayType(warn = { warnings += it }).read { TYPE_INTERNAL })
    }

    @Test
    fun theListenerPicksUpAnAddedScreen() {
        val source = FakeDisplaySource()
        val displays = SecondScreenDisplays(source, info = {})
        displays.start()
        assertNull(displays.current.value)
        source.change(listOf(display(4, TYPE_INTERNAL)))
        assertEquals(4, displays.current.value?.displayId)
    }

    @Test
    fun theListenerDropsARemovedScreen() {
        val source = FakeDisplaySource(listOf(display(4, TYPE_INTERNAL)))
        val displays = SecondScreenDisplays(source, info = {})
        displays.start()
        assertEquals(ScreenKind.BuiltIn, displays.current.value?.kind)
        source.change(emptyList())
        assertNull(displays.current.value)
    }

    @Test
    fun aChangedTypeIsRecomputed() {
        val source = FakeDisplaySource(listOf(display(4, TYPE_INTERNAL)))
        val displays = SecondScreenDisplays(source, info = {})
        displays.start()
        source.change(listOf(display(4, null)))
        assertEquals(ScreenKind.Unsure, displays.current.value?.kind)
        source.change(listOf(display(4, TYPE_EXTERNAL)))
        assertNull("now external: never used", displays.current.value)
    }

    @Test
    fun eachChangeOfTheChoiceIsOneLogLineWithIdNameTypeAndKind() {
        val lines = mutableListOf<String>()
        val source = FakeDisplaySource()
        val displays = SecondScreenDisplays(source, info = { lines += it })
        displays.start()
        source.change(listOf(display(2, TYPE_OVERLAY, name = "Overlay #1")))
        source.change(listOf(display(2, TYPE_OVERLAY, name = "Overlay #1"))) // the same answer: no line
        source.change(emptyList())
        assertEquals(lines.toString(), 3, lines.size)
        assertTrue(lines[0], "none" in lines[0])
        assertEquals("Second screen: display 2 \"Overlay #1\", type 4, kind Unsure", lines[1])
        assertTrue(lines[2], "none" in lines[2])
    }

    @Test
    fun startsAreCountedAndStoppingKeepsTheLastAnswer() {
        val source = FakeDisplaySource(listOf(display(4, TYPE_INTERNAL)))
        val displays = SecondScreenDisplays(source, info = {})
        displays.start()
        displays.start() // a recreated Activity's new instance, before the old one stops
        assertEquals(1, source.registrations)
        displays.stop()
        assertTrue("still listening for the new instance", source.isListening)
        displays.stop()
        assertFalse(source.isListening)
        assertEquals("a recreated Activity does not see the screen vanish", 4, displays.current.value?.displayId)
        displays.stop() // one stop too many changes nothing
        assertFalse(source.isListening)
    }

    @Test
    fun aListenerThatCannotBeRegisteredMeansNoSecondScreen() {
        val lines = mutableListOf<String>()
        val source = FakeDisplaySource(listOf(display(4, TYPE_INTERNAL))).apply { canRegister = false }
        val displays = SecondScreenDisplays(source, info = { lines += it })
        displays.start()
        assertNull("unwatched, a screen is not used", displays.current.value)
        assertEquals(listOf("Second screen: none (no display listener)"), lines)
        displays.stop()
        assertFalse(source.isListening)
        // The next start tries again.
        source.canRegister = true
        displays.start()
        assertEquals(4, displays.current.value?.displayId)
    }

    /** Displays set by the test; [change] calls the listener as Android does on the main thread. */
    private class FakeDisplaySource(
        private var displays: List<DisplayFacts> = emptyList(),
    ) : DisplaySource {
        private var listener: (() -> Unit)? = null
        var registrations = 0
        val isListening: Boolean get() = listener != null

        fun change(next: List<DisplayFacts>) {
            displays = next
            listener?.invoke()
        }

        override fun presentationDisplays(): List<DisplayFacts> = displays

        /** False makes [register] fail, as a refused `registerDisplayListener` does. */
        var canRegister = true

        override fun register(onChange: () -> Unit): Boolean {
            registrations++
            if (!canRegister) return false
            listener = onChange
            return true
        }

        override fun unregister() {
            listener = null
        }
    }
}
