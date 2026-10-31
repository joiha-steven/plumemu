package com.plumemu.game

import com.plumemu.telemetry.LaunchFrom
import com.plumemu.coredata.db.AddedSession
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.launcher.choice.ChoiceSource
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** Records what the menu asked for; answers are set per test. */
internal class FakeGameActions(
    var facts: (String) -> GameFacts? = { key -> sampleFacts(title = "Game $key") },
    var listing: (String) -> EmulatorListing? = { sampleListing() },
    var outcome: (String) -> LaunchOutcome = { key -> started(key) },
    var titles: (String) -> TitleList? = { TitleList.Ready(TitleFinder(sampleTitles)) },
) : GameActions {
    val chosenTitles = mutableListOf<Pair<String, TitleChoice>>()
    val ownNames = mutableListOf<Pair<String, String>>()
    val notGames = mutableListOf<String>()
    val backToAutomatic = mutableListOf<String>()
    val launched = mutableListOf<String>()
    val favorites = mutableListOf<Pair<String, Boolean>>()
    val hidden = mutableListOf<String>()
    val removedFromRecent = mutableListOf<String>()
    val choices = mutableListOf<Pair<String, String?>>()
    val systemChoices = mutableListOf<Pair<String, String>>()
    val storePages = mutableListOf<String>()
    var rescans = 0

    /** When set, a launch waits for it (a launch still running). */
    var launchGate: CompletableDeferred<Unit>? = null
    var storeOpens = true
    var writeError: Exception? = null
    var readError: Exception? = null
    var onHide: (String) -> Unit = {}
    var onRemoveFromRecent: (String) -> Unit = {}
    var onFavorite: (String, Boolean) -> Unit = { _, _ -> }

    /** When set, reads (facts, emulators) wait for it (a slow database). */
    var readGate: CompletableDeferred<Unit>? = null
    var scanBusy = false

    /** When set, writes (favorite, hide, emulator choice) wait for it. */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Where each launch was pressed, in order. */
    val launchesFrom = mutableListOf<LaunchFrom>()

    /** Runs in [launch] before [launchGate]; a test claims the hand-off here (PlayTimeTracker.handOff does in the app). */
    var beforeGate: suspend () -> Unit = {}

    override suspend fun launch(
        gameKey: String,
        from: LaunchFrom,
    ): LaunchOutcome {
        launchesFrom += from
        launched += gameKey
        beforeGate()
        launchGate?.await()
        return outcome(gameKey)
    }

    val confirmedCores = mutableListOf<CoreToConfirm>()
    val takenBack = mutableListOf<AddedSession>()

    override suspend fun confirmCore(core: CoreToConfirm) {
        writeError?.let { throw it }
        confirmedCores += core
    }

    /** What the app does besides taking the play back (it drops the held usage event). */
    var onTakeBack: (AddedSession) -> Unit = {}

    override suspend fun takeBackPlay(added: AddedSession): Boolean {
        takenBack += added
        onTakeBack(added)
        return true
    }

    val forgottenCores = mutableListOf<Pair<String, String>>()

    override suspend fun forgetFailedCore(gameKey: String, emulatorId: String) {
        writeError?.let { throw it }
        forgottenCores += gameKey to emulatorId
    }

    override suspend fun facts(gameKey: String): GameFacts? {
        readGate?.await()
        readError?.let { throw it }
        return facts.invoke(gameKey)
    }

    override suspend fun emulators(gameKey: String): EmulatorListing? {
        readGate?.await()
        readError?.let { throw it }
        return listing.invoke(gameKey)
    }

    override suspend fun setFavorite(gameKey: String, isFavorite: Boolean) {
        writeGate?.await()
        writeError?.let { throw it }
        favorites += gameKey to isFavorite
        onFavorite(gameKey, isFavorite)
    }

    override suspend fun hide(gameKey: String) {
        writeError?.let { throw it }
        hidden += gameKey
        onHide(gameKey)
    }

    override suspend fun removeFromRecent(gameKey: String) {
        writeError?.let { throw it }
        removedFromRecent += gameKey
        onRemoveFromRecent(gameKey)
    }

    override suspend fun chooseEmulator(gameKey: String, emulatorId: String?) {
        writeGate?.await()
        writeError?.let { throw it }
        choices += gameKey to emulatorId
    }

    override suspend fun chooseEmulatorForSystem(gameKey: String, emulatorId: String) {
        writeGate?.await()
        writeError?.let { throw it }
        systemChoices += gameKey to emulatorId
    }

    override fun openStorePage(packageName: String): Boolean {
        storePages += packageName
        return storeOpens
    }

    override fun rescanLibrary(): Boolean {
        if (scanBusy) return false
        rescans++
        return true
    }

    /** What Other versions' read answers (an arcade set's, read after the menu opens); [versionReads] counts the reads. */
    var versionsAnswer: (String) -> VersionListing? = { null }
    var versionReads = 0

    /** When set, Other versions' read waits for it (the arcade pack being opened). */
    var versionsGate: CompletableDeferred<Unit>? = null

    /** Other versions' choices, in order (row key to copy key); [chooseVersionAnswer] false: no copy any more. */
    val chosenVersions = mutableListOf<Pair<String, String>>()
    var chooseVersionAnswer = true

    override suspend fun versions(gameKey: String): VersionListing? {
        versionReads++
        versionsGate?.await()
        return versionsAnswer(gameKey)
    }

    override suspend fun chooseVersion(gameKey: String, copyKey: String): Boolean {
        writeGate?.await()
        writeError?.let { throw it }
        if (chooseVersionAnswer) chosenVersions += gameKey to copyKey
        return chooseVersionAnswer
    }

    var sourceReads = 0

    /** When set, Info's read of the name's source waits for it (a name pack being opened). */
    var nameSourceGate: CompletableDeferred<Unit>? = null

    override suspend fun nameSource(gameKey: String): String? {
        sourceReads++
        nameSourceGate?.await()
        return "Test DAT"
    }

    var arcadeSetReads = 0

    /** What Info's arcade set read answers; null: a set the pack does not list. */
    var arcadeSetAnswer: ArcadeSetFacts? = ArcadeSetFacts("dinou", listOf("USA 930201"))

    /** When set, Info's read of the arcade set waits for it (the arcade pack being opened). */
    var arcadeSetGate: CompletableDeferred<Unit>? = null

    override suspend fun arcadeSet(gameKey: String): ArcadeSetFacts? {
        arcadeSetReads++
        arcadeSetGate?.await()
        return arcadeSetAnswer
    }

    override suspend fun titleList(gameKey: String): TitleList? {
        readGate?.await()
        readError?.let { throw it }
        return titles(gameKey)
    }

    /** What the next rename answers about box art ([RenameBoxArtNotice]); null: looked up, nothing to say. */
    var renameNotice: RenameBoxArtNotice? = null

    override suspend fun setChosenTitle(gameKey: String, choice: TitleChoice): RenameBoxArtNotice? {
        writeGate?.await()
        writeError?.let { throw it }
        chosenTitles += gameKey to choice
        return renameNotice
    }

    override suspend fun setOwnName(gameKey: String, name: String): RenameBoxArtNotice? {
        writeError?.let { throw it }
        ownNames += gameKey to name
        return renameNotice
    }

    override suspend fun markNotAGame(gameKey: String) {
        writeError?.let { throw it }
        notGames += gameKey
        onHide(gameKey)
    }

    /** The answers asked of an app game's menu, in order. */
    val appKinds = mutableListOf<Pair<String, com.plumemu.coredata.db.AppKind>>()

    /** What setAppKind answers: false when the library could not follow yet (a skipped sync). */
    var kindFollowed = true

    override suspend fun setAppKind(gameKey: String, kind: com.plumemu.coredata.db.AppKind): Boolean {
        writeError?.let { throw it }
        appKinds += gameKey to kind
        return kindFollowed
    }

    override suspend fun backToAutomatic(gameKey: String) {
        writeGate?.await()
        writeError?.let { throw it }
        backToAutomatic += gameKey
    }

    /** Fix box art…: what each search answers, in order of the calls ([boxArtSearches] records the queries). */
    var boxArtSearch: suspend (String) -> BoxArtSearchOutcome = { BoxArtSearchOutcome.Found(sampleHits) }
    val boxArtSearches = mutableListOf<String>()

    /** Thumbnails: what each answers; [thumbnailsAsked] records the ids, [thumbnailsInFlight] the most at once. */
    var boxArtThumbnailOf: suspend (BoxArtHit) -> File? = { null }
    val thumbnailsAsked = mutableListOf<String>()
    var thumbnailsInFlight = 0
        private set
    var mostThumbnailsInFlight = 0
        private set

    /** A pick: told when the pictures start, then answers; [boxArtPicks] records them ("-" for No box art). */
    var boxArtPick: suspend (BoxArtHit?, () -> Unit) -> BoxArtStoreOutcome = { _, _ -> BoxArtStoreOutcome.Stored }
    val boxArtPicks = mutableListOf<Pair<String, String>>()

    override suspend fun searchBoxArt(gameKey: String, query: String): BoxArtSearchOutcome {
        boxArtSearches += query
        return boxArtSearch(query)
    }

    override suspend fun boxArtThumbnail(hit: BoxArtHit): File? {
        thumbnailsAsked += hit.id
        thumbnailsInFlight++
        mostThumbnailsInFlight = maxOf(mostThumbnailsInFlight, thumbnailsInFlight)
        try {
            return boxArtThumbnailOf(hit)
        } finally {
            thumbnailsInFlight--
        }
    }

    override suspend fun pickBoxArt(gameKey: String, hit: BoxArtHit, onSavingPictures: () -> Unit): BoxArtStoreOutcome {
        boxArtPicks += gameKey to hit.id
        return boxArtPick(hit, onSavingPictures)
    }

    override suspend fun storeNoBoxArt(gameKey: String): BoxArtStoreOutcome {
        boxArtPicks += gameKey to "-"
        return boxArtPick(null) {}
    }

    companion object {
        fun started(key: String): LaunchOutcome =
            LaunchOutcome.Started(key.toLongOrNull() ?: UNKNOWN_GAME_ID, "duckstation", "com.github.stenzek.duckstation", ChoiceSource.PersonForSystem, null)
    }
}

