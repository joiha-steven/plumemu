package com.plumemu.coredata.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.Locale

/**
 * [PlumSettings] to and from the Preferences file. A missing key reads as its default; a value
 * this version does not know (written by a newer version, or damaged) reads as its default and
 * logs the key name, never the value.
 *
 * Key names are the file format: renaming one loses the person's choice.
 */
internal class SettingsCodec(private val log: SettingsLog) {
    fun decode(p: Preferences): PlumSettings {
        val d = PlumSettings.Defaults
        return PlumSettings(
            appearance = AppearanceSettings(
                background = color(p, Keys.background, d.appearance.background),
                textColor = textColor(p, d.appearance.textColor),
                accent = color(p, Keys.accent, d.appearance.accent, bySystem = true),
                font = enum(p, Keys.font, d.appearance.font),
                textSize = decodeTextSize(p),
                textWeight = optionalEnum<TextWeightSetting>(p, Keys.textWeight),
                leftEdge = optionalEnum<LeftEdgeSetting>(p, Keys.leftEdge),
                gridSize = enum(p, Keys.gridSize, d.appearance.gridSize),
                gridSystems = p[Keys.gridSystems] ?: d.appearance.gridSystems,
                ownColors = decodeOwnColors(p),
                buttonSet = p[Keys.buttonSet],
                language = p[Keys.language],
            ),
            controls = ControlsSettings(
                confirmButton = enum(p, Keys.confirmButton, d.controls.confirmButton),
                holdToScrollFaster = p[Keys.holdToScrollFaster] ?: d.controls.holdToScrollFaster,
                showButtonHints = p[Keys.showButtonHints] ?: d.controls.showButtonHints,
                keyboard = enum(p, Keys.keyboard, d.controls.keyboard),
                switchSystemWith = enum(p, Keys.switchSystemWith, d.controls.switchSystemWith),
                pushRightToPlay = p[Keys.pushRightToPlay] ?: d.controls.pushRightToPlay,
            ),
            library = LibrarySettings(
                scanOnStartup = p[Keys.scanOnStartup] ?: d.library.scanOnStartup,
                sortGames = enum(p, Keys.sortGames, d.library.sortGames),
                boxArtRegion = decodeBoxArtRegion(p, d.library.boxArtRegion),
                boxArtPaused = p[Keys.boxArtPaused] ?: d.library.boxArtPaused,
                systemOrder = decodeSystemOrder(p, d.library.systemOrder),
                manualSystemOrder = p[Keys.manualSystemOrder]?.let(::decodeSystemIds) ?: d.library.manualSystemOrder,
                hiddenSystems = p[Keys.hiddenSystems] ?: d.library.hiddenSystems,
                recentSize = enum(p, Keys.recentSize, d.library.recentSize),
            ),
            homeScreen = HomeScreenSettings(
                showApps = p[Keys.showApps] ?: d.homeScreen.showApps,
                showClock = p[Keys.showClock] ?: d.homeScreen.showClock,
                showBattery = p[Keys.showBattery] ?: d.homeScreen.showBattery,
                showSystemInfo = p[Keys.showSystemInfo],
            ),
            secondScreen = SecondScreenSettings(
                enabled = p[Keys.secondScreenEnabled],
                inGame = decodeInGame(p, d.secondScreen.inGame),
            ),
            advanced = AdvancedSettings(
                animations = p[Keys.animations] ?: d.advanced.animations,
                sounds = decodeSounds(p, d.advanced.sounds),
                // No stored key on an install that finished setup means it ran before the default turned off: it keeps the old "on".
                usageStatistics = p[Keys.usageStatistics] ?: (p[Keys.setupCompleted] == true || d.advanced.usageStatistics),
                vibration = p[Keys.vibration] ?: d.advanced.vibration,
            ),
            updates = UpdateSettings(
                checkDaily = p[Keys.checkUpdatesDaily] ?: d.updates.checkDaily,
            ),
            flags = SetupFlags(
                setupCompleted = p[Keys.setupCompleted] ?: d.flags.setupCompleted,
                homeRoleAsked = p[Keys.homeRoleAsked] ?: d.flags.homeRoleAsked,
                rescanAfterReset = p[Keys.rescanAfterReset] ?: d.flags.rescanAfterReset,
                folderMatchVersion = p[Keys.folderMatchVersion] ?: d.flags.folderMatchVersion,
                namingRulesVersion = p[Keys.namingRulesVersion] ?: d.flags.namingRulesVersion,
                scrapeRulesVersion = p[Keys.scrapeRulesVersion] ?: d.flags.scrapeRulesVersion,
                inGameDriftNoteShown = p[Keys.inGameDriftNoteShown] ?: d.flags.inGameDriftNoteShown,
            ),
        )
    }

