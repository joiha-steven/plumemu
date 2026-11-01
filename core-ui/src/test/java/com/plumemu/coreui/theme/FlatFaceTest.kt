package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flat face's colours: the text on a key and on a sunken field reads in every set, and a key stands off its background. */
class FlatFaceTest {
    private fun rgb(color: Color) = Rgb(color.toArgb() and 0xFFFFFF)

    private fun themes(): List<Pair<String, PlumColors>> =
        ColorSet.entries.map { it.name to PlumColors.derive(it.backgroundColor, it.textMode, it.accentColor) } +
            listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper).map { (name, s) -> name to PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor) }

    @Test
    fun theLettersOnAKeyAndOnAFieldReadInEverySet() {
        for ((name, tokens) in themes()) {
            val colors = PlumComposeColors.from(tokens)
            val key = rgb(colors.keyFace.face)
            assertTrue("$name: text2 on a key (dialog button) >= 4.5, was ${ColorMath.contrast(tokens.text2, key)}", ColorMath.contrast(tokens.text2, key) >= 4.5)
            assertTrue("$name: strong on a key (hint cap) >= 4.5", ColorMath.contrast(tokens.strong, key) >= 4.5)
            // Settings' sel2 panels and fields: the text sits on sel2, with the key beside it.
            assertTrue("$name: text2 on sel2 >= 4.5", ColorMath.contrast(tokens.text2, tokens.sel2) >= 4.5)
            assertTrue("$name: text on the sunken field (sel2) >= 7", ColorMath.contrast(tokens.text, tokens.sel2) >= 7.0)
        }
    }

    @Test
    fun aKeyStandsOffItsBackgroundByItsEdge() {
        for ((name, tokens) in themes()) {
            val face = PlumComposeColors.from(tokens).keyFace
            assertTrue("$name: the edge is darker than the face", ColorMath.luminance(rgb(face.edge)) < ColorMath.luminance(rgb(face.face)))
            assertTrue("$name: the face differs from the background", rgb(face.face) != tokens.bg)
        }
    }

    @Test
    fun theKnobOfASwitchThatIsOnIsTheTextColourOnAnAccentFill() {
        for ((name, tokens) in themes()) {
            val colors = PlumComposeColors.from(tokens)
            // The same token as text on an accent fill: Readability's rule (4.5:1; three preset pairs a little under, as for badges).
            assertTrue("$name: the on knob is onAccent", colors.knobOnFace().face == colors.onAccent)
        }
    }
}
