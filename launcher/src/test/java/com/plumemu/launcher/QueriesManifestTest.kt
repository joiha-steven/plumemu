package com.plumemu.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

/**
 * The `<queries>` that make emulators and launchable apps visible from Android 11: every package
 * the detector may ask about must be listed, or detection silently finds nothing on new devices.
 */
class QueriesManifestTest {
    private val file = File("../platforms/src/main/AndroidManifest.xml")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun document() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(file)

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    @Test
    fun everyEmulatorPackageOfTheShippedDataIsQueried() {
        val queries = document().documentElement.children("queries").single()
        val listed = queries.children("package").map { it.getAttributeNS(android, "name") }.toSet()
        val needed = TestCatalogs.shipped.emulators.flatMap { emulator -> emulator.allPackages.map { it.packageName } }.toSet()
        assertEquals(needed, listed)
    }

    @Test
    fun theAppsEntryIntentIsQueried() {
        val intent = document().documentElement.children("queries").single().children("intent").single()
        assertEquals(listOf("android.intent.action.MAIN"), intent.children("action").map { it.getAttributeNS(android, "name") })
        assertEquals(listOf("android.intent.category.LAUNCHER"), intent.children("category").map { it.getAttributeNS(android, "name") })
    }

    @Test
    fun noPermissionIsDeclared() {
        val root = document().documentElement
        assertEquals(emptyList<Element>(), root.children("uses-permission"))
        assertFalse(file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").contains("QUERY_ALL_PACKAGES"))
    }
}