    /** Writes every field the person has set, so the file says what they had even if a default changes later (text size excepted when never picked). */
    fun encode(s: PlumSettings, p: MutablePreferences) {
        p[Keys.background] = encodeColor(s.appearance.background)
        p[Keys.textColor] = s.appearance.textColor.stored
        p[Keys.accent] = encodeColor(s.appearance.accent)
        p[Keys.font] = s.appearance.font.name
        // Not picked yet is a missing key, so a later screen change still moves the default.
        p[Keys.textSizePickMarked] = true
        val textSize = s.appearance.textSize
        if (textSize == null) p.remove(Keys.textSize) else p[Keys.textSize] = textSize.name
        val textWeight = s.appearance.textWeight
        if (textWeight == null) p.remove(Keys.textWeight) else p[Keys.textWeight] = textWeight.name
        val leftEdge = s.appearance.leftEdge
        if (leftEdge == null) p.remove(Keys.leftEdge) else p[Keys.leftEdge] = leftEdge.name
        p[Keys.gridSize] = s.appearance.gridSize.name
        p[Keys.gridSystems] = s.appearance.gridSystems
        val ownColors = s.appearance.ownColors
        if (ownColors == null) p.remove(Keys.ownColors) else p[Keys.ownColors] = encodeOwnColors(ownColors)
        val buttonSet = s.appearance.buttonSet
        if (buttonSet == null) p.remove(Keys.buttonSet) else p[Keys.buttonSet] = buttonSet
        val language = s.appearance.language
        if (language == null) p.remove(Keys.language) else p[Keys.language] = language
        p[Keys.confirmButton] = s.controls.confirmButton.name
        p[Keys.holdToScrollFaster] = s.controls.holdToScrollFaster
        p[Keys.showButtonHints] = s.controls.showButtonHints
        p[Keys.keyboard] = s.controls.keyboard.name
        p[Keys.switchSystemWith] = s.controls.switchSystemWith.name
        p[Keys.pushRightToPlay] = s.controls.pushRightToPlay
        p[Keys.scanOnStartup] = s.library.scanOnStartup
        p[Keys.sortGames] = s.library.sortGames.name
        p[Keys.boxArtRegion] = s.library.boxArtRegion.code
        p[Keys.boxArtPaused] = s.library.boxArtPaused
        p[Keys.systemOrder] = encodeSystemOrder(s.library.systemOrder)
        p[Keys.manualSystemOrder] = encodeSystemIds(s.library.manualSystemOrder)
        p[Keys.hiddenSystems] = s.library.hiddenSystems
        p[Keys.recentSize] = s.library.recentSize.name
        p[Keys.showApps] = s.homeScreen.showApps
        p[Keys.showClock] = s.homeScreen.showClock
        p[Keys.showBattery] = s.homeScreen.showBattery
        val showSystemInfo = s.homeScreen.showSystemInfo
        if (showSystemInfo == null) p.remove(Keys.showSystemInfo) else p[Keys.showSystemInfo] = showSystemInfo
        // Not decided yet is a missing key, so the kind of screen found still decides.
        val secondScreenEnabled = s.secondScreen.enabled
        if (secondScreenEnabled == null) p.remove(Keys.secondScreenEnabled) else p[Keys.secondScreenEnabled] = secondScreenEnabled
        p[Keys.secondScreenInGame] = encodeInGame(s.secondScreen.inGame)
        p[Keys.animations] = s.advanced.animations
        p[Keys.soundLevel] = s.advanced.sounds.name.lowercase(Locale.ROOT)
        // The old on/off switch stays written beside the level, so an older build keeps Off (Quiet and Loud read as on there).
        p[Keys.sounds] = s.advanced.sounds != SoundLevel.Off
        p[Keys.vibration] = s.advanced.vibration
        p[Keys.usageStatistics] = s.advanced.usageStatistics
        p[Keys.checkUpdatesDaily] = s.updates.checkDaily
        p[Keys.setupCompleted] = s.flags.setupCompleted
        p[Keys.homeRoleAsked] = s.flags.homeRoleAsked
        p[Keys.rescanAfterReset] = s.flags.rescanAfterReset
        p[Keys.folderMatchVersion] = s.flags.folderMatchVersion
        p[Keys.namingRulesVersion] = s.flags.namingRulesVersion
        p[Keys.scrapeRulesVersion] = s.flags.scrapeRulesVersion
        p[Keys.inGameDriftNoteShown] = s.flags.inGameDriftNoteShown
    }

