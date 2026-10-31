package com.plumemu.platforms

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Catches a hand edit of platforms.json or of the generated `<queries>` manifest, or a converter
 * change whose output was not committed.
 */
class ConverterTest {
    @Test
    fun converterOutputEqualsTheCommittedFileByteForByte() {
        val out = File.createTempFile("platforms", ".json")
        val manifestOut = File.createTempFile("AndroidManifest", ".xml")
        try {
            val process = ProcessBuilder(
                "python3", "-I", "../tools/platforms/convert.py", "--out", out.path, "--manifest-out", manifestOut.path,
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("converter failed: $output", true, process.waitFor(120, TimeUnit.SECONDS))
            assertEquals("converter exit code, output: $output", 0, process.exitValue())
            assertArrayEquals(
                "src/main/assets/platforms.json differs from the converter output; rerun tools/platforms/convert.py",
                File("src/main/assets/platforms.json").readBytes(),
                out.readBytes(),
            )
            assertArrayEquals(
                "src/main/AndroidManifest.xml differs from the converter output; rerun tools/platforms/convert.py",
                File("src/main/AndroidManifest.xml").readBytes(),
                manifestOut.readBytes(),
            )
        } finally {
            out.delete()
            manifestOut.delete()
        }
    }
}
