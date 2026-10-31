package com.plumemu.platforms

/**
 * The class of a system's box art in the data (Daijisho's, or its measured ratio's), with the
 * width over height a system of that class gets when it was not measured ([GameSystem.coverRatio]).
 */
enum class CoverShape(val defaultRatio: Double) {
    Landscape(1.4),
    Portrait(0.71),
    ;

    companion object {
        /** A measured ratio from this up is [Landscape] (`tools/platforms/tables.py` `LANDSCAPE_MIN_RATIO`). */
        const val LANDSCAPE_MIN_RATIO = 1.2

        /** The ratios the data may hold: wider or taller than any box is a typo. */
        val RATIO_RANGE: ClosedFloatingPointRange<Double> = 0.4..2.5
    }
}

/** Whether the system's emulators were tried on real hardware. */
enum class Tier { Tested, Untested }

/** What sort of system this is; [code] is the word in `platforms.json`. */
enum class SystemKind(val code: String) {
    Console("console"), Handheld("handheld"), Arcade("arcade"), Computer("computer"), AddOn("add-on"), Os("os");

    companion object {
        fun fromCode(code: String): SystemKind? = entries.firstOrNull { it.code == code }
    }
}

/** What the emulator wants to be given for the game. */
enum class PassMode {
    Path,
    Uri,

    /**
     * PS Vita only: the title ID (PCSE00000) written in the game's `.psvita` text file, ES-DE's
     * convention; the game itself is installed inside the emulator. See [VitaTitleId].
     */
    VitaTitleId,

    /**
     * A Steam app id (274190) written in the game's `.steam` text file, ES-DE's convention for
     * GameNative's "Export for frontend"; the game itself is in the Steam app. See [SteamAppId].
     * Goes in an extra of [GamePass.idType], or into the data through [GamePass.template].
     */
    SteamAppId,
}

/** Where in the Intent the game goes: the data field, a named extra, or last in a string-array extra. */
enum class GameTarget { Data, Extra, ExtraArray }

/** The type of the extra that carries a [PassMode.SteamAppId] id: `int` (GameNative's `app_id`) or `string` in the data. */
enum class IdType { Number, Text }

enum class LaunchFlag { ClearTask, ClearTop, NoHistory }

/**
 * How the game is handed to an emulator: [mode] says what (path, URI, title ID, Steam app id),
 * [target] where it goes. [prefix] is only for [GameTarget.ExtraArray]: the fixed strings before the
 * game (Vita3K: `-r`). [idType] and [template] are only for [PassMode.SteamAppId]: the extra's type
 * with [GameTarget.Extra], the data with [GameTarget.Data] (`droiddeck://game/{id}`, [ID_PLACEHOLDER]).
 */
data class GamePass(
    val mode: PassMode,
    val target: GameTarget,
    val extraKey: String?,
    val prefix: List<String> = emptyList(),
    val idType: IdType? = null,
    val template: String? = null,
    /**
     * Mode [PassMode.Uri] only: files with one of these extensions go by path when the path is known (DuckStation's
     * `.m3u` loader cannot open a content URI). Without a path the URI is passed as for any file.
     */
    val pathExtensions: Set<String> = emptySet(),
) {
    companion object {
        /** Where a [template] takes the id. */
        const val ID_PLACEHOLDER = "{id}"
    }
}

/** A value of an Intent extra. The platform data's `extras` use the first three; [TextArray] only carries the game. */
sealed interface ExtraValue {
    data class Text(val value: String) : ExtraValue
    data class Flag(val value: Boolean) : ExtraValue
    data class Number(val value: Int) : ExtraValue

    /** A `String[]` extra; made for [GameTarget.ExtraArray]. */
    data class TextArray(val values: List<String>) : ExtraValue
}

/** One package of an emulator; the 32-bit and 64-bit RetroArch builds are two of these. */
data class PackageVariant(val packageName: String, val activity: String?, val bits: Int?)