internal val DuckStation = EmulatorItem("duckstation", "DuckStation", "com.github.stenzek.duckstation", isLegalRisk = false)
internal val Beetle = EmulatorItem("retroarch_mednafen_psx", "RetroArch (Beetle PSX)", "com.retroarch.aarch64", isLegalRisk = false)
internal val Epsxe = EmulatorItem("epsxe", "ePSXe", "com.epsxe.ePSXe", isLegalRisk = false)
internal val RiskyEmu = EmulatorItem("risky", "Risky Emu", "org.example.risky", isLegalRisk = true)

/** Made-up titles for Fix name…. */
internal val sampleTitles = listOf(
    TitleChoice("Zork Quest", "USA, Europe", 1991, "Test DAT"),
    TitleChoice("Zork Quest II", "USA", 1993, "Test DAT"),
    TitleChoice("Zork Quest II: Moonlit Harbor", "Japan", null, "Test DAT"),
    TitleChoice("Galfen Island", "", 1995, "Test DAT"),
    TitleChoice("Dravon Knight", "", null, null),
)

/** Made-up ScreenScraper games for Fix box art… (never real data in tests). */
internal val sampleHits = listOf(
    BoxArtHit("9001", "Zork Quest", 1991, "Galfen Soft", "Super Nintendo", com.plumemu.scraper.MediaRef(com.plumemu.scraper.MediaKind.BoxFront, "us", "https://example.invalid/9001.png")),
    BoxArtHit("9002", "Zork Quest II", 1993, null, "Super Nintendo", com.plumemu.scraper.MediaRef(com.plumemu.scraper.MediaKind.BoxFront, "eu", "https://example.invalid/9002.png")),
    BoxArtHit("9003", null, null, null, null, null),
)

