package com.plumemu.coredata.settings

/**
 * Every stored setting the alpha uses, immutable. Defaults are the product's defaults: Black, light text, Red accent, Plum Sans,
 * Medium text, scan on startup, usage statistics on.
 *
 * Plain values only: this module does not know the theme or the input types. The app maps
 * [AppearanceSettings] to the theme (core-ui) and [ControlsSettings] to core-input.
 */
data class PlumSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val controls: ControlsSettings = ControlsSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val homeScreen: HomeScreenSettings = HomeScreenSettings(),
    val secondScreen: SecondScreenSettings = SecondScreenSettings(),
    val advanced: AdvancedSettings = AdvancedSettings(),
    val updates: UpdateSettings = UpdateSettings(),
    val flags: SetupFlags = SetupFlags(),
) {
    companion object {
        val Defaults = PlumSettings()
    }
}

/**
 * A colour setting: one of the presets (by the preset's name, as core-ui names it) or the person's
 * own colour, used exactly as picked.
 */
sealed interface ColorSetting {
    data class Preset(val name: String) : ColorSetting

    /** `0xRRGGBB`; anything above 24 bits is dropped. */
    data class Custom(val rgb: Int) : ColorSetting

    /**
     * Accent color only: the accent is the colour of the system in view.
     * [fallback] is the accent the person had before (a [Preset] or [Custom], never another
     * BySystem), used where no system is in view and for systems without a colour.
     */
    data class BySystem(val fallback: ColorSetting) : ColorSetting
}

/**
 * The text colour: [Light] and [Dark] are the two presets, [Custom]
 * is any colour. Stored as `Light`, `Dark` or `#RRGGBB`, so a file written when the setting was
 * only Light or Dark reads unchanged. Whether a colour is readable on the background is not
 * decided here (core-ui `Readability`).
 */
sealed interface TextColorSetting {
    /** The text as `appearance.text_color` and the backup spell it: the preset's name or `#RRGGBB`. */
    val stored: String

    data object Light : TextColorSetting {
        override val stored: String get() = "Light"
    }

    data object Dark : TextColorSetting {
        override val stored: String get() = "Dark"
    }

    /** `0xRRGGBB`; anything above 24 bits is dropped. */
    data class Custom(val rgb: Int) : TextColorSetting {
        override val stored: String get() = SettingsCodec.encodeColor(ColorSetting.Custom(rgb))
    }

    companion object {
        /** The two presets in the order the Text color row steps through them. */
        val presets: List<TextColorSetting> = listOf(Light, Dark)

        /** Null for anything this version does not know (a damaged or newer value). */
        fun fromStored(value: String): TextColorSetting? =
            presets.firstOrNull { it.stored == value }
                ?: (SettingsCodec.decodeColor(value) as? ColorSetting.Custom)?.let { Custom(it.rgb) }
    }
}

enum class FontSetting { Sans, Serif, Mono }

/**
 * The six text sizes, smallest first. The names are what `appearance.text_size` stores; Small,
 * Medium and Large are the three the file always held, so they keep their meaning.
 */
enum class TextSizeSetting {
    Small,
    Medium,
    Large,
    Larger,
    Huge,
    Largest,
    ;

    companion object {
        /** What "not picked" (null) means, on every screen. Same as core-ui `TextSize.Default`. */
        val Default: TextSizeSetting = Large
    }
}

/**
 * Settings › Appearance › Text weight, lightest first. The names are what `appearance.text_weight` stores
 * (core-ui `TextWeight` has the same names).
 */
enum class TextWeightSetting {
    Thin,
    Medium,
    Bold,
    ;

    companion object {
        /** What a person who has not picked gets; [AppearanceSettings.textWeight] stays null until a pick. */
        val Default: TextWeightSetting = Medium
    }
}

/**
 * Settings › Appearance › Left edge: what the home screen's 48 dp peek shows while the focus is in a games list
 * The names are what `appearance.left_edge` stores.
 */