data class Emulator(
    val id: String,
    val name: String,
    /** The preferred package; [variants] are the others, in priority order. */
    val primary: PackageVariant,
    val variants: List<PackageVariant>,
    /** RetroArch core file name; fills `{core}` in [extras]. */
    val core: String?,
    val action: String?,
    val category: String?,
    val mimeType: String?,
    val flags: Set<LaunchFlag>,
    val game: GamePass,
    /** Extras with `{package}` and `{core}` still to fill in. */
    val extras: Map<String, ExtraValue>,
    /** Listed, but never picked by default. */
    val legalRisk: Boolean,
    /**
     * The file extensions (lowercase, no dot) this emulator can open, among its systems'; null for
     * every file of its systems. In Steam a `.steam` app id goes to the Steam apps, a `.desktop`
     * shortcut to WinNative: neither is offered, recommended or used for the other's files.
     */
    val accepts: Set<String>? = null,
) {
    val allPackages: List<PackageVariant> get() = listOf(primary) + variants

    /** Whether this emulator can open [fileName] (by its extension; compound ones such as `p8.png` too). */
    fun acceptsFile(fileName: String): Boolean {
        val accepted = accepts ?: return true
        val name = fileName.lowercase(java.util.Locale.ROOT)
        return accepted.any { name.endsWith(".$it") }
    }
}

data class GameSystem(
    val id: String,
    val name: String,
    val shortName: String,
    /** The name people use, without the maker where possible; what the main screen shows. */
    val displayName: String,
    /** Place in the main screen's system list; unique, the tested systems first. */
    val order: Int,
    /** Lowercase folder names people use for this system; the first is the system id. */
    val folders: List<String>,
    /** Lowercase, without the dot. */
    val extensions: List<String>,
    val cover: CoverShape,
    val tier: Tier,
    /** Emulator ids in priority order. */
    val emulatorIds: List<String>,
    /** Who made the system, as people know it ("Nintendo"); null for engines, stores and emulators. */
    val maker: String? = null,
    /** The year it first went on sale anywhere; null when [maker] is. */
    val year: Int? = null,
    /** What sort of system it is; null when [maker] is. */
    val kind: SystemKind? = null,
    /** The year its maker stopped making it; null while still made ([inProduction]), when unknown, or when [year] is null. */
    val endYear: Int? = null,
    /** True while its maker still makes it (then [endYear] is null). Neither set: unknown, show the launch year alone. */
    val inProduction: Boolean = false,
    /**
     * Box front width over height: measured for the main systems (the file's `coverRatio`), else
     * [cover]'s default. The app sizes the grid from it (docs/design/rules.md › Grid).
     */
    val coverRatio: Double = cover.defaultRatio,
    /**
     * The short name players say ("SNES", "PS1", "MD"), at most 5 characters: the right-hand column of
     * a game row in Recent, Favorites and Search. Empty only in a hand-made file; the shipped one has it for every system.
     */
    val abbr: String = "",
    /**
     * Three characters at most ("SNS", "PS1", "MD"): the home screen's left edge shows it in place of the game count
     * (Settings › Appearance › Left edge › System codes). A hand-made file without one gets the short name's first three letters.
     */
    val code: String = "",
    /**
     * The colour the system is known by (hardware, logo or box), 0xRRGGBB; null where it has none.
     * Settings › Appearance › Accent color › By system uses it as the accent while the system is in view.
     */
    val color: Int? = null,
    /**
     * True for a system whose games are installed apps (`source: "apps"` in the file, not files:
     * it has no folders, extensions or emulators, so a scan or an emulator choice must skip it.
     */
    val fromInstalledApps: Boolean = false,
)

class PlatformCatalog internal constructor(
    val formatVersion: Int,
    val systems: List<GameSystem>,
    val emulators: List<Emulator>,
    /** Installed app package name to the id of the system it belongs to; a system id that exists (checked on load). */
    val knownApps: Map<String, String> = emptyMap(),
) {
    private val emulatorsById = emulators.associateBy { it.id }
    private val systemsById = systems.associateBy { it.id }

    fun system(id: String): GameSystem? = systemsById[id]
    fun emulator(id: String): Emulator? = emulatorsById[id]

    /** The emulators of [system] in priority order. */
    fun emulatorsOf(system: GameSystem): List<Emulator> = system.emulatorIds.mapNotNull { emulatorsById[it] }

    /**
     * The priority list without legal-risk emulators: what a default may be picked from.
     * Empty for an unknown system.
     */
    fun defaultCandidates(systemId: String): List<Emulator> =
        systemsById[systemId]?.let { system -> emulatorsOf(system).filterNot { it.legalRisk } }.orEmpty()

    /** The emulators of [system] that can open [fileName] ([Emulator.acceptsFile]), in priority order. */
    fun emulatorsFor(system: GameSystem, fileName: String): List<Emulator> = emulatorsOf(system).filter { it.acceptsFile(fileName) }

    /** [defaultCandidates] that can open [fileName]: what a game's recommendation may be picked from. */
    fun defaultCandidatesFor(systemId: String, fileName: String): List<Emulator> =
        defaultCandidates(systemId).filter { it.acceptsFile(fileName) }
}
