package com.plumemu.setup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folder step's two points are true on every device and for both inputs: they name no folder ("ROMs": the picker
 * does not open there on every Android) and no button ("Tap", "Use this folder": a gamepad has no tap, and the picker's
 * button is worded by Android's version). Every language file has them, and the confirm step's removed key is gone.
 */
class SetupFolderWordsTest {
    private val res = File("src/main/res")

    private fun valuesOf(dir: File): Map<String, String> {
        val xml = File(dir, "strings.xml").readText()
        return Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val languages = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }.orEmpty().sortedBy { it.name }

    @Test
    fun everyLanguageHasTheTwoPointsAndNoneNamesAFolderOrAButton() {
        assertEquals("values and seven languages", 8, languages.size)
        for (dir in languages) {
            val values = valuesOf(dir)
            val opens = values["setup_folder_point_opens"]
            val use = values["setup_folder_point_use"]
            assertTrue("${dir.name} has the opens line", opens != null)
            assertTrue("${dir.name} has the use line", use != null)
            for (line in listOf(opens!!, use!!)) {
                assertFalse("${dir.name}: $line names the ROMs folder", "ROMs" in line)
                assertFalse("${dir.name}: $line names a button or a tap", Regex("Tap|Use this folder|[\u201C\u201D\u2018\u2019\u300C\u300D\"]").containsMatchIn(line))
            }
            assertTrue("${dir.name}: every bullet ends with a full stop", listOf(opens, use, values["setup_folder_point_private"]!!).all { it.trimEnd().last() in ".\u3002" })
            assertTrue("${dir.name} has the sign-up line", values["settings_account_sign_up"]?.contains("screenscraper.fr") == true)
            assertFalse("${dir.name}: the pressed-key message is gone", "setup_confirm_press_ab" in values)
        }
    }

    @Test
    fun theEnglishWordsAreTheSettledOnes() {
        val english = valuesOf(File(res, "values"))
        assertEquals("Android\\'s folder picker opens. Go to your games folder.", english["setup_folder_point_opens"])
        assertEquals("Then confirm that folder there. On some handhelds this needs a touch of the screen.", english["setup_folder_point_use"])
        assertEquals("Use A", english["setup_confirm_keep"])
    }
}
