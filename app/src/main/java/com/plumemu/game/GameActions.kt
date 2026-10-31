package com.plumemu.game

import com.plumemu.telemetry.LaunchFrom
import androidx.compose.runtime.Immutable
import com.plumemu.battery.BatteryPace
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coredata.db.AppKind
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.FailureReason
import com.plumemu.launcher.LaunchOutcome
import java.io.File

/**
 * What Play and the game menu ask of the library, the launcher and Android: a seam so the menu's
 * tests run on a fake. A game is named by its list row id ([GameRowModel.id][com.plumemu.home.GameRowModel],
 * the database id as text). Suspending calls may throw a database error; the caller catches it and
 * shows a message. Production: [AppGameActions].
 */
internal interface GameActions {
    /** Starts the game in its emulator (off the main thread); never throws. [from]: where Play was pressed, for the statistics (`game_launched`). */
    suspend fun launch(
        gameKey: String,
        from: LaunchFrom,
    ): LaunchOutcome

    /** What the menu and Info show; null when the game is no longer in the library. */
    suspend fun facts(gameKey: String): GameFacts?

    /** The system's emulators for "Play with" and the no-emulator message; null when the game or its system is unknown. */
    suspend fun emulators(gameKey: String): EmulatorListing?

    suspend fun setFavorite(gameKey: String, isFavorite: Boolean)

    suspend fun hide(gameKey: String)

    /** Takes the game out of Recent; play time and statistics stay. Throws when it could not be written. */
    suspend fun removeFromRecent(gameKey: String)

    /** Stores [emulatorId] as this game's emulator; null goes back to automatic. */
    suspend fun chooseEmulator(gameKey: String, emulatorId: String?)

    /** Stores [emulatorId] as the emulator of every game of [gameKey]'s system (the first launch's question). */
    suspend fun chooseEmulatorForSystem(gameKey: String, emulatorId: String)

    /** Opens the store page of [packageName]; false when nothing on the device could open it. */
    fun openStorePage(packageName: String): Boolean

    /** Starts one rescan of every picked folder; false when one is already running. */
    fun rescanLibrary(): Boolean

    /**
     * Other versions: every copy of the game the row stands for, the one A plays now marked; null
     * when the game is gone. Opens the arcade pack for an arcade set.
     */
    suspend fun versions(gameKey: String): VersionListing?

    /**
     * A on [gameKey] plays the copy [copyKey] from now on ([copyKey] == [gameKey]: its own file).
     * False when [copyKey] is no copy of the game any more (a rescan since the list was read).
     */
    suspend fun chooseVersion(gameKey: String, copyKey: String): Boolean

    /** Info › Name from: the name pack's sources for the game's system; null when there is none. Opens the pack. */
    suspend fun nameSource(gameKey: String): String?

    /** Info › Arcade set: the set the game's file holds; null when not an arcade game or not in the pack. Opens the pack. */
    suspend fun arcadeSet(gameKey: String): ArcadeSetFacts?

    /** Fix name…: the system's name list ready to search; null when the game is gone. */
    suspend fun titleList(gameKey: String): TitleList?

    /**
     * Stores [choice] as the person's fix under every key of the game; shown at once. Then box art
     * is looked up by the new name; the answer says why it could not be, or null.
     */
    suspend fun setChosenTitle(gameKey: String, choice: TitleChoice): RenameBoxArtNotice?

    /** Stores [name] (trimmed, never blank) as the person's own name; the answer as [setChosenTitle]'s. */
    suspend fun setOwnName(gameKey: String, name: String): RenameBoxArtNotice?

    /** Not a game: the game leaves every list; Settings › Library › Hidden games brings it back. */
    suspend fun markNotAGame(gameKey: String)

    /**
     * The person's answer for the installed app a game row stands for ([AppKind.APP] is Not a game): the
     * row moves to Android games, Ports or back to Apps. True when the library followed; false when the answer is
     * stored but the library could not follow yet. Throws when it could not be stored.
     */
    suspend fun setAppKind(gameKey: String, kind: AppKind): Boolean

    /** Deletes the person's fix under every key and names the game again now (off the main thread). */
    suspend fun backToAutomatic(gameKey: String)

    /** Fix box art…: ScreenScraper's games for [query] on the game's system and regions. */
    suspend fun searchBoxArt(gameKey: String, query: String): BoxArtSearchOutcome

