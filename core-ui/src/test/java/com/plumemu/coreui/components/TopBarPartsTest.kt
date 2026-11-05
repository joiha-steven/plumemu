package com.plumemu.coreui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The top bar's place line (bold name, dim count) and the battery text's low state. */
class TopBarPartsTest {
    @Test
    fun theEmphasisIsTheNameAndTheRestIsTheDimCount() {
        assertEquals(PlaceParts("Super Nintendo", "248 games"), splitPlace("Super Nintendo · 248 games", "Super Nintendo"))
        assertEquals(PlaceParts("+24 games", "Super Nintendo"), splitPlace("Super Nintendo · +24 games", "+24 games"))
        // Settings' crumbs: the arrow between the name and the section is not drawn, the ones inside the rest stay.
        assertEquals(PlaceParts("Settings", "Appearance › Colors"), splitPlace("Settings › Appearance › Colors", "Settings"))
    }

    @Test
    fun withoutTheEmphasisTheWholeLineIsDim() {
        assertEquals(PlaceParts("", "12 systems"), splitPlace("12 systems", ""))
        assertEquals(PlaceParts("", "12 systems"), splitPlace("12 systems", "Nope"))
    }

    @Test
    fun aBatteryTextIsLowAtFifteenPercentOrLess() {
        assertEquals(82, batteryPercent("82%"))
        assertEquals(null, batteryPercent(""))
        assertTrue(isLowBattery("15%"))
        assertTrue(isLowBattery("3%"))
        assertFalse(isLowBattery("16%"))
        assertFalse(isLowBattery("100%"))
        assertFalse("no digits is not low", isLowBattery(""))
    }
}
