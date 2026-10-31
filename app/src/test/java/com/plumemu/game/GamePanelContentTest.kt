package com.plumemu.game

import com.plumemu.R
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.SteamAppIdProblem
import com.plumemu.launcher.TitleIdProblem
import com.plumemu.launcher.choice.ChoiceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each panel lists and does, without drawing it: the menu, Play with and every launch message. */
class GamePanelContentTest {
    private val game = GameRef("7", "Chrono Trigger")

    private fun PanelContent.rows() = items.filterIsInstance<PanelItem.Row>().map { it.row }

    private fun PanelContent.commands() = rows().map { it.command }

    @Test
    fun menuEntriesInOrderWithFavoriteOff() {
        // Every entry: Fix box art… (listed only while box art can be asked for) and Other versions…
        // (only for a row of two or more copies) included.
        val rows = gameMenuEntries(sampleFacts(isFavorite = false, canFixBoxArt = true, versions = sampleVersions(), canRemoveFromRecent = true))
        assertEquals(GameMenuEntryId.entries.map { it.name }, rows.map { it.key })
        assertEquals(UiText.Res(R.string.game_menu_favorite), rows[3].label)
        assertEquals(PanelCommand.SetFavorite(true), rows[3].command)
        assertTrue(rows.all { it.isEnabled })
    }

    @Test
    fun aPinnedShortcutHasNoNameOrFixBoxArtEntry() {
        // A shortcut game: no file to name or to look up; Hide, Favorite and Info stay.
        val rows = gameMenuEntries(sampleFacts(canFixBoxArt = true, isShortcut = true))
        assertEquals(
            listOf(GameMenuEntryId.Play, GameMenuEntryId.PlayWith, GameMenuEntryId.Favorite, GameMenuEntryId.Hide, GameMenuEntryId.Info).map { it.name },
            rows.map { it.key },
        )
    }

    @Test
    fun menuOffersUnfavoriteForAFavorite() {
        val rows = gameMenuEntries(sampleFacts(isFavorite = true))
        assertEquals(UiText.Res(R.string.game_menu_unfavorite), rows[2].label)
        assertEquals(PanelCommand.SetFavorite(false), rows[2].command)
    }

    @Test
    fun menuIsTitledWithTheGame() {
        val content = gameMenuContent(game, sampleFacts())
        assertEquals(UiText.Plain("Chrono Trigger"), content.title)
        assertEquals(
            listOf(
                PanelCommand.Play, PanelCommand.OpenPlayWith, PanelCommand.SetFavorite(true), PanelCommand.Hide,
                PanelCommand.OpenNameMenu, PanelCommand.OpenInfo,
            ),
            content.commands(),
        )
    }

    @Test
    fun playWithListsAutomaticFirstThenInstalledThenTheOthersAfterAHeading() {
        val content = playWithContent(sampleListing())
        val rows = content.rows()
        assertEquals(listOf(AUTOMATIC_KEY, "duckstation", "retroarch_mednafen_psx", "get:epsxe", "get:risky"), rows.map { it.key })
        assertEquals(UiText.Res(R.string.game_play_with_automatic, listOf("DuckStation")), rows[0].label)
        assertEquals(PanelCommand.PlayWith(null), rows[0].command)
        assertEquals(PanelCommand.PlayWith("duckstation"), rows[1].command)
        assertEquals(PanelCommand.GetEmulator("com.epsxe.ePSXe", "ePSXe"), rows[3].command)
        assertEquals(UiText.Res(R.string.game_get_it), rows[3].value)
        // The heading sits between the installed and the not installed ones.
        val headingAt = content.items.indexOfFirst { it is PanelItem.Heading }
        assertEquals(3, headingAt)
        assertEquals(UiText.Res(R.string.game_play_with_not_installed), (content.items[headingAt] as PanelItem.Heading).text)
        assertTrue(content.body.isEmpty())
    }

    @Test
    fun automaticIsCurrentWithoutAChoiceAndTheChosenOneOtherwise() {
        assertEquals(listOf(AUTOMATIC_KEY), playWithContent(sampleListing(current = null)).rows().filter { it.isCurrent }.map { it.key })
        assertEquals(listOf("retroarch_mednafen_psx"), playWithContent(sampleListing(current = Beetle.id)).rows().filter { it.isCurrent }.map { it.key })
    }

    @Test
    fun aChosenEmulatorThatWasUninstalledIsMarkedAmongTheOnesToGet() {
        val content = playWithContent(sampleListing(current = Epsxe.id))
        assertEquals(listOf("get:epsxe"), content.rows().filter { it.isCurrent }.map { it.key })
    }