    /** [hit]'s box front, downloaded small into the cache; null when it has none or it failed. */
    suspend fun boxArtThumbnail(hit: BoxArtHit): File?

    /**
     * Fetches [hit] by its id, saves its pictures as a run does (fresh) and stores it as the
     * person's choice; [onSavingPictures] is told (on any thread) when the picture save begins.
     */
    suspend fun pickBoxArt(gameKey: String, hit: BoxArtHit, onSavingPictures: () -> Unit): BoxArtStoreOutcome

    /** Stores "No box art" as the person's choice (a pick's name gives way to the identifier's at once). */
    suspend fun storeNoBoxArt(gameKey: String): BoxArtStoreOutcome

    /**
     * The person says a game started with [core]: it is never
     * asked about again. Throws when it could not be kept (the caller says so; the next launch asks again).
     */
    suspend fun confirmCore(core: CoreToConfirm)

    /** The core never started: [added], the play session its return recorded, is taken back. False when nothing was (logged). */
    suspend fun takeBackPlay(added: AddedSession): Boolean

    /** No to "Did it start?": the person's game and system choices of [emulatorId] (the core that failed) are cleared; others stay. */
    suspend fun forgetFailedCore(gameKey: String, emulatorId: String)

    /** Benchmark and preview builds: nothing to launch, nothing stored. */
    object None : GameActions {
        override suspend fun launch(
            gameKey: String,
            from: LaunchFrom,
        ): LaunchOutcome =
            LaunchOutcome.Failed(UNKNOWN_GAME_ID, FailureReason.GameNotInLibrary)

        override suspend fun facts(gameKey: String): GameFacts? = null

        override suspend fun emulators(gameKey: String): EmulatorListing? = null

        override suspend fun setFavorite(gameKey: String, isFavorite: Boolean) = Unit

        override suspend fun hide(gameKey: String) = Unit

        override suspend fun removeFromRecent(gameKey: String) = Unit

        override suspend fun chooseEmulator(gameKey: String, emulatorId: String?) = Unit

        override suspend fun chooseEmulatorForSystem(gameKey: String, emulatorId: String) = Unit

        override fun openStorePage(packageName: String): Boolean = false

        override fun rescanLibrary(): Boolean = false

        override suspend fun versions(gameKey: String): VersionListing? = null

        override suspend fun chooseVersion(gameKey: String, copyKey: String): Boolean = false

        override suspend fun nameSource(gameKey: String): String? = null

        override suspend fun arcadeSet(gameKey: String): ArcadeSetFacts? = null

        override suspend fun titleList(gameKey: String): TitleList? = null

        override suspend fun setChosenTitle(gameKey: String, choice: TitleChoice): RenameBoxArtNotice? = null

        override suspend fun setOwnName(gameKey: String, name: String): RenameBoxArtNotice? = null

        override suspend fun markNotAGame(gameKey: String) = Unit

        override suspend fun backToAutomatic(gameKey: String) = Unit

        override suspend fun setAppKind(gameKey: String, kind: AppKind): Boolean = true

        override suspend fun searchBoxArt(gameKey: String, query: String): BoxArtSearchOutcome = BoxArtSearchOutcome.Failed(BoxArtProblem.NotAvailable)

        override suspend fun boxArtThumbnail(hit: BoxArtHit): File? = null

        override suspend fun confirmCore(core: CoreToConfirm) = Unit

        override suspend fun takeBackPlay(added: AddedSession): Boolean = false

        override suspend fun forgetFailedCore(gameKey: String, emulatorId: String) = Unit

        override suspend fun pickBoxArt(gameKey: String, hit: BoxArtHit, onSavingPictures: () -> Unit): BoxArtStoreOutcome =
            BoxArtStoreOutcome.Failed(BoxArtProblem.NotAvailable)

        override suspend fun storeNoBoxArt(gameKey: String): BoxArtStoreOutcome = BoxArtStoreOutcome.Failed(BoxArtProblem.NotAvailable)
    }
}

/** The game id of a row whose id is not a database id (fake content). */
internal const val UNKNOWN_GAME_ID = -1L

/**
 * One game as the menu and Info show it. Titles, file and folder names are for the screen only,
 * never for a log line. [lastPlayedAt] is epoch milliseconds, null when never played.
 */
