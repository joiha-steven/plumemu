package com.plumemu.thortype

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import java.io.File

/** Where review crops go: `build/v2pairs/audit/pairs/_thortype/<name>.png` at the repository root (not in git). */
internal object ThorTypeShots {
    private val dir: File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
            .resolve("build/v2pairs/audit/pairs/_thortype")

    /** Writes the whole root as `<name>.png` and returns its size, so a test can tell a blank shot. */
    fun save(
        compose: ComposeContentTestRule,
        name: String,
    ): Pair<Int, Int> {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap.width to bitmap.height
    }
}