    private inline fun <reified E : Enum<E>> enum(p: Preferences, key: Preferences.Key<String>, default: E): E {
        val stored = p[key] ?: return default
        return enumValues<E>().firstOrNull { it.name == stored } ?: default.also { unknown(key) }
    }

    /**
     * Builds before the six sizes wrote every field, so each install that had saved anything holds
     * `Medium` without the person ever picking it. Without the marker (written by every save from
     * this version on) a stored `Medium` is that old default and reads as not picked, so the screen's
     * default applies once (so a device that never picked a size follows the screen's default). Small and Large
     * were always picks. With the marker every stored name is a pick, Medium included.
     */
    private fun decodeTextSize(p: Preferences): TextSizeSetting? {
        val stored = optionalEnum<TextSizeSetting>(p, Keys.textSize)
        val isFromOldBuild = p[Keys.textSizePickMarked] != true
        return if (isFromOldBuild && stored == TextSizeSetting.Medium) null else stored
    }

    /** A missing key, or a name this version does not know, is "not picked" (null). */
    private inline fun <reified E : Enum<E>> optionalEnum(p: Preferences, key: Preferences.Key<String>): E? {
        val stored = p[key] ?: return null
        return enumValues<E>().firstOrNull { it.name == stored } ?: null.also { unknown(key) }
    }

