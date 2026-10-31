package com.plumemu.scraper.media

import android.os.Build
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.work.pngBytes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The `CompressFormat.WEBP` branch (WEBP_LOSSY is API 30). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaSaveBelowApi30Test {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `below API 30 a picture is still saved as WebP`() {
        assertEquals(28, Build.VERSION.SDK_INT)
        val source = File(tmp.root, "box.png").apply { writeBytes(pngBytes(400, 560)) }

        val saved = MediaSaver(File(tmp.root, "media"), lowRam = false, log = {})
            .save(source, "1001", MediaKind.BoxFront, listOf(MediaSize("grid", 200, 200)))

        val media = (saved as MediaSaver.Saved.Files).media.single()
        assertEquals(143 to 200, media.width to media.height)
        assertEquals("RIFF", File(media.path).readBytes().copyOfRange(0, 4).decodeToString())
        assertEquals("WEBP", File(media.path).readBytes().copyOfRange(8, 12).decodeToString())
    }
}
