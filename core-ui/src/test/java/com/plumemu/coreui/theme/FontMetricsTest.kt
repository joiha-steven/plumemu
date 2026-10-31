package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Re-reads the metrics that [PlumFont]'s tuning constants were computed from, straight from the
 * TTF tables, so a font update that changes them fails here instead of shifting sizes silently.
 */
class FontMetricsTest {
    private data class Metrics(
        val unitsPerEm: Int,
        val xHeight: Int,
        val ascent: Int,
        val descent: Int,
    )

    private fun read(name: String): Metrics =
        RandomAccessFile(File("src/main/res/font/$name"), "r").use { f ->
            f.seek(4)
            val tables = f.readUnsignedShort()
            val offsets = HashMap<String, Long>()
            for (i in 0 until tables) {
                f.seek(12L + i * 16)
                val tag = ByteArray(4).also { f.readFully(it) }.toString(Charsets.US_ASCII)
                f.readInt() // checksum
                offsets[tag] = f.readInt().toLong() and 0xFFFFFFFFL
            }
            fun short(
                table: String,
                at: Int,
            ): Int {
                f.seek(offsets.getValue(table) + at)
                return f.readShort().toInt()
            }
            Metrics(
                unitsPerEm = short("head", 18),
                xHeight = short("OS/2", 86), // sxHeight, OS/2 version 2 and later
                ascent = short("hhea", 4),
                descent = short("hhea", 6),
            )
        }

    @Test
    fun sansMetricsMatchTheTuning() {
        val m = read("plum_sans_400.ttf")
        assertEquals(Metrics(2048, 1118, 1984, -494), m)
        assertEquals(PlumFont.Sans.lineHeightEm, (m.ascent - m.descent) / m.unitsPerEm.toFloat(), 1e-6f)
    }

    @Test
    fun serifMetricsMatchTheTuning() {
        val m = read("plum_serif_400.ttf")
        assertEquals(Metrics(1000, 512, 1177, -308), m)
        assertEquals(0.53f, PlumFont.Serif.sizeFactor * m.xHeight / m.unitsPerEm, 1e-6f)
        assertEquals(PlumFont.Serif.lineHeightEm, (m.ascent - m.descent) / m.unitsPerEm.toFloat(), 1e-6f)
    }

    @Test
    fun monoMetricsMatchTheTuning() {
        val m = read("plum_mono_400.ttf")
        assertEquals(Metrics(1000, 550, 1020, -300), m)
        assertEquals(0.53f, PlumFont.Mono.sizeFactor * m.xHeight / m.unitsPerEm, 1e-6f)
        assertEquals(PlumFont.Mono.lineHeightEm, (m.ascent - m.descent) / m.unitsPerEm.toFloat(), 1e-6f)
    }
}
