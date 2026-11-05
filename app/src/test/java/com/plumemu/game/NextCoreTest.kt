package com.plumemu.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What follows a "No" to the core question: the next core of the data's order, or how to get one. */
class NextCoreTest {
    private fun core(id: String, name: String) = EmulatorItem("retroarch-$id", "RetroArch: $name", "com.retroarch", isLegalRisk = false, core = "${id}_libretro_android.so")

    private val mgba = core("mgba", "mGBA")
    private val gpsp = core("gpsp", "gpSP")
    private val vbam = core("vbam", "VBA-M")
    private val standalone = EmulatorItem("my-boy", "My Boy!", "com.fastemulator.gba", isLegalRisk = false)
    private val listing = sampleListing(installed = listOf(standalone, mgba, gpsp, vbam), notInstalled = emptyList())

    private fun rows(content: PanelContent) = content.items.filterIsInstance<PanelItem.Row>().map { it.row }

    @Test
    fun theNextCoreIsTheNextOneInTheDataOrder() {
        assertEquals(CoreFollowUp.Next(gpsp), coreFollowUp(listing, mgba.id, emptySet()))
    }

    @Test
    fun aCoreAnsweredNoIsNotOfferedAgain() {
        assertEquals(CoreFollowUp.Next(vbam), coreFollowUp(listing, gpsp.id, setOf(mgba.id)))
        assertEquals("the one that failed is never its own next", CoreFollowUp.Next(vbam), coreFollowUp(listing, mgba.id, setOf(gpsp.id)))
    }

    @Test
    fun withNoCoreLeftTheFirstOfTheOrderIsTheOneToGet() {
        assertEquals(CoreFollowUp.NoneLeft(mgba), coreFollowUp(listing, vbam.id, setOf(mgba.id, gpsp.id)))
    }

    @Test
    fun aSystemWithOneCoreHasNoneLeftAtOnce() {
        val only = sampleListing(installed = listOf(standalone, mgba), notInstalled = emptyList())
        assertEquals(CoreFollowUp.NoneLeft(mgba), coreFollowUp(only, mgba.id, emptySet()))
    }

