package com.plumemu.library

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loads and initialises every `com.plumemu` class in this test APK (library, identify, core-data,
 * scraper) on the device, and fails on any that cannot be loaded or whose static initialiser
 * throws (any `LinkageError`); all of them are listed in one failure.
 *
 * Android's regex engine (ICU) is stricter than the JVM's: an unescaped `]` or `}` that every JVM
 * test accepted made `TitleCleaner` fail to load on Android 6, so every scan failed there. A `Regex` in an `object` or a
 * top-level `val` is compiled by the static initialiser, so this test catches it on the device.
 * Patterns built inside functions are not covered.
 */
@RunWith(AndroidJUnit4::class)
class StaticInitialisersTest {
    @Test
    fun everyClassInitialisesOnTheDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = classNamesIn(context.packageCodePath).filter { it.startsWith("com.plumemu.") }
        val failures = names.mapNotNull { name ->
            try {
                Class.forName(name, true, javaClass.classLoader)
                null
            } catch (e: LinkageError) {
                // ExceptionInInitializerError (a static initialiser threw), NoClassDefFoundError,
                // VerifyError: every one is collected, so one run names all the broken classes.
                val root = generateSequence<Throwable>(e) { it.cause }.last()
                "$name: ${e.javaClass.simpleName} (${root.javaClass.simpleName}: ${root.message?.replace('\n', ' ')})"
            }
        }
        Log.i(TAG, "classes=${names.size} failed=${failures.size}")
        failures.forEach { Log.i(TAG, "failed $it") }

        assertTrue("found only ${names.size} classes in ${context.packageCodePath}", names.size > MIN_CLASSES)
        assertTrue("no identify class among them", IDENTIFY_CLASS in names)
        assertTrue("static initialisers failed on the device:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Suppress("DEPRECATION") // DexFile is the one way to list an APK's classes on API 23.
    private fun classNamesIn(apk: String): List<String> {
        val dex = dalvik.system.DexFile(apk)
        try {
            return dex.entries().toList()
        } finally {
            dex.close()
        }
    }

    private companion object {
        const val TAG = "PlumStaticInit"
        const val MIN_CLASSES = 100
        const val IDENTIFY_CLASS = "com.plumemu.identify.name.TitleCleaner"
    }
}