enum class LeftEdgeSetting {
    /** Each system's game count: the look before the letter rail. */
    Counts,

    /** A three-character code per system ("SNS", "PS1"). */
    Codes,

    /** The A to Z letter rail of the focused list. */
    Letters,
    ;

    companion object {
        /** What a person who has not picked gets; [AppearanceSettings.leftEdge] stays null until a pick. */
        val Default: LeftEdgeSetting = Counts
    }
}

/** Settings › Appearance › Grid size, smallest covers first. The names are what `appearance.grid_size` stores. */
enum class GridSizeSetting { Small, Medium, Large }

/**
 * Whether A and B are swapped. [Right] is the default: the button printed "A"
 * confirms and the one printed "B" goes back. [Bottom] is the Advanced option "Confirm with B": the
 * button printed "B" confirms. The names are what older installs stored in `controls.confirm_button`
 * and stay so those values keep their meaning.
 */
enum class ConfirmButtonSetting { Right, Bottom }

/**
 * Settings › Controls › Keyboard: which on-screen keyboard every text field types with.
 * Plumemu's own by default; Android's (the system keyboard) for people who prefer it.
 */
enum class KeyboardSetting { Plumemu, Android }

/**
 * Settings › Controls › Switch system with: which pair of shoulder buttons
 * switches system on the main screen. [Triggers] (L2 / R2, keys or analog triggers) is the default and
 * what the app always did; [Bumpers] (L1 / R1) trades the two pairs, so L2 / R2 then jump a letter in a
 * list and change Size in a grid. The names are what `controls.switch_system_with` stores.
 */
enum class SwitchSystemWithSetting { Triggers, Bumpers }

/** Settings › Appearance. */
data class AppearanceSettings(
    val background: ColorSetting = ColorSetting.Preset(DEFAULT_BACKGROUND),
    val textColor: TextColorSetting = TextColorSetting.Light,
    val accent: ColorSetting = ColorSetting.Preset(DEFAULT_ACCENT),
    val font: FontSetting = FontSetting.Sans,
    /** Null until the person picks a size: the app then uses [TextSizeSetting.Default], Large. */
    val textSize: TextSizeSetting? = null,
    /** The main screen's grid, one size for every system (Medium by default). */
    val gridSize: GridSizeSetting = GridSizeSetting.Medium,
    /**
     * Systems the main screen shows as a grid (SELECT); every other system is a list. Ids
     * of systems that have no games now stay in it and are ignored, so a folder that comes back keeps
     * its grid. Recent, Favorites and Apps switch too under their entries' ids
     * "recent", "favorites" and "apps" (no platform has those ids); a file written before that has
     * none of them and reads as lists.
     */
    val gridSystems: Set<String> = emptySet(),
    /**
     * The person's own colours, kept when they pick a ready-made set while their colours matched
     * none (Settings › Appearance › Colors), so "Custom" brings them back exactly. Null
     * until then; Reset all settings clears it.
     */
    val ownColors: OwnColors? = null,
    /**
     * Whose A/B/X/Y glyph colours are in use: the name of the
     * ready-made set the person last picked, or [BUTTONS_NONE] for a set without them. Changing
     * Background, Text color or Accent color by hand never changes it. Null means not decided yet
     * (a file from before this setting): the app settles it once, from the colours then current.
     */
    val buttonSet: String? = null,
    /** Null (never picked, or a file from before the setting) draws Thin for now; the default moves to Medium in a later step. */
    val textWeight: TextWeightSetting? = null,
    /** Null (never picked, or a file from before the setting) is [LeftEdgeSetting.Default], Counts. */
    val leftEdge: LeftEdgeSetting? = null,
    /**
     * Settings › Advanced › Language (docs/conventions/translation.md): a BCP 47 tag of one of the app's
     * languages ("en", "vi", "zh-CN"), or null to follow the system's language (the default).
     */
    val language: String? = null,
) {
    companion object {
        const val BUTTONS_NONE = "None"

        const val DEFAULT_BACKGROUND = "Black"
        const val DEFAULT_ACCENT = "Red"
    }
}

