package com.plumemu.setup

import com.plumemu.account.SignInForm
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.secondscreen.ScreenKind

/** The settings setup shows or asks about, read together. */
internal data class SetupChoices(
    val usageStatistics: Boolean,
    /** The second screen's kind while it should be asked about; null otherwise. */
    val secondScreen: ScreenKind?,
    /** The stored ScreenScraper account: none, usable, or one ScreenScraper refused. */
    val account: AccountUse,
    /** `library.box_art_source` as stored; null until the person chooses. */
    val boxArtSource: BoxArtSourceSetting?,
    /** A gamepad is connected. */
    val hasGamepad: Boolean,
) {
    /** A ScreenScraper account is stored and not refused. */
    val hasAccount: Boolean get() = account == AccountUse.Usable
}

internal enum class AccountUse { None, Usable, Refused }

/** What came back from Android's folder picker. */
internal sealed interface PickResult {
    data class Picked(val treeUri: String) : PickResult

    /** The person backed out of the picker: nothing changes. */
    data object Cancelled : PickResult

    /** No app answers the picker intent. */
    data object NoPicker : PickResult
}

/** What the screen must do for the model: only opening the picker needs the Activity. */
internal enum class SetupEffect { OpenFolderPicker }

/** What the model keeps of its own, apart from what the source says (the step, the form, answers given in this setup). */
internal data class SetupLocal(
    val step: SetupStep = SetupStep.Folders,
    /** The device could reach the internet when Ready last opened; read once there, never watched. */
    val isOnline: Boolean = true,
    val message: SetupMessage? = null,
    val requestedFocus: SetupControl? = null,
    val emulators: EmulatorCoverage? = null,
    /** The second screen question was answered in this setup: its dot stays. */
    val hasAnsweredSecondScreen: Boolean = false,
    /**
     * The question was skipped because the screen went away: its dot stays gone until the next
     * Continue, even if the screen comes back during that scan (it is not asked then).
     */
    val hasSkippedSecondScreen: Boolean = false,
    /** The box art step's form, made each time the step opens. */
    val boxArt: SignInForm? = null,
    /** "ScreenScraper account" needed a sign-in: the step shows [boxArt], not the choices. */
    val isBoxArtFormOpen: Boolean = false,
    /** "Yes" was chosen in a build with ScreenScraper: the step shows the source page (and the form opens from it). */
    val isBoxArtSourceOpen: Boolean = false,
    /** The name of a sign-in this setup saw succeed: the step stays signed in even before the stored status says so. */
    val signedInHere: String? = null,
    /** What the confirm step stored (or kept) in this setup; null until it is answered. */
    val confirmAnswer: ConfirmButtonSetting? = null,
    /** The question answered last before the scan (box art, or the second screen); Back from Scan and Ready goes there. Null with none asked. */
    val lastQuestion: SetupStep? = null,
    /** The second screen's kind when it was answered, so Back can show the question again. */
    val askedSecondScreen: ScreenKind? = null,
    /** The look tile picked in this setup; Back to the look step opens on it. */
    val pickedLook: SetupControl? = null,
    /** Set up later skipped the questions in this run: finishing stores it (`SetupFlags.setupQuestionsSkipped`), so they are asked when a folder is added. */
    val skippedQuestions: Boolean = false,
    /** Folders whose removal was asked and has not landed yet (it waits for a running scan). */
    val removing: Set<Long> = emptySet(),
)