    @Test
    fun aRetroArchThatWasUninstalledMeanwhileKeepsTheOldMessage() {
        val gone = sampleListing(installed = listOf(standalone), notInstalled = listOf(mgba, gpsp, vbam))
        assertNull(coreFollowUp(gone, mgba.id, emptySet()))
        val message = coreMissingMessage(gone, mgba.id, mgba.name)
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_missing_body, listOf(mgba.name)), message.body.first())
    }

    @Test
    fun anEmulatorWithoutACoreHasNoFollowUp() {
        assertNull(coreFollowUp(listing, standalone.id, emptySet()))
        assertNull("an emulator the listing does not know", coreFollowUp(listing, "gone", emptySet()))
    }

    @Test
    fun theOfferIsTheFirstFocusedRowAndTheOtherEmulatorsFollowIt() {
        val message = coreMissingMessage(listing, mgba.id, mgba.name)
        val rows = rows(message)
        assertEquals(listOf(CORE_TRY_KEY, "my-boy", vbam.id), rows.map { it.key })
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_try_row, listOf("gpSP")), rows[0].label)
        assertEquals("stored for this game, never for the system", PanelCommand.PlayWith(gpsp.id, forSystem = false), rows[0].command)
        assertEquals(rows[0].key, message.focusableRows.first().key)
        assertEquals("the others are for this game too", PanelCommand.PlayWith("my-boy", forSystem = false), rows[1].command)
        assertEquals(UiText.Res(com.plumemu.R.string.game_play_with_title), message.title)
    }

    @Test
    fun coresAnsweredNoAreLeftOutOfTheOtherEmulators() {
        val rows = rows(coreMissingMessage(listing, gpsp.id, gpsp.name, tried = setOf(mgba.id)))
        assertEquals(listOf(CORE_TRY_KEY, "my-boy"), rows.map { it.key })
        assertEquals(PanelCommand.PlayWith(vbam.id, forSystem = false), rows[0].command)
    }

    @Test
    fun withNoCoreLeftTheMessageSaysHowToGetOneAndNamesTheFirstCore() {
        val message = coreMissingMessage(listing, vbam.id, vbam.name, tried = setOf(mgba.id, gpsp.id))
        assertEquals(listOf("my-boy"), rows(message).map { it.key })
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_none_left_body, listOf(vbam.name, "mGBA")), message.body.first())
    }

    @Test
    fun withNoCoreLeftAndNothingElseOkCloses() {
        val only = sampleListing(installed = listOf(mgba), notInstalled = emptyList())
        val message = coreMissingMessage(only, mgba.id, mgba.name)
        assertTrue(rows(message).single().command == PanelCommand.Close)
    }

    @Test
    fun anEmulatorWithoutACoreKeepsTheOldMessage() {
        val message = coreMissingMessage(listing, standalone.id, standalone.name)
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_missing_body, listOf(standalone.name)), message.body.first())
        assertEquals(listOf(mgba.id, gpsp.id, vbam.id), rows(message).map { it.key })
    }

    private val mameCurrent = core("mamearcade", "MAME - Current")
    private val fbneo = core("fbneo", "FinalBurn Neo")
    private val plus = core("mame2003-plus", "MAME 2003-Plus")
    private val arcade = sampleListing(installed = listOf(mameCurrent, fbneo, plus), notInstalled = emptyList()).copy(isArcade = true)

    @Test
    fun anOldMame2003PlusNameOffersThatCoreFirstWhenItIsInstalled() {
        val old = arcade.copy(oldArcadeName = true)
        assertEquals(CoreFollowUp.Next(plus), coreFollowUp(old, mameCurrent.id, emptySet()))
        assertEquals("once answered No it is not offered again", CoreFollowUp.Next(fbneo), coreFollowUp(old, mameCurrent.id, setOf(plus.id)))
        assertEquals("the failed one is never its own next", CoreFollowUp.Next(mameCurrent), coreFollowUp(old, plus.id, emptySet()))
        val withoutIt = old.copy(installed = listOf(mameCurrent, fbneo))
        assertEquals("not installed: the usual order", CoreFollowUp.Next(fbneo), coreFollowUp(withoutIt, mameCurrent.id, emptySet()))
    }

    @Test
    fun withoutTheSignalTheOrderIsTheDataOrder() {
        assertEquals(CoreFollowUp.Next(fbneo), coreFollowUp(arcade, mameCurrent.id, emptySet()))
    }

    @Test
    fun anArcadeAnswerSaysSetsAreMadeForOneEmulatorVersion() {
        val generic = coreMissingMessage(arcade, mameCurrent.id, mameCurrent.name)
        assertEquals(listOf(CORE_TRY_KEY), rows(generic).map { it.key }.take(1))
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_arcade_note), generic.body[1])
        val old = coreMissingMessage(arcade.copy(oldArcadeName = true), mameCurrent.id, mameCurrent.name)
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_arcade_old_name_note), old.body[1])
        assertEquals(UiText.Res(com.plumemu.R.string.game_core_next_body, listOf(mameCurrent.name)), old.body[0])
        assertEquals("Try MAME 2003-Plus first", UiText.Res(com.plumemu.R.string.game_core_try_row, listOf("MAME 2003-Plus")), rows(old).first().label)
        val none = coreMissingMessage(arcade, plus.id, plus.name, tried = setOf(mameCurrent.id, fbneo.id))
        assertEquals("the note stays when no core is left", UiText.Res(com.plumemu.R.string.game_core_arcade_note), none.body[1])
    }

    @Test
    fun aGameThatIsNotArcadeGetsNoNote() {
        val message = coreMissingMessage(listing, mgba.id, mgba.name)
        assertEquals(1, message.body.size)
    }

    @Test
    fun theOldNameNoteNeverPointsAtTheCoreThatFailedOrWasRefused() {
        val old = arcade.copy(oldArcadeName = true)
        val plain = UiText.Res(com.plumemu.R.string.game_core_arcade_note)
        assertEquals("plus failed", plain, coreMissingMessage(old, plus.id, plus.name).body[1])
        assertEquals("plus already refused", plain, coreMissingMessage(old, mameCurrent.id, mameCurrent.name, tried = setOf(plus.id)).body[1])
        assertEquals("none left", plain, coreMissingMessage(old, fbneo.id, fbneo.name, tried = setOf(plus.id, mameCurrent.id)).body[1])
    }
}