    @Test
    fun withNothingInstalledThereIsNoAutomaticAndTheBodySaysSo() {
        val content = playWithContent(sampleListing(installed = emptyList(), automatic = null))
        assertEquals(listOf("get:epsxe", "get:risky"), content.rows().map { it.key })
        assertEquals(listOf(UiText.Res(R.string.game_no_emulator_title, listOf("PlayStation"))), content.body)
    }

    @Test
    fun legalRiskEmulatorsCarryTheOneLineWhereverTheyAreListed() {
        val installedRisky = playWithContent(sampleListing(installed = listOf(DuckStation, RiskyEmu), notInstalled = listOf(Epsxe)))
        val noted = installedRisky.rows().filter { it.note != null }
        assertEquals(listOf("risky"), noted.map { it.key })
        assertEquals(UiText.Res(R.string.game_legal_risk_note), noted.single().note)
        // Installed, a person may pick it; never offered as Automatic (the listing's automatic comes from the defaults).
        assertEquals(PanelCommand.PlayWith("risky"), noted.single().command)
        assertEquals(listOf("get:risky"), playWithContent(sampleListing()).rows().filter { it.note != null }.map { it.key })
    }

    @Test
    fun startedHasNoMessage() {
        assertNull(launchMessage(FakeGameActions.started("7"), null))
    }

    @Test
    fun emulatorMissingNamesItAndOffersItsStorePageAndAnother() {
        val offer = EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation")
        val message = launchMessage(LaunchOutcome.EmulatorMissing(7, offer, ChoiceSource.PersonForGame), null)!!
        assertEquals(UiText.Res(R.string.game_error_missing_title, listOf("DuckStation")), message.title)
        assertEquals(
            listOf(PanelCommand.GetEmulator("com.github.stenzek.duckstation", "DuckStation"), PanelCommand.OpenPlayWith),
            message.commands(),
        )
        assertEquals(UiText.Res(R.string.game_error_get_named, listOf("DuckStation")), message.rows()[0].label)
    }

    @Test
    fun emulatorMissingThatTheDataNoLongerListsOffersOnlyAnother() {
        val message = launchMessage(LaunchOutcome.EmulatorMissing(7, EmulatorOffer("gone", null, null), ChoiceSource.PersonForSystem), null)!!
        assertEquals(UiText.Res(R.string.game_error_missing_title_unnamed), message.title)
        assertEquals(listOf(PanelCommand.OpenPlayWith), message.commands())
    }

