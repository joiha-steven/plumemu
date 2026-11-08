package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The focus costs no layer (docs/design/rules.md > Focus and current): a cell scales by a canvas transform in its own draw, and the rim, light and
 * shadow are one cached image and one stroke drawn by the grid, so no cell, focused or not, gets a `graphicsLayer`, an offscreen layer, a clip that
 * makes one or a blur. Read from the sources (the layers are in the code, not in a frame).
 */
class FocusDrawCostTest {
    private fun source(path: String): String {
        val file = listOf(File(path), File("../$path")).first { it.exists() }
        // Comments out: they may name what the code must not do.
        return file.readText().lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.joinToString("\n")
    }

    private val layerWords = listOf("graphicsLayer", "CompositingStrategy", "Modifier.clip(", ".clip(", "BlurMaskFilter", "RenderEffect", "plainLayer", "rememberGraphicsLayer", "drawIntoCanvas")

    @Test
    fun theFocusCodeMakesNoLayerAndNoBlur() {
        for (path in listOf("app/src/main/java/com/plumemu/home/FocusOutline.kt", "core-ui/src/main/java/com/plumemu/coreui/components/FocusLiftDraw.kt", "core-ui/src/main/java/com/plumemu/coreui/theme/FocusLift.kt")) {
            val text = source(path)
            for (word in layerWords) assertFalse("$path uses $word", text.contains(word))
        }
    }

    @Test
    fun noCellOfTheGridsGetsALayerForTheFocus() {
        val apps = source("app/src/main/java/com/plumemu/home/AppsGrid.kt")
        // The only layer in the Apps grid is the preview's column shift, which is not the focus's and is off in the real grid.
        for (word in layerWords.filter { it != "plainLayer" }) assertFalse("AppsGrid uses $word", apps.contains(word))
        assertEquals("one plainLayer: the preview's spread", 1, Regex("plainLayer \\{").findAll(apps).count())
        val covers = source("app/src/main/java/com/plumemu/home/HomeGrid.kt")
        // The only layer in the cover grid is the dimmed Favorites cover's alpha: not the focus's.
        assertEquals("one graphicsLayer: the dimmed cover", 1, Regex("graphicsLayer \\{").findAll(covers).count())
        for (word in layerWords.filter { it != "graphicsLayer" && it != "drawIntoCanvas" }) assertFalse("HomeGrid uses $word", covers.contains(word))
    }
}