    /** The stored level; else the old on/off switch (on is Loud, off is Off); else [default]. No schema change: two keys, the new one wins. */
    private fun decodeSounds(p: Preferences, default: SoundLevel): SoundLevel {
        val stored = p[Keys.soundLevel]
        if (stored != null) return SoundLevel.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == stored } ?: default.also { unknown(Keys.soundLevel) }
        return p[Keys.sounds]?.let(SoundLevel::fromLegacySwitch) ?: default
    }

    /** Stored in lower case (`clock`, `black`). */
    private fun decodeInGame(p: Preferences, default: InGameSetting): InGameSetting {
        val stored = p[Keys.secondScreenInGame] ?: return default
        return InGameSetting.entries.firstOrNull { encodeInGame(it) == stored } ?: default.also { unknown(Keys.secondScreenInGame) }
    }

    /** Stored as ScreenScraper's region code (`us`, `eu`, `jp`, `wor`), the value the scraper is given. */
    private fun decodeBoxArtRegion(p: Preferences, default: BoxArtRegionSetting): BoxArtRegionSetting {
        val stored = p[Keys.boxArtRegion] ?: return default
        return BoxArtRegionSetting.entries.firstOrNull { it.code == stored } ?: default.also { unknown(Keys.boxArtRegion) }
    }

    private fun encodeInGame(value: InGameSetting): String = value.name.lowercase(Locale.ROOT)

    /** Stored in lower case (`name`, `maker`, `manual`), as the settings file spells them. */
    private fun decodeSystemOrder(
        p: Preferences,
        default: SystemOrderSetting,
    ): SystemOrderSetting {
        val stored = p[Keys.systemOrder] ?: return default
        return SystemOrderSetting.entries.firstOrNull { encodeSystemOrder(it) == stored } ?: default.also { unknown(Keys.systemOrder) }
    }

    private fun encodeSystemOrder(value: SystemOrderSetting): String = value.name.lowercase(Locale.ROOT)

    /** A missing key is "none" (older files); a damaged value is "none" too, and logged. */
    private fun decodeOwnColors(p: Preferences): OwnColors? {
        val stored = p[Keys.ownColors] ?: return null
        return decodeOwnColors(stored) ?: null.also { unknown(Keys.ownColors) }
    }

    private fun textColor(p: Preferences, default: TextColorSetting): TextColorSetting {
        val stored = p[Keys.textColor] ?: return default
        return TextColorSetting.fromStored(stored) ?: default.also { unknown(Keys.textColor) }
    }

    private fun color(p: Preferences, key: Preferences.Key<String>, default: ColorSetting, bySystem: Boolean = false): ColorSetting {
        val stored = p[key] ?: return default
        return decodeColor(stored, bySystem) ?: default.also { unknown(key) }
    }

    private fun unknown(key: Preferences.Key<*>) = log.warn("Setting ${key.name} has a value this version does not know; using the default")

    internal object Keys {
        val background = stringPreferencesKey("appearance.background")
        val textColor = stringPreferencesKey("appearance.text_color")
        val accent = stringPreferencesKey("appearance.accent")
        val font = stringPreferencesKey("appearance.font")
        val textSize = stringPreferencesKey("appearance.text_size")
        /** Set by every save from the six-size build on: from then a stored text size is a pick. */
        val textSizePickMarked = booleanPreferencesKey("appearance.text_size_pick_marked")
        val textWeight = stringPreferencesKey("appearance.text_weight")
        val leftEdge = stringPreferencesKey("appearance.left_edge")
        val gridSize = stringPreferencesKey("appearance.grid_size")
        /** System ids shown as a grid (a set: the order means nothing). Absent in older files: all lists. */
        val gridSystems = stringSetPreferencesKey("appearance.grid_systems")
        /** The person's own colours: `<background> <text color> <accent>` and, when kept with them, the button set then in use, e.g. `#1A2B3C Light Red Ivory` (an older 3-part value has none). Absent: none. */
        val ownColors = stringPreferencesKey("appearance.own_colors")
        /** Name of the colour set whose A/B/X/Y colours are in use, or `None`. Absent: not decided yet. */
        val buttonSet = stringPreferencesKey("appearance.button_set")
        val language = stringPreferencesKey("appearance.language")
        val confirmButton = stringPreferencesKey("controls.confirm_button")
        val holdToScrollFaster = booleanPreferencesKey("controls.hold_to_scroll_faster")
        val showButtonHints = booleanPreferencesKey("controls.show_button_hints")
        val keyboard = stringPreferencesKey("controls.keyboard")
        val switchSystemWith = stringPreferencesKey("controls.switch_system_with")
        val pushRightToPlay = booleanPreferencesKey("controls.push_right_to_play")
        val scanOnStartup = booleanPreferencesKey("library.scan_on_startup")
        val sortGames = stringPreferencesKey("library.sort_games")
        val boxArtRegion = stringPreferencesKey("library.box_art_region")
        val boxArtPaused = booleanPreferencesKey("library.box_art_paused")
        val systemOrder = stringPreferencesKey("library.system_order")
        /** System ids top to bottom, joined by [SYSTEM_ID_SEPARATOR] (a string set has no order). */
        val manualSystemOrder = stringPreferencesKey("library.system_order_manual")
        val hiddenSystems = stringSetPreferencesKey("library.hidden_systems")
        val recentSize = stringPreferencesKey("library.recent_size")
        val showApps = booleanPreferencesKey("home.show_apps")
        val showClock = booleanPreferencesKey("home.show_clock")
        val showBattery = booleanPreferencesKey("home.show_battery")
        val showSystemInfo = booleanPreferencesKey("home.show_system_info")
        /** Absent until the person decides: the app then follows the kind of screen. */
        val secondScreenEnabled = booleanPreferencesKey("second_screen.enabled")
        val secondScreenInGame = stringPreferencesKey("second_screen.in_game")
        val animations = booleanPreferencesKey("advanced.animations")
        /** The old Sounds switch; still read (on is Loud, off is Off) when [soundLevel] is absent. */
        val sounds = booleanPreferencesKey("advanced.sounds")
        val soundLevel = stringPreferencesKey("advanced.sound_level")
        val vibration = booleanPreferencesKey("advanced.vibration")
        val usageStatistics = booleanPreferencesKey("advanced.usage_statistics")
        val checkUpdatesDaily = booleanPreferencesKey("updates.check_daily")
        val setupCompleted = booleanPreferencesKey("flags.setup_completed")
        val homeRoleAsked = booleanPreferencesKey("flags.home_role_asked")
        val rescanAfterReset = booleanPreferencesKey("flags.rescan_after_reset")
        val folderMatchVersion = intPreferencesKey("flags.folder_match_version")
        val namingRulesVersion = intPreferencesKey("flags.naming_rules_version")
        val scrapeRulesVersion = intPreferencesKey("flags.scrape_rules_version")
        val inGameDriftNoteShown = booleanPreferencesKey("flags.in_game_drift_note_shown")
    }

    internal companion object {
        /** A preset is stored by name; a custom colour as `#RRGGBB`. */
        fun encodeColor(color: ColorSetting): String = when (color) {
            is ColorSetting.Preset -> color.name
            is ColorSetting.Custom -> String.format(Locale.ROOT, "#%06X", color.rgb and RGB_MASK)
            is ColorSetting.BySystem -> BY_SYSTEM_PREFIX + encodeColor(color.fallback)
        }

        /** Accent color's "By system": `BySystem:` and the accent it falls back to, e.g. `BySystem:Red` or `BySystem:#1A2B3C`. */
        private const val BY_SYSTEM_PREFIX = "BySystem:"

        /** [bySystem] is true only for the accent: no other setting has that choice, so there it reads as unknown. */
        fun decodeColor(stored: String, bySystem: Boolean = false): ColorSetting? = when {
            stored.startsWith(BY_SYSTEM_PREFIX) ->
                if (bySystem) decodeColor(stored.removePrefix(BY_SYSTEM_PREFIX))?.let { ColorSetting.BySystem(it) } else null
            stored.startsWith("#") ->
                if (stored.length == HEX_LENGTH && stored.drop(1).all { it in HEX_DIGITS }) {
                    ColorSetting.Custom(stored.drop(1).toInt(HEX_RADIX))
                } else {
                    null
                }
            stored.isNotBlank() && stored.all { it.isLetterOrDigit() } -> ColorSetting.Preset(stored)
            else -> null
        }

        /**
         * [OwnColors] in one value, so its three parts are written and read together: the background
         * and the accent as [encodeColor] spells them, the text colour as `Light`, `Dark` or `#RRGGBB`, one space between
         * (neither a preset name nor `#RRGGBB` holds a space).
         */
        fun encodeOwnColors(own: OwnColors): String =
            listOfNotNull(encodeColor(own.background), own.textColor.stored, encodeColor(own.accent), own.buttonSet)
                .joinToString(OWN_COLORS_SEPARATOR)

        /** Null unless [stored] is three valid parts, plus the kept button set's name when there are four. */
        fun decodeOwnColors(stored: String): OwnColors? {
            val parts = stored.trim().split(OWN_COLORS_SEPARATOR)
            if (parts.size != OWN_COLORS_PARTS && parts.size != OWN_COLORS_PARTS + 1) return null
            val background = decodeColor(parts[0]) ?: return null
            val textColor = TextColorSetting.fromStored(parts[1]) ?: return null
            val accent = decodeColor(parts[2], bySystem = true) ?: return null
            return OwnColors(background, textColor, accent, parts.getOrNull(OWN_COLORS_PARTS))
        }

        private const val OWN_COLORS_SEPARATOR = " "
        private const val OWN_COLORS_PARTS = 3

        /**
         * Between the ids of `library.system_order_manual`. System ids are the platform data's
         * (letters, digits, `-`) or a folder's name, which never holds a line break.
         */
        const val SYSTEM_ID_SEPARATOR = "\n"

        fun encodeSystemIds(ids: List<String>): String = ids.joinToString(SYSTEM_ID_SEPARATOR)

        /** Blank and repeated ids are dropped (a damaged file must not put one system in two places). */
        fun decodeSystemIds(stored: String): List<String> =
            stored.split(SYSTEM_ID_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        private const val RGB_MASK = 0xFFFFFF
        private const val HEX_LENGTH = 7
        private const val HEX_RADIX = 16
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"
    }
}