internal fun sampleFacts(
    title: String = "Chrono Trigger",
    isFavorite: Boolean = false,
    lastPlayedAt: Long? = null,
    name: NameFacts? = null,
    canFixBoxArt: Boolean = false,
    isShortcut: Boolean = false,
    isArcade: Boolean = false,
    versions: VersionListing? = null,
    versionsPending: Boolean = false,
    canRemoveFromRecent: Boolean = false,
) = GameFacts(
    title = title,
    systemName = "PlayStation",
    regions = "USA,EUR",
    fileName = "Chrono Cross (USA) (Disc 1).chd",
    sizeBytes = 512L * 1024 * 1024,
    folderName = "ROMs/psx",
    emulator = EmulatorToStart("DuckStation", isInstalled = true),
    lastPlayedAt = lastPlayedAt,
    totalPlaySeconds = 4L * 3600 + 12 * 60,
    isFavorite = isFavorite,
    name = name,
    canFixBoxArt = canFixBoxArt,
    isShortcut = isShortcut,
    isArcade = isArcade,
    versions = versions,
    versionsPending = versionsPending,
    canRemoveFromRecent = canRemoveFromRecent,
)

/** Two regional copies of a console game, the first one current ("USA", "Europe"; made-up files). */
internal fun sampleVersions(currentIndex: Int = 0): VersionListing =
    VersionListing(
        listOf(
            VersionItem("11", UiText.Plain("USA"), isCurrent = currentIndex == 0),
            VersionItem("12", UiText.Plain("Europe"), isCurrent = currentIndex == 1),
        ),
    )

/** An arcade game's two sets, as the arcade pack's details label them. */
internal fun sampleArcadeVersions(currentIndex: Int = 0): VersionListing =
    VersionListing(
        listOf(
            VersionItem("21", UiText.Plain("World 930201"), isCurrent = currentIndex == 0),
            VersionItem("22", UiText.Plain("USA 930201"), isCurrent = currentIndex == 1),
        ),
    )

internal fun sampleListing(
    current: String? = null,
    installed: List<EmulatorItem> = listOf(DuckStation, Beetle),
    notInstalled: List<EmulatorItem> = listOf(Epsxe, RiskyEmu),
    automatic: EmulatorItem? = installed.firstOrNull(),
) = EmulatorListing("PlayStation", automatic, installed, notInstalled, current)
