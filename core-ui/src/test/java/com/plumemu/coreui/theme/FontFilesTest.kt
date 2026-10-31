package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped font files: only Sans, Serif and Mono (Plum Pixel was dropped),
 * and each family ships its OFL text, which the licence requires.
 * Unit tests run with the module directory as the working directory.
 */
class FontFilesTest {
    private val fontDir = File("src/main/res/font")
    private val licenseDir = File("src/main/assets/licenses")

    @Test
    fun shipsExactlyTheExpectedFontFiles() {
        val expected =
            setOf(
                "plum_sans_400.ttf",
                "plum_sans_500.ttf",
                "plum_sans_600.ttf",
                "plum_sans_700.ttf",
                "plum_sans_800.ttf",
                "plum_serif_400.ttf",
                "plum_serif_500.ttf",
                "plum_serif_600.ttf",
                "plum_serif_700.ttf",
                "plum_serif_800.ttf",
                "plum_mono_400.ttf",
                "plum_mono_500.ttf",
                "plum_mono_700.ttf",
                "plum_mono_800.ttf",
            )
        val actual = fontDir.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun everyShippedFamilyHasItsLicence() {
        val families = fontDir.listFiles().orEmpty().map { it.name.substringBeforeLast('_') }.toSet()
        assertTrue("no font files found in ${fontDir.absolutePath}", families.isNotEmpty())
        for (family in families) {
            val licence = File(licenseDir, family.replace('_', '-') + "-OFL.txt")
            assertTrue("missing ${licence.path}", licence.isFile)
            assertTrue("${licence.path} is not the SIL OFL", licence.readText().contains("SIL OPEN FONT LICENSE"))
        }
    }
}
