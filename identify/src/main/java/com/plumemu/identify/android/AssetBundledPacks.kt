package com.plumemu.identify.android

import android.content.res.AssetManager
import com.plumemu.identify.pack.BundledPacks
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * The packs shipped in the app's assets: `identify/packs.txt` and `identify/<fileName>`. The only
 * Android class of the module; it opens files and nothing else (parsing is in
 * [com.plumemu.identify.pack.BundledIndex], installing in [com.plumemu.identify.pack.PackStore]).
 */
class AssetBundledPacks(private val assets: AssetManager) : BundledPacks {
    @Throws(IOException::class)
    override fun readIndex(): String? = try {
        assets.open(INDEX).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: FileNotFoundException) {
        // An app built without packs has no index; that is not an error.
        null
    }

    /**
     * Opens `identify/<fileName>`. The Android Gradle plugin un-gzips `.gz` assets when it merges
     * them (and drops the extension), so in an APK a bundled `arcade.pack.gz` would be `arcade.pack`, stored
     * deflated by the APK itself; that name is tried when the `.gz` one is missing.
     * [com.plumemu.identify.pack.PackStore] takes either form.
     */
    @Throws(IOException::class)
    override fun open(fileName: String): InputStream = try {
        assets.open("$FOLDER/$fileName")
    } catch (e: FileNotFoundException) {
        if (!fileName.endsWith(GZIP_SUFFIX)) throw e
        assets.open("$FOLDER/${fileName.removeSuffix(GZIP_SUFFIX)}")
    }

    private companion object {
        const val FOLDER = "identify"
        const val INDEX = "$FOLDER/packs.txt"
        const val GZIP_SUFFIX = ".gz"
    }
}
