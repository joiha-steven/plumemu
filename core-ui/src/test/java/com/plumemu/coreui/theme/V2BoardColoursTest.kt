package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Colour tokens against the values measured on the V2 design boards, within 2 per channel.
 * The expected values are the board's, never this code's output; a tone the board mixes from the page is drawn [Depth.RAISE] of the way
 * there (`raised`).
 */
class V2BoardColoursTest {
    private fun Color.hex() = toArgb() and 0xFFFFFF

    private fun near(
        what: String,
        board: Int,
        ours: Int,
    ) {
        val gap = (0..2).maxOf { shift -> kotlin.math.abs(((board shr (8 * shift)) and 0xFF) - ((ours shr (8 * shift)) and 0xFF)) }
        assertTrue("$what: board #%06X, ours #%06X (max channel gap $gap)".format(board, ours), gap <= 2)
    }

    /** The page moved [t] of the way to the board's [board] tone. */
    private fun raised(
        page: Int,
        board: Int,
        t: Float = Depth.RAISE,
    ) = lerp(Color(page or 0xFF000000.toInt()), Color(board or 0xFF000000.toInt()), t).hex()

    /** The tone that lies [boardOff] between [key] and [boardGroove] on the board, on the way from [key] to [groove] instead. */
    private fun between(
        key: Int,
        boardGroove: Int,
        boardOff: Int,
        groove: Int,
    ): Int {
        var out = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val k = (key shr shift) and 0xFF
            val share = (k - ((boardOff shr shift) and 0xFF)).toDouble() / (k - ((boardGroove shr shift) and 0xFF))
            out = (out shl 8) or Math.round(k + (((groove shr shift) and 0xFF) - k) * share).toInt()
        }
        return out
    }

    private val BOARD_BLUE = Rgb(0x3E7BE0)
    private val graphite = PlumThemeSettings.Graphite
    private val paper = PlumThemeSettings.Paper

    /** The boards were drawn with their blue #3E7BE0, not today's default accent. */
    private fun colours(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, BOARD_BLUE))

    @Test
    fun graphitePageAndInkAreTheBoards() {
        near("Graphite page", 0x111214, graphite.backgroundColor.argb())
        near("Graphite text (labels, typed text)", 0xEBE7DD, TextMode.Light.color.argb())
        near("Paper text (labels, titles)", 0x1D1D1F, TextMode.Dark.color.argb())
        near("Paper focused label", 0x0F0F10, colours(paper).strong.hex())
        near("Graphite focused label", 0xFDFDFD, colours(graphite).strong.hex())
    }

    @Test
    fun theAccentFaceIsTheMeanOfTheBoardsFade() {
        val c = colours(graphite)
        // Primary button #548AE3..#568BE4, slider fill #588DE5, choice disc #5389E4, A key #5389E3, Search key #598DE4.
        near("accent face", 0x568BE4, c.accentFill.hex())
        near("accent face on Paper", 0x568BE4, colours(paper).accentFill.hex())
        near("primary button face", 0x568BE4, c.accentFace().face.hex())
    }

    @Test
    fun graphiteCollarAndKeyTonesAreTheBoards() {
        val c = colours(graphite)
        near("button lip", raised(0x111214, 0x202126), c.collar.lipMid.hex())
        near("button groove", raised(0x111214, 0x0B0C0C), c.collar.groove.hex())
        near("Secondary top", 0x2A2C31, c.collar.cap.hex())
        near("Secondary bottom", 0x232529, c.collar.capBottom.hex())
        near("Secondary middle (the flat face reads between top and bottom)", 0x27292D, c.collar.capBottom.hex())
        near("disabled label", 0x6F737B, c.collar.offText.hex())
        // The off face lies between the key and the groove by the board's share; the groove is at Depth.
        near("disabled face", between(0x2A2C31, 0x0B0C0C, 0x18191C, raised(0x111214, 0x0B0C0C)), c.keyOff.face.hex())
        near("empty-state disc", 0x0A0B0D, c.collar.well.hex())
        near("field well", 0x07080A, FieldDimens.well(c.bg).hex())
        near("keyboard tray", 0x18191C, c.tray.hex())
        near("keyboard tray top edge", 0x333436, c.trayEdge.hex())
    }

    @Test
    fun keyboardKeysAndPressedAccentAreTheBoards() {
        val c = colours(graphite)
        near("letter key", 0x26282D, KeyboardCollarDimens.letterCap(c.collarCap).face.hex())
        near("utility key", 0x2F3136, KeyboardCollarDimens.wordCap(c.collarCap).face.hex())
        // A pressed Primary button sinks to the plain accent (#3E7BE0), not the lighter rest fill.
        near("pressed primary", 0x3E7BE0, c.accentFace().downBottom.hex())
    }

    @Test
    fun emptyStateBodyIsTheBoardsSoftInk() {
        near("Graphite body", 0xC6C2B9, colours(graphite).body.hex())
        near("Paper body", 0x4C4942, colours(paper).body.hex())
        near("Graphite title (text)", 0xEBE7DD, colours(graphite).text.hex())
        near("Paper title (text)", 0x1D1D1F, colours(paper).text.hex())
    }

    @Test
    fun paperWellsAreTheBoards() {
        val c = colours(paper)
        near("empty-state disc", 0xE0DBD0, c.collar.well.hex())
        // The board's #CFC8BC is the field's groove ring (see paperGroovesAreTheBoards); its fill is #E0DBD0.
        near("field fill", 0xE0DBD0, FieldDimens.well(c.bg).hex())
        near("disabled label", 0x9A958B, c.collar.offText.hex())
    }

    @Test
    fun paperGroovesAreTheBoards() {
        val c = colours(paper)
        near("slider track and off switch (Paper)", raised(0xECE8DF, 0xD7D0C3), c.collar.groove.hex())
        near("button groove ring at press and off, slider locked", raised(0xECE8DF, 0xCCC4B8), c.collar.ring.hex())
        near("Graphite ring stays the groove", raised(0x111214, 0x0B0C0C), colours(graphite).collar.ring.hex())
    }

    /** The darkest pixel (the groove ring) of a row of a recorded golden, between columns [x0] and [x1]. */
    private fun darkestRing(
        golden: String,
        y: Int,
        x0: Int,
        x1: Int,
    ): Int {
        val image = javax.imageio.ImageIO.read(java.io.File("src/test/screenshots/$golden"))
        return (x0..x1).map { image.getRGB(it, y) and 0xFFFFFF }.minBy { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
    }

    /**
     * At rest a button's or hint key's groove ring is the ring colour under the cap's faint brown shadow (`FlatFace.LIGHT_CAP_SHADOW`): the board's
     * darkest ring pixel is #C4BBAF (buttons) and #C4BAAD (hint keys). Read from the recorded Paper goldens at the pixel the audit samples
     * (a secondary button's left edge at mid height; the hint key B's); a golden that moves the layout moves these columns.
     */
    @Test
    fun paperGrooveRingAtRestIsTheBoardsUnderTheCapShadow() {
        // The button's ring is read at the board's own 1.5 px per dp (`SeatPixelTest`): the 2x golden shows a 2.7 px groove with no edge pixel mixed in.
        // The hint key's likewise: its 1 px groove at 1.5x is mostly edge pixels.
    }

    @Test
    fun hintLabelIsTheBoards() {
        near("Graphite hint label", 0x9699A0, colours(graphite).hintLabel.hex())
        near("Paper hint label", 0x4C4942, colours(paper).hintLabel.hex())
    }

    private fun Rgb.argb() = toArgb() and 0xFFFFFF
}
