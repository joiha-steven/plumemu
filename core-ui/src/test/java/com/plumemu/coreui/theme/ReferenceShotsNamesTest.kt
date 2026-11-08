package com.plumemu.coreui.theme

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.ReferenceShots.Preset
import com.plumemu.coreui.testing.ReferenceShots.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceShotsNamesTest {
    @Test
    fun theNameHasNoFontPartAndAnOtherTextSizeAddsItsOwn() {
        assertEquals("home_768x432_graphite.png", ReferenceShots.fileName("home", Size.Wide, Preset.Graphite))
        assertEquals("home_576x432_paper.png", ReferenceShots.fileName("home", Size.Narrow, Preset.Paper, TextSize.Small))
        assertEquals("home_576x432_paper_largest.png", ReferenceShots.fileName("home", Size.Narrow, Preset.Paper, TextSize.Largest))
    }
}