/** Background, text and accent as the person had them before picking a ready-made set. */
data class OwnColors(
    val background: ColorSetting,
    val textColor: TextColorSetting,
    val accent: ColorSetting,
    /** [AppearanceSettings.buttonSet] as it was when these were kept, so Custom brings the same buttons back. Null: none kept. */
    val buttonSet: String? = null,
)

/** Settings › Controls (the confirm button sits in Settings › Advanced). */
data class ControlsSettings(
    val confirmButton: ConfirmButtonSetting = ConfirmButtonSetting.Right,
    val holdToScrollFaster: Boolean = true,
    val showButtonHints: Boolean = true,
    val keyboard: KeyboardSetting = KeyboardSetting.Plumemu,
    val switchSystemWith: SwitchSystemWithSetting = SwitchSystemWithSetting.Triggers,
    /** Push right on a game in a list to play it, as A does. Off by default; asked once in first-run setup. */
    val pushRightToPlay: Boolean = false,
)

/** Settings › Library. Scan on startup is on by default. */
data class LibrarySettings(
    val scanOnStartup: Boolean = true,
    val sortGames: SortGamesSetting = SortGamesSetting.Name,
    val boxArtRegion: BoxArtRegionSetting = BoxArtRegionSetting.Usa,
    /**
     * The person paused box art in Settings › Library › ScreenScraper: no run starts
     * or goes on until they resume. Off by default.
     */
    val boxArtPaused: Boolean = false,
    /** Settings › Library › System order: how the main screen's system column is arranged. */
    val systemOrder: SystemOrderSetting = SystemOrderSetting.Name,
    /**
     * The person's own arrangement, system ids top to bottom, used while [systemOrder] is
     * [SystemOrderSetting.Manual]. Kept when the order is switched away and back. Ids of systems
     * that have no games now stay in it, so a folder that comes back keeps its systems' places.
     */
    val manualSystemOrder: List<String> = emptyList(),
    /**
     * Systems left out of the main screen's system column (Settings › Library › Systems). Their
     * games still show in Recent, Favorites and Search. Recent, Favorites and Apps are never in it.
     */
    val hiddenSystems: Set<String> = emptySet(),
    /** How many games Recent keeps: 20 unless the person picks 10 or 50. */
    val recentSize: RecentSizeSetting = RecentSizeSetting.Twenty,
)

/**
 * How many of the last games played Recent shows. Stored by name (`Ten`, `Twenty`,
 * `Fifty`); an unknown value reads as the default.
 */
enum class RecentSizeSetting(val games: Int) {
    Ten(10),
    Twenty(20),
    Fifty(50),
}

/**
 * How the main screen's systems are arranged (Settings › Library › System order). [Name] is the
 * default: A to Z by the name shown. [Maker] groups by manufacturer, oldest first in each.
 * [Manual] follows [LibrarySettings.manualSystemOrder]. Stored in lower case (`name`, `maker`, `manual`).
 */
enum class SystemOrderSetting { Name, Maker, Manual }

/**
 * Settings › Library › Box art region: whose box to show when the game file's
 * own region has none. [code] is ScreenScraper's region code; the file stores [code] too.
 */
enum class BoxArtRegionSetting(val code: String) {
    Usa("us"),
    Europe("eu"),
    Japan("jp"),
    World("wor"),
}

/**
 * Settings › Library › Sort games, for every system's list and Favorites (Recent is always newest
 * first). By year is oldest first, from the years ScreenScraper gave; games without a year come after, by name.
 * Values are stored by name and picked by position: Year stays last.
 */
enum class SortGamesSetting { Name, LastPlayed, Year }

/** Settings › Home screen. */
data class HomeScreenSettings(
    val showApps: Boolean = true,
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    /**
     * The band over a system's games list (badge and facts line); the second screen's card is not
     * affected. Null until the person flips the switch or the app decides for a device with a second
     * screen (which shows the same facts: it writes false once); read it through
     * [isSystemInfoShown], which is on while null.
     */
    val showSystemInfo: Boolean? = null,
) {
    /** Whether the band shows: the stored value, on when none was ever stored. */
    val isSystemInfoShown: Boolean get() = showSystemInfo ?: true
}

