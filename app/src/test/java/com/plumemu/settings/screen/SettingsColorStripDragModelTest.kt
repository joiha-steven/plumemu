package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A finger dragging along the Color strip previews stop by stop and stores once, when it lifts. */
internal class SettingsColorStripDragModelTest : SettingsModelHarness() {
    private val reported = Reported().also { it.listenTo(model.feel) }

    private fun onStrip() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        toColor()
        reported.drain()
        writesAtStart = actions.writes // reaching the strip from the presets wrote already
    }

    private var writesAtStart = 0

    private fun drag(stop: Int) = model.onTouchStop(stop, actions.shown, wide, drag = true)

    @Test
    fun aDragShowsEachStopThroughThePreviewAndStoresNothingUntilTheFingerLifts() {
        onStrip()
        val first = model.colorStop
        drag(first + 2)
        assertNotNull("the preview shows the stop under the finger", actions.colorPreview.appearance)
        drag(first + 3)
        assertEquals(first + 3, model.colorStop)
        assertEquals("nothing stored while the finger is down", 0, actions.writes - writesAtStart)
        model.onTouchStopEnd()
        assertEquals("stored once on lift", 1, actions.writes - writesAtStart)
        assertFalse(actions.colorPreview.isActive && !actions.colorPreview.isReleased)
    }

    @Test
    fun theMoveSoundPlaysOncePerStopChangeAndNotWhenTheFingerStaysOnAStop() {
        onStrip()
        val first = model.colorStop
        drag(first + 1)
        drag(first + 1)
        drag(first + 2)
        assertEquals(listOf(Outcome.Moved, Outcome.Moved), reported.drain())
        model.onTouchStopEnd()
    }

    @Test
    fun liftingOnTheStopTheDragStartedOnChangesNothing() {
        onStrip()
        drag(model.colorStop)
        model.onTouchStopEnd()
        assertEquals(0, actions.writes - writesAtStart)
        assertNull("no preview was made", actions.colorPreview.appearance.takeIf { !actions.colorPreview.isActive })
        assertTrue(model.colourPart == ColorPart.Color)
        move(Direction.Down)
    }
}