    @Test
    fun noEmulatorNamesTheSystemAndListsTheCandidatesNonLegalRiskFirst() {
        val outcome = LaunchOutcome.NoEmulatorForSystem(7, "psx", listOf(EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation")))
        val message = launchMessage(outcome, sampleListing(installed = emptyList(), automatic = null, notInstalled = listOf(DuckStation, RiskyEmu)))!!
        assertEquals(UiText.Res(R.string.game_no_emulator_title, listOf("PlayStation")), message.title)
        assertEquals(listOf("get:duckstation", "get:risky"), message.rows().map { it.key })
        assertEquals("legal-risk: no Get it", listOf(UiText.Res(R.string.game_get_it), null), message.rows().map { it.value })
        assertEquals(listOf(true, false), message.rows().map { it.isEnabled })
        assertEquals(listOf(null, UiText.Res(R.string.game_legal_risk_note)), message.rows().map { it.note })
    }

    @Test
    fun noEmulatorWithoutAListingFallsBackToTheEnginesCandidates() {
        val outcome = LaunchOutcome.NoEmulatorForSystem(7, "psx", listOf(EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation")))
        val message = launchMessage(outcome, null)!!
        assertEquals(UiText.Res(R.string.game_no_emulator_title, listOf("psx")), message.title)
        assertEquals(listOf(PanelCommand.GetEmulator("com.github.stenzek.duckstation", "DuckStation")), message.commands())
    }

    @Test
    fun fileGoneSaysWhichFileAndOffersARescan() {
        val message = launchMessage(LaunchOutcome.FileMissing(7, "Chrono Trigger (USA).sfc", FileProblem.Gone), null)!!
        assertEquals(UiText.Res(R.string.game_error_file_gone_title), message.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_file_gone_body, listOf("Chrono Trigger (USA).sfc"))), message.body)
        assertEquals(listOf(PanelCommand.RescanLibrary), message.commands())
    }

    @Test
    fun unreachableFolderSaysWhatToCheckAndOffersARescan() {
        val message = launchMessage(LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.FolderUnreachable), null)!!
        assertEquals(UiText.Res(R.string.game_error_folder_title), message.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_folder_body)), message.body)
        assertEquals(listOf(PanelCommand.RescanLibrary), message.commands())
    }

    @Test
    fun aMissingCardSaysTheStorageIsNotFoundAndOffersNoRescan() {
        val message = launchMessage(LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.StorageNotFound), null)!!
        assertEquals(UiText.Res(R.string.game_error_storage_title), message.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_storage_body)), message.body)
        assertEquals(listOf(PanelCommand.Close), message.commands())
    }

    @Test
    fun aVitaFileWithoutTitleIdNamesTheFileAndSaysWhatToWrite() {
        val empty = launchMessage(LaunchOutcome.BadTitleIdFile(7, "Persona 4 Golden.psvita", TitleIdProblem.Empty), null)!!
        assertEquals(UiText.Res(R.string.game_error_title_id_title), empty.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_title_id_empty_body, listOf("Persona 4 Golden.psvita"))), empty.body)
        assertEquals(listOf(PanelCommand.Close), empty.commands())
        val invalid = launchMessage(LaunchOutcome.BadTitleIdFile(7, "Persona 4 Golden.psvita", TitleIdProblem.NotATitleId), null)!!
        assertEquals(listOf(UiText.Res(R.string.game_error_title_id_invalid_body, listOf("Persona 4 Golden.psvita"))), invalid.body)
    }

    @Test
    fun aSteamFileWithoutAppIdNamesTheFileAndSaysWhatToWrite() {
        val empty = launchMessage(LaunchOutcome.BadSteamAppIdFile(7, "Broforce.steam", SteamAppIdProblem.Empty), null)!!
        assertEquals(UiText.Res(R.string.game_error_steam_app_id_title), empty.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_steam_app_id_empty_body, listOf("Broforce.steam"))), empty.body)
        assertEquals(listOf(PanelCommand.Close), empty.commands())
        val invalid = launchMessage(LaunchOutcome.BadSteamAppIdFile(7, "Broforce.steam", SteamAppIdProblem.NotAnAppId), null)!!
        assertEquals(UiText.Res(R.string.game_error_steam_app_id_title), invalid.title)
        assertEquals(listOf(UiText.Res(R.string.game_error_steam_app_id_invalid_body, listOf("Broforce.steam"))), invalid.body)
        assertEquals(listOf(PanelCommand.Close), invalid.commands())
    }

    @Test
    fun aSteamAppNotInstalledIsTheUsualMissingEmulatorMessage() {
        val offer = EmulatorOffer("gamenative", "GameNative", "app.gamenative")
        val message = launchMessage(LaunchOutcome.EmulatorMissing(7, offer, ChoiceSource.PersonForSystem), null)!!
        assertEquals(UiText.Res(R.string.game_error_missing_title, listOf("GameNative")), message.title)
        assertEquals(listOf(PanelCommand.GetEmulator("app.gamenative", "GameNative"), PanelCommand.OpenPlayWith), message.commands())
    }

    @Test
    fun needsFilePathNamesTheEmulatorAndOffersAnother() {
        val outcome = LaunchOutcome.NeedsFilePath(7, EmulatorOffer("retroarch_snes9x", "RetroArch (Snes9x)", "com.retroarch.aarch64"))
        val message = launchMessage(outcome, null)!!
        assertEquals(UiText.Res(R.string.game_error_needs_path_title, listOf("RetroArch (Snes9x)")), message.title)
        assertEquals(listOf(PanelCommand.OpenPlayWith), message.commands())
    }

    @Test
    fun everyFailureReasonHasItsOwnTitleAndBody() {
        val expected =
            mapOf(
                FailureReason.GameNotInLibrary to (R.string.game_error_not_in_library_title to PanelCommand.RescanLibrary),
                FailureReason.UnknownSystem to (R.string.game_error_unknown_system_title to PanelCommand.Close),
                FailureReason.ActivityNotFound to (R.string.game_error_start_title to PanelCommand.OpenPlayWith),
                FailureReason.NotAllowed to (R.string.game_error_start_title to PanelCommand.OpenPlayWith),
                FailureReason.StartFailed to (R.string.game_error_start_title to PanelCommand.OpenPlayWith),
                FailureReason.DatabaseError to (R.string.game_error_database_title to PanelCommand.Close),
                FailureReason.Internal to (R.string.game_error_internal_title to PanelCommand.Close),
            )
        assertEquals("every reason is covered", FailureReason.entries.toSet(), expected.keys)
        val bodies = mutableSetOf<UiText>()
        for ((reason, pair) in expected) {
            val message = launchMessage(LaunchOutcome.Failed(7, reason), null)!!
            assertEquals(reason.name, UiText.Res(pair.first), message.title)
            assertEquals(reason.name, listOf(pair.second), message.commands())
            assertEquals(reason.name, 1, message.body.size)
            bodies += message.body.single()
        }
        assertEquals("a body per reason", FailureReason.entries.size, bodies.size)
    }

    @Test
    fun onlyEnabledRowsTakeTheFocus() {
        val content =
            PanelContent(
                UiText.Plain("t"),
                items =
                    listOf(
                        PanelItem.Row(PanelRow("a", UiText.Plain("a"), PanelCommand.Close, isEnabled = false)),
                        PanelItem.Heading(UiText.Plain("h")),
                        PanelItem.Row(PanelRow("b", UiText.Plain("b"), PanelCommand.Close)),
                    ),
            )
        assertEquals(listOf("b"), content.focusableRows.map { it.key })
        assertFalse(content.focusableRows.any { !it.isEnabled })
    }

    @Test
    fun legalRiskEmulatorsToGetHaveNoGetIt() {
        val rows = playWithContent(sampleListing()).rows()
        val risky = rows.single { it.key == "get:risky" }
        assertNull(risky.value)
        assertFalse(risky.isEnabled)
        assertEquals(UiText.Res(R.string.game_legal_risk_note), risky.note)
        assertEquals(UiText.Res(R.string.game_get_it), rows.single { it.key == "get:epsxe" }.value)
    }

    @Test
    fun theFirstLaunchAsksAmongTheInstalledOnesRecommendedFirstAndKeepsItForTheSystem() {
        val offers = listOf(
            EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation"),
            EmulatorOffer("risky", "Risky Emu", "org.example.risky", isLegalRisk = true),
            EmulatorOffer("retroarch_mednafen_psx", "RetroArch (Beetle PSX)", "com.retroarch.aarch64"),
        )
        val message = launchMessage(LaunchOutcome.EmulatorNotChosen(7, "psx", "PlayStation", offers, "duckstation"), sampleListing())!!
        assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("PlayStation")), message.title)
        assertEquals(listOf(UiText.Res(R.string.game_choose_emulator_body, listOf("PlayStation"))), message.body)
        // Only installed ones: nothing to get, no heading; the recommended one first, so the focus is on it.
        assertEquals(listOf("duckstation", "risky", "retroarch_mednafen_psx"), message.rows().map { it.key })
        assertTrue(message.items.none { it is PanelItem.Heading })
        assertEquals("duckstation", message.focusableRows.first().key)
        assertEquals(UiText.Res(R.string.game_choose_emulator_recommended), message.rows()[0].value)
        assertEquals(listOf(null, null), message.rows().drop(1).map { it.value })
        assertEquals(PanelCommand.PlayWith("duckstation", forSystem = true), message.rows()[0].command)
        assertTrue(message.rows().all { (it.command as PanelCommand.PlayWith).forSystem })
        assertEquals(UiText.Res(R.string.game_legal_risk_note), message.rows()[1].note)
    }

    @Test
    fun aLegalRiskSystemIsAskedWithNoRecommendationAndNothingToGet() {
        val offers = listOf(EmulatorOffer("risky", "Risky Emu", "org.example.risky", isLegalRisk = true))
        val listing = sampleListing(installed = listOf(RiskyEmu), automatic = null, notInstalled = listOf(Epsxe)).copy(systemName = "Switch")
        val message = launchMessage(LaunchOutcome.EmulatorNotChosen(7, "switch", "Switch", offers, null), listing)!!
        assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("Switch")), message.title)
        assertEquals(listOf("risky"), message.rows().map { it.key })
        assertNull(message.rows()[0].value)
        assertEquals(UiText.Res(R.string.game_legal_risk_note), message.rows()[0].note)
        assertEquals(PanelCommand.PlayWith("risky", forSystem = true), message.rows()[0].command)
    }

    @Test
    fun anEmulatorInstalledSinceTheLaunchLookedGetsTheSameQuestion() {
        val outcome = LaunchOutcome.NoEmulatorForSystem(7, "switch", emptyList())
        val listing = sampleListing(installed = listOf(RiskyEmu), automatic = null, notInstalled = listOf(Epsxe)).copy(systemName = "Switch")
        val message = launchMessage(outcome, listing)!!
        assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("Switch")), message.title)
        assertEquals(listOf("risky"), message.rows().map { it.key })
        assertEquals(PanelCommand.PlayWith("risky", forSystem = true), message.rows()[0].command)
    }

    @Test
    fun playWithSaysAutomaticAsksWhileNothingIsChosenForTheSystem() {
        val content = playWithContent(sampleListing().copy(isSystemChosen = false))
        val automatic = content.rows().first()
        assertEquals(UiText.Res(R.string.game_play_with_ask), automatic.label)
        assertEquals(PanelCommand.PlayWith(null), automatic.command)
        assertTrue(automatic.isCurrent)
        assertEquals(1, content.rows().count { it.key == AUTOMATIC_KEY })
    }

    @Test
    fun automaticThatWouldStartAMissingSystemChoiceCannotBePicked() {
        val listing = sampleListing(automatic = Epsxe).copy(isAutomaticInstalled = false)
        val content = playWithContent(listing)
        val automatic = content.rows().first()
        assertEquals(UiText.Res(R.string.game_play_with_automatic_missing, listOf("ePSXe")), automatic.label)
        assertFalse(automatic.isEnabled)
        assertEquals(listOf(UiText.Res(R.string.game_play_with_system_choice_missing, listOf("ePSXe", "PlayStation"))), content.body)
        assertEquals("the installed ones are offered first", "duckstation", content.focusableRows.first().key)
    }

    @Test
    fun automaticFollowsTheLaunchOrder() {
        val all = listOf(DuckStation, Beetle, Epsxe)
        val defaults = listOf(DuckStation, Beetle)
        assertEquals("no system choice: the first installed default", Beetle to true, automaticChoice(null, all, defaults, setOf(Beetle.id)))
        assertEquals("the system choice wins over the defaults", Epsxe to true, automaticChoice(Epsxe.id, all, defaults, setOf(Epsxe.id, DuckStation.id)))
        assertEquals("a missing system choice is reported, not swapped", Epsxe to false, automaticChoice(Epsxe.id, all, defaults, setOf(DuckStation.id)))
        assertEquals("gone from the data: named by its id", "old" to false, automaticChoice("old", all, defaults, emptySet())?.let { it.first.name to it.second })
        assertNull("nothing installed", automaticChoice(null, all, defaults, emptySet()))
    }

    @Test
    fun infoEndsWithAFocusedBackRow() {
        val content = OpenPanel(game, PanelScreen.Info(sampleFacts())).content
        assertEquals(listOf(INFO_BACK_KEY), content.focusableRows.map { it.key })
        assertEquals(PanelCommand.Back, content.focusableRows.single().command)
    }

    @Test
    fun aMissingLegalRiskEmulatorIsNeverOfferedFromAStore() {
        val offer = EmulatorOffer("eden", "Eden", "dev.eden.eden_emulator", isLegalRisk = true)
        val message = launchMessage(LaunchOutcome.EmulatorMissing(7, offer, ChoiceSource.PersonForGame), null)!!
        assertEquals(listOf(PanelCommand.OpenPlayWith), message.commands())
        assertTrue("the R30 line is said", UiText.Res(R.string.game_legal_risk_note) in message.body)
    }

    @Test
    fun noEmulatorWhenEveryCandidateIsLegalRiskSaysSoAndHasAFocusedOk() {
        val outcome = LaunchOutcome.NoEmulatorForSystem(7, "switch", emptyList())
        val listing = sampleListing(installed = emptyList(), automatic = null, notInstalled = listOf(RiskyEmu, RiskyEmu.copy(id = "risky2"))).copy(systemName = "Switch")
        val message = launchMessage(outcome, listing)!!
        assertEquals(listOf(UiText.Res(R.string.game_error_no_emulator_offered_body)), message.body)
        assertEquals(listOf("ok"), message.focusableRows.map { it.key })
    }

    @Test
    fun playWithWithNothingToPickStillHasAFocusedRow() {
        val content = playWithContent(sampleListing(installed = emptyList(), automatic = null, notInstalled = listOf(RiskyEmu)))
        assertEquals(listOf("back"), content.focusableRows.map { it.key })
        assertEquals(PanelCommand.Back, content.focusableRows.single().command)
        assertTrue(UiText.Res(R.string.game_error_no_emulator_offered_body) in content.body)
    }
}