/**
 * Settings › Home screen › Second screen.
 * [enabled] is null until the person answers or flips the switch: the app then decides from the
 * kind of screen it finds (on for a built-in one, off when unsure). [inGame] is what the second
 * screen shows while a game runs.
 */
data class SecondScreenSettings(
    val enabled: Boolean? = null,
    val inGame: InGameSetting = InGameSetting.Clock,
)

/** What the second screen shows while a game runs: a dim clock and battery card, or nothing. */
enum class InGameSetting { Clock, Black }

/**
 * Settings › Advanced. [sounds] is how loud the soft interface clicks are ([SoundLevel]; Loud by default, as the old
 * switch was on by default). [vibration] is the light haptic tick on every move and a stronger one on confirm (on by
 * default). [usageStatistics] is off by default, the person switches it on; the
 * telemetry module follows it (app `graph/GraphTelemetry.kt`).
 */
data class AdvancedSettings(
    val animations: Boolean = true,
    val sounds: SoundLevel = SoundLevel.Loud,
    val usageStatistics: Boolean = false,
    val vibration: Boolean = true,
)

/**
 * Settings › Advanced › Sounds: Off, Quiet or Loud. [volume] is the share of the sound pool's volume each
 * click plays at. Stored by name; an older "on" switch reads as [Loud] and "off" as [Off] ([fromLegacySwitch]).
 */
enum class SoundLevel(val volume: Float) {
    Off(0f),
    Quiet(0.35f),
    Loud(1f),
    ;

    companion object {
        fun fromLegacySwitch(on: Boolean): SoundLevel = if (on) Loud else Off
    }
}

/**
 * Settings › About › Check for updates daily (on by default). When on, Plumemu
 * asks the update server for its manifest once a day, on an unmetered network, when it comes to the
 * front. The check's time and what it found are not settings: they live in
 * the app's own file and are never in a backup.
 */
data class UpdateSettings(
    val checkDaily: Boolean = true,
)

/** One-time steps: first-run setup finished, the "use as home screen?" question asked, the in-game card's note shown. */
data class SetupFlags(
    val setupCompleted: Boolean = false,
    val homeRoleAsked: Boolean = false,
    /**
     * Settings › Reset library forgot the games and the scan that reads the folders again has not
     * ended yet: the next start scans whatever Scan on startup says, so a process death between
     * the two never leaves the library empty.
     */
    val rescanAfterReset: Boolean = false,
    /**
     * The folder-matching version ([com.plumemu.library.matching.FOLDER_MATCH_VERSION]) the last
     * finished scan used; 0 before any. A lower value than the app's means the library was matched
     * by older rules, so the next scan lists every folder again.
     */
    val folderMatchVersion: Int = 0,
    /**
     * The naming-rules version (`com.plumemu.library.naming.NAMING_RULES_VERSION`) whose rows the
     * app last sent back to the naming pass; 0 before any. A lower value than the app's means
     * some rows were named by older rules (0.0.2-alpha's arcade rows without regions), so the next
     * scan marks them first and names them again from what is cached.
     */
    val namingRulesVersion: Int = 0,
    /**
     * The scrape-rules version (`com.plumemu.library.scrape.SCRAPE_RULES_VERSION`) whose wrong
     * ScreenScraper results the app last repaired; 0 before any. A lower value than the app's
     * means some stored matches were judged by older rules (an older scraper let a cartridge serial
     * match another system's game), so the next scan forgets them first and the box-art
     * run after it asks those games again from the scraper's cache.
     */
    val scrapeRulesVersion: Int = 0,
    /**
     * The second screen's in-game card has shown, once, why it moves ("Moves a little every minute
     * to protect the screen"): never again on this device.
     */
    val inGameDriftNoteShown: Boolean = false,
)