@Immutable
internal data class GameFacts(
    val title: String,
    val systemName: String,
    /** Region codes as stored, comma-separated (`USA,EUR`); empty when the name has none. */
    val regions: String,
    val fileName: String,
    val sizeBytes: Long,
    /** The picked folder's name and the subfolder the file is in (`ROMs/snes`). */
    val folderName: String,
    val emulator: EmulatorToStart?,
    val lastPlayedAt: Long?,
    val totalPlaySeconds: Long,
    val isFavorite: Boolean,
    /** The game's usual battery pace: Info's Battery row; null: never measured, no row. */
    val batteryPace: BatteryPace? = null,
    /** Info › Name from and Back to automatic; null when it could not be read. */
    val name: NameFacts? = null,
    /**
     * Fix box art… is listed: a build with developer credentials and a ScreenScraper account
     * signed in that ScreenScraper has not refused.
     */
    val canFixBoxArt: Boolean = false,
    /**
     * A pinned shortcut: no file, so no Name… and no Fix box art… (it is named by
     * its app and gets no box art yet).
     */
    val isShortcut: Boolean = false,
    /**
     * An installed app listed as a game (Android games, Ports apps): started by its package,
     * so no Play with…, Other versions, Name… or Fix box art… (it has no file and no emulator).
     */
    val isAppGame: Boolean = false,
    /** An app game listed in Ports (not Android games): its menu offers Show as game instead of Show as port. */
    val isPortApp: Boolean = false,
    /** An arcade system's game: Info asks for its set ([GameActions.arcadeSet]) when it opens. */
    val isArcade: Boolean = false,
    /** Info › Arcade set (shown in place of File), once read; null before, and for a set the arcade pack does not list. */
    val arcadeSet: ArcadeSetFacts? = null,
    /**
     * Other versions (listed with two or more copies); null while not known: a console game's are
     * read with the facts, an arcade set's ([versionsPending]) when the menu has opened, since the
     * menu never waits for the arcade pack.
     */
    val versions: VersionListing? = null,
    val versionsPending: Boolean = false,
    /** Info's year, publisher, developer, genre, players and description; null without box art or when ScreenScraper gave none. */
    val scraped: ScrapedFacts? = null,
    /** The menu was opened from the Recent entry: it lists Remove from Recent. Set by the controller, never read from the library. */
    val canRemoveFromRecent: Boolean = false,
)

/**
 * The MAME set an arcade game's file holds, for Info: [shortName] the file's short name, and
 * [details] what tells it apart from other sets of the game, the bracket texts of its MAME
 * description in the arcade pack ("USA 930201" of "Cadillacs and Dinosaurs (USA 930201)",
 * identify's `ArcadeTitles.details`; empty when it has none). Info's title already names the game.
 * Not stored: looked up when Info opens.
 */
@Immutable
internal data class ArcadeSetFacts(
    val shortName: String,
    val details: List<String>,
)

/**
 * The emulator a launch would start now; [isInstalled] false for a person's choice that was
 * uninstalled. [isNotChosenYet]: nothing is chosen for the system, Play asks first ([name] is empty).
 */
@Immutable
internal data class EmulatorToStart(
    val name: String,
    val isInstalled: Boolean,
    val isNotChosenYet: Boolean = false,
)

/** One emulator of a system, with the package its store page opens. */
@Immutable
internal data class EmulatorItem(
    val id: String,
    val name: String,
    val packageName: String,
    /** listed with its one line, never picked automatically. */
    val isLegalRisk: Boolean,
)

/**
 * A system's emulators for "Play with": [automatic] is what Automatic (the game's choice forgotten)
 * starts, in the launch's order: the person's choice for the system, else the first installed
 * default (null when none); [isAutomaticInstalled] false when that system choice is not installed.
 * [installed] in the data's order; [notInstalled] with the legal-risk ones last.
 * [currentEmulatorId] is the person's choice for this game, null for automatic.
 */
@Immutable
internal data class EmulatorListing(
    val systemName: String,
    val automatic: EmulatorItem?,
    val installed: List<EmulatorItem>,
    val notInstalled: List<EmulatorItem>,
    val currentEmulatorId: String?,
    val isAutomaticInstalled: Boolean = true,
    /** False while nothing is chosen for the system: Automatic then asks on Play ([automatic] is only the recommendation). */
    val isSystemChosen: Boolean = true,
)
