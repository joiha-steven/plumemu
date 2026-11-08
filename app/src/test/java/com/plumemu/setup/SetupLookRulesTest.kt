package com.plumemu.setup

import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each device gets suggested on the look step, and how the tiles' grid moves. */
class SetupLookRulesTest {
    private fun facts(
        maker: String,
        model: String,
        width: Float = 768f,
        height: Float = 432f,
        brand: String = "",
    ) = DeviceFacts(maker, model, width, height, brand)

    @Test
    fun aThorIsOledSoNearBlackIsSuggested() {
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("AYN", "Thor")).colorSet)
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("ayn", "THOR", 640f, 360f)).colorSet)
    }

    @Test
    fun anOledScreenGetsTrueBlackUnderTheDefaultSetOnly() {
        val oled = deviceLookOf(facts("AYN", "Thor"))
        assertEquals(BackgroundChoice.Preset(BackgroundPreset.Black), oled.pageOf(ColorSet.Plumemu))
        assertEquals("another set keeps its page", ColorSet.Daylight.background, oled.pageOf(ColorSet.Daylight))
        val lcd = deviceLookOf(facts("Moorechip", "Retroid Pocket Nova", 576f, 432f))
        assertEquals("elsewhere the default is the ink", ColorSet.Plumemu.background, lcd.pageOf(ColorSet.Plumemu))
    }

    @Test
    fun theOledRetroidsGetItAndTheNovaDoesNot() {
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("Moorechip", "Retroid Pocket 6")).colorSet)
        assertNull(deviceLookOf(facts("Moorechip", "Retroid Pocket Nova", 576f, 432f)).colorSet)
    }

    @Test
    fun retroidsAreMatchedOnTheModelAloneAndAynOnMakerBrandOrModel() {
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("Moorechip", "Retroid Pocket Mini", brand = "qti")).colorSet)
        assertNull(deviceLookOf(facts("Moorechip", "Retroid Pocket 4 Pro", brand = "qti")).colorSet)
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("AYN", "AYN Thor")).colorSet)
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("Other", "Thor", brand = "AYN")).colorSet)
        assertEquals(ColorSet.Plumemu, deviceLookOf(facts("Other", "AYN Thor")).colorSet)
        assertNull(deviceLookOf(facts("Other", "Thor")).colorSet)
    }

    @Test
    fun otherDevicesGetNoSuggestionAtAll() {
        assertEquals(DeviceLook.None, deviceLookOf(facts("samsung", "SM-S911B", 411f, 891f)))
        assertEquals(DeviceLook.None, deviceLookOf(DeviceFacts()))
    }

    @Test
    fun aSmallFourThreeScreenGetsALargerTextSize() {
        val nova = deviceLookOf(facts("Moorechip", "Retroid Pocket Nova", 576f, 432f))
        assertEquals(TextSizeSetting.Larger, nova.textSize)
        assertEquals("either way round", TextSizeSetting.Larger, deviceLookOf(facts("x", "y", 432f, 576f)).textSize)
    }

    @Test
    fun aWideOrABigFourThreeScreenKeepsTheTextSize() {
        assertFalse(isSmallFourThree(facts("x", "y", 768f, 432f)))
        assertFalse("a tablet's 4:3 is not small", isSmallFourThree(facts("x", "y", 1024f, 768f)))
        assertFalse(isSmallFourThree(DeviceFacts()))
        assertTrue(isSmallFourThree(facts("x", "y", 640f, 480f)))
    }

    @Test
    fun theGridMovesByThreeColumnsAndStopsAtItsEdges() {
        val count = SetupLooks.sets.size
        assertEquals(-1, lookNeighbour(0, Direction.Left, count))
        assertEquals(1, lookNeighbour(0, Direction.Right, count))
        assertEquals(-1, lookNeighbour(2, Direction.Right, count))
        assertEquals(3, lookNeighbour(0, Direction.Down, count))
        assertEquals(-1, lookNeighbour(3, Direction.Down, count))
        assertEquals(-1, lookNeighbour(1, Direction.Up, count))
        assertEquals(1, lookNeighbour(4, Direction.Up, count))
    }

    @Test
    fun everyTileHasAControlAndTheSetsAreDistinct() {
        assertEquals(SetupLooks.sets.size, SetupLooks.sets.toSet().size)
        assertEquals(SetupLooks.sets.size, controlsOf(SetupStep.Look, folderCount = 1, hasScanFailed = false).size)
    }
}
