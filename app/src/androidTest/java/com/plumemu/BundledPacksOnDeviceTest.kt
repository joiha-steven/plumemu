package com.plumemu

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.identify.IdentifyLog
import com.plumemu.identify.android.AssetBundledPacks
import com.plumemu.identify.pack.PackStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real `.pack.lzma` assets of the installed APK unpack on a device (the pack bundle is stored, not
 * deflated, and decoded by identify's own LZMA decoder) and answer a lookup. The CRC is the first row
 * of the bundled `nes` pack. Run with `ANDROID_SERIAL` set to an emulator.
 */
@RunWith(AndroidJUnit4::class)
class BundledPacksOnDeviceTest {
    @Test fun bundledLzmaPacksUnpackAndAnswerOnTheDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "bundled-packs-test").also { it.deleteRecursively() }
        val log = IdentifyLog { Log.i("BundledPacksTest", it) }
        val store = PackStore(dir, AssetBundledPacks(context.assets), log)
        for (id in listOf("nes", "arcade", "switch")) {
            val started = System.nanoTime()
            val pack = store.packFor(id)
            Log.i("BundledPacksTest", "$id: first use ${(System.nanoTime() - started) / 1_000_000} ms")
            assertNotNull("$id: no pack", pack)
            assertTrue(File(dir, "$id.pack").length() > 1000)
        }
        val release = store.packFor("nes")!!.byCrc(0x46b71L, 262160L)
        assertTrue("no release for the known CRC", release.isNotEmpty())
        Log.i("BundledPacksTest", "known CRC 00046b71 is ${release.first().title}")
    }
}
