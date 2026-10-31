package com.plumemu.coreui.theme

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.ReferenceShots.Preset
import com.plumemu.coreui.testing.ReferenceShots.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceShotsNamesTest {
    @Test
    fun sansKeepsThePlainNameAndOtherFontsAddTheirs() {
        assertEquals("home_768x432_graphite.png", ReferenceShots.fileName("home", Size.Wide, Preset.Graphite))
        assertEquals("home_576x432_paper.png", ReferenceShots.fileName("home", Size.Narrow, Preset.Paper, PlumFont.Sans))
        assertEquals("home_768x432_paper_serif.png", ReferenceShots.fileName("home", Size.Wide, Preset.Paper, PlumFont.Serif))
        assertEquals("home_576x432_graphite_mono.png", ReferenceShots.fileName("home", Size.Narrow, Preset.Graphite, PlumFont.Mono))
    }
}
