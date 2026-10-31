package com.plumemu.settings

import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SystemAccents
import com.plumemu.home.shippedCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shipped platform data's system colours as the By system accent settles them. */
class SystemColorsTest {
    private val colors = shippedCatalog.systemColors()

    private val backgrounds =
        mapOf(
            "Black" to BackgroundPreset.Black.color,
            "Paper" to BackgroundPreset.Paper.color,
            "Banana" to ColorSet.Banana.backgroundColor,
            "Graphite" to BackgroundPreset.Graphite.color,
        )

    @Test
    fun everySystemColourSettlesToThreeToOneOnTheFourReferenceBackgrounds() {
        assertTrue("more than a hundred systems have a colour", colors.size > 100)
        for ((name, background) in backgrounds) {
            val accents = SystemAccents(background, colors)
            for (id in colors.keys) {
                val settled = accents.colorOf(id)
                assertNotNull("$id on $name", settled)
                assertTrue("$id on $name: ${settled!!.value}", Readability.passes(settled, background, ColorRole.Accent))
            }
        }
    }

    @Test
    fun aSystemWithoutAColourAndNoSystemAtAllHaveNoneSoTheFallbackShows() {
        val accents = SystemAccents(BackgroundPreset.Black.color, colors)
        assertNull(accents.colorOf(null))
        assertNull(accents.colorOf("doom"))
        assertNull(accents.colorOf("no-such-system"))
        assertEquals(Rgb(0x7B5EA7), accents.colorOf("snes"))
    }

    @Test
    fun theTilesSampleIsFourSystemsColoursFromTheData() {
        assertEquals(SystemAccents.SAMPLE_SYSTEMS.map { colors.getValue(it) }, SystemAccents.SAMPLE)
        assertEquals(4, SystemAccents.SAMPLE.toSet().size)
    }
}
