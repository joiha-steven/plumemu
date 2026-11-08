package com.plumemu.settings

import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings stored by the five sets that were taken out and mapped (Dot matrix, Super, Indigo, Charcoal, Cream) draw as the set that
 * replaced each, buttons included, with nothing rewritten: through the app's mapping and through a backup file. The first colour
 * write on such a look stores the successor's colours with it. Paper is not mapped: its look is two presets anyone can pick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RemovedSetsMigrationTest {
    private class Old(
        val name: String,
        val successor: ColorSet,
        val stored: AppearanceSettings,
    )

    private fun own(
        background: Int,
        text: TextColorSetting,
        accent: Int,
        buttonSet: String?,
    ) = AppearanceSettings(ColorSetting.Custom(background), text, ColorSetting.Custom(accent), buttonSet = buttonSet)

    private val removed =
        listOf(
            Old("Dot matrix", ColorSet.PeaGreen, own(0xB4DB11, TextColorSetting.Dark, 0x306130, "None")),
            Old("Super", ColorSet.Ivory, own(0xD9D8D3, TextColorSetting.Dark, 0xAA2924, "Super")),
            Old("Indigo", ColorSet.SpiceCube, own(0x262158, TextColorSetting.Light, 0xA79CF5, "Indigo")),
            Old("Charcoal", ColorSet.ColdGrey, AppearanceSettings(ColorSetting.Custom(0x2A2B2E), TextColorSetting.Light, ColorSetting.Preset("Green"), buttonSet = "Charcoal")),
            Old("Cream", ColorSet.RedAndGold, own(0xE9DFC8, TextColorSetting.Dark, 0x9A1F2B, "Cream")),
        )

    private fun assertDrawnAs(
        old: Old,
        stored: AppearanceSettings,
    ) {
        val theme = stored.toThemeSettings(animationsOn = true)
        val set = old.successor
        assertEquals("${old.name} reads as ${set.name}", set, ColorSet.matching(theme))
        assertEquals("${old.name} page", set.backgroundColor, theme.backgroundColor)
        assertEquals("${old.name} accent", set.accentColor, theme.accentColor)
        assertEquals("${old.name} text", set.textMode.color, theme.textColor)
        // A stored name resolves to the successor's keys (none where it has none); an unnamed look keeps the keys of the colours.
        assertEquals("${old.name} keys", set.takeIf { it.buttons != null }, theme.buttonSet)
    }

    @Test
    fun everyRemovedSetDrawsAsItsSuccessorWithItsKeys() {
        for (old in removed) assertDrawnAs(old, old.stored)
    }

    @Test
    fun theStoredValuesAreNotRewrittenByReadingThem() {
        for (old in removed) {
            val before = old.stored.copy()
            old.stored.toThemeSettings(animationsOn = true)
            assertEquals(old.name, before, old.stored)
        }
    }

    @Test
    fun aRemovedSetsNameStoredForItsButtonsResolvesToTheSuccessor() {
        assertEquals(ColorSet.Ivory, ColorSet.named("Super"))
        assertEquals(ColorSet.ColdGrey, ColorSet.named("Charcoal"))
        assertEquals(ColorSet.RedAndGold, ColorSet.named("Cream"))
        assertEquals(ColorSet.SpiceCube, ColorSet.named("Indigo"))
        assertEquals(ColorSet.PeaGreen, ColorSet.named("DotMatrix"))
        assertNull(ColorSet.named("Paper"))
        assertNull(ColorSet.named("Nothing"))
    }

    @Test
    fun theOtherAccentsTheRemovedSetsHadAreReadToo() {
        assertDrawnAs(removed[1], own(0xD9D8D3, TextColorSetting.Dark, 0xB22B26, "Super"))
        assertDrawnAs(removed[0], own(0xB8E011, TextColorSetting.Dark, 0x2E5E2E, "None"))
    }

    @Test
    fun aBackupHoldingARemovedSetsLookRestoresAndDrawsTheSuccessor() {
        for (old in removed) {
            val text = SettingsJson.encode(PlumSettings(appearance = old.stored)).toString()
            val restored = SettingsJson.decode(text).settings
            assertEquals("${old.name} stored values restore as they were", old.stored, restored.appearance)
            assertDrawnAs(old, restored.appearance)
        }
    }

    @Test
    fun aBackupHoldingSuperButtonsDrawsIvorysKeys() {
        val json = SettingsJson.encode(PlumSettings(appearance = removed[1].stored))
        assertEquals("Super", json.getJSONObject("appearance").getString("buttonSet"))
        val buttons = SettingsJson.decode(json.toString()).settings.appearance.toThemeSettings(animationsOn = true).buttonSet
        assertEquals(ColorSet.Ivory, buttons)
        assertEquals(ColorSet.Ivory.buttons, buttons?.buttons)
    }

    /** Every removed look, plus the old default on Black. */
    private val oldLooks =
        removed.map { it.name to (it.stored to it.successor) } +
            ("old default" to (AppearanceSettings(ColorSetting.Preset("Black"), TextColorSetting.Light, ColorSetting.Custom(0x3E7BE0)) to ColorSet.Plumemu))

    private fun pageOf(stored: AppearanceSettings) = stored.toThemeSettings(animationsOn = true).backgroundColor

    /** Changing only the accent of a removed look stores the successor's page with it: the old page never comes back. */
    @Test
    fun anAccentChangeOnARemovedLookKeepsTheSuccessorsPage() {
        for ((name, look) in oldLooks) {
            val (stored, successor) = look
            val write = stored.writeAccent(ColorSetting.Custom(if (successor.textMode.name == "Light") 0xF0F0F0 else 0x101010))
            assertNull("$name: the accent is accepted", write.refusal)
            assertEquals("$name: the page drawn after the write", successor.backgroundColor, pageOf(write.appearance))
            assertEquals("$name: the page is stored", successor.background.toSetting(), write.appearance.background)
        }
    }

    /** Changing the text keeps the successor's page and accent. */
    @Test
    fun aTextChangeOnARemovedLookKeepsTheSuccessorsPageAndAccent() {
        for ((name, look) in oldLooks) {
            val (stored, successor) = look
            val write = stored.writeText(stored.textColor)
            assertNull("$name: the text is accepted", write.refusal)
            assertEquals("$name: page", successor.background.toSetting(), write.appearance.background)
            assertEquals("$name: accent", successor.accent.toSetting(), write.appearance.accent)
            assertEquals("$name: still the successor set", successor, write.appearance.colorSet())
        }
    }

    /** A new background keeps the successor's accent (the old one would come back otherwise). */
    @Test
    fun aBackgroundChangeOnARemovedLookKeepsTheSuccessorsAccent() {
        for ((name, look) in oldLooks) {
            val (stored, successor) = look
            val page = if (successor.textMode.name == "Light") ColorSetting.Preset("Graphite") else ColorSetting.Preset("Mist")
            val write = stored.writeBackground(page)
            assertEquals("$name: page", page, write.appearance.background)
            if (!write.accentAdjusted) assertEquals("$name: accent", successor.accent.toSetting(), write.appearance.accent)
        }
    }

    /** A backup holding a removed look restores as stored, and the first colour write after it stores the successor. */
    @Test
    fun aRestoredRemovedLookIsSettledOnTheFirstColourWrite() {
        for ((name, look) in oldLooks) {
            val (stored, successor) = look
            val restored = SettingsJson.decode(SettingsJson.encode(PlumSettings(appearance = stored)).toString()).settings.appearance
            assertEquals("$name: restored as stored", stored.background, restored.background)
            val written = restored.writeText(restored.textColor).appearance
            assertEquals("$name: page after the first write", successor.background.toSetting(), written.background)
            assertEquals("$name: accent after the first write", successor.accent.toSetting(), written.accent)
        }
    }

    /** A look that is no removed one is written as it is. */
    @Test
    fun anOrdinaryLookIsNotTouched() {
        val mine = AppearanceSettings(ColorSetting.Preset("Black"), TextColorSetting.Light, ColorSetting.Preset("Green"))
        assertEquals(mine, mine.withSettledColors())
        assertEquals(ColorSetting.Preset("Black"), mine.writeAccent(ColorSetting.Preset("Amber")).appearance.background)
    }

    /** The Paper page with the Red accent, picked by hand, is drawn and written as chosen (the Paper set is not mapped). */
    @Test
    fun aHandPickedPaperAndRedStaysAsChosen() {
        val paper = AppearanceSettings(ColorSetting.Preset("Paper"), TextColorSetting.Dark, ColorSetting.Preset("Red"))
        assertEquals(paper, paper.withSettledColors())
        assertNull(paper.toThemeSettings(animationsOn = true).let { ColorSet.matching(it) })
        assertEquals(ColorSetting.Preset("Paper"), paper.writeAccent(ColorSetting.Preset("Amber")).appearance.background)
    }
}
