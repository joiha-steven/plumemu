package com.plumemu.coredata.backup

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.OwnColors
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coredata.settings.SettingsCodec
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coredata.settings.UpdateSettings
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [PlumSettings] as the backup's `settings.json`: one
 * object per group of the model, readable and versioned, written from the model and not from the
 * DataStore file, so a restore can check every value and a later version can read an older file.
 *
 * Values are spelled as the DataStore file spells them (SettingsCodec): preset names or `#RRGGBB`,
 * enum names, the box art region's ScreenScraper code, the system order and the in-game card in
 * lower case. A missing key or a value this version does not know reads as its default, as the
 * DataStore file does; [decode] lists the keys it had to default in [Decoded.unknownKeys].
 */
object SettingsJson {
    /** Bump when a value changes meaning or a key is renamed; a new key with a default does not need it. */
    const val VERSION = 1

    fun encode(s: PlumSettings): JSONObject =
        JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(
                APPEARANCE,
                JSONObject()
                    .put("background", SettingsCodec.encodeColor(s.appearance.background))
                    .put("textColor", s.appearance.textColor.stored)
                    .put("accent", SettingsCodec.encodeColor(s.appearance.accent))
                    .put("font", s.appearance.font.name)
                    // Not picked yet stays "not picked", so the restored device's screen width still decides.
                    .put("textSize", s.appearance.textSize?.name ?: JSONObject.NULL)
                    .put("textWeight", s.appearance.textWeight?.name ?: JSONObject.NULL)
                    .put("leftEdge", s.appearance.leftEdge?.name ?: JSONObject.NULL)
                    .put("gridSize", s.appearance.gridSize.name)
                    // Sorted: a set has no order, and the same settings should give the same file.
                    .put("gridSystems", JSONArray(s.appearance.gridSystems.sorted()))
                    // None yet is null, so a restore clears whatever the device had.
                    .put("ownColors", s.appearance.ownColors?.let(::ownColorsJson) ?: JSONObject.NULL)
                    .put("buttonSet", s.appearance.buttonSet ?: JSONObject.NULL)
                    // Null follows the system's language, so a restore clears a language picked on this device.
                    .put("language", s.appearance.language ?: JSONObject.NULL),
            ).put(
                CONTROLS,
                JSONObject()
                    .put("confirmButton", s.controls.confirmButton.name)
                    .put("holdToScrollFaster", s.controls.holdToScrollFaster)
                    .put("showButtonHints", s.controls.showButtonHints)
                    .put("keyboard", s.controls.keyboard.name)
                    .put("switchSystemWith", s.controls.switchSystemWith.name)
                    .put("pushRightToPlay", s.controls.pushRightToPlay),
            ).put(
                LIBRARY,
                JSONObject()
                    .put("scanOnStartup", s.library.scanOnStartup)
                    .put("sortGames", s.library.sortGames.name)
                    .put("boxArtRegion", s.library.boxArtRegion.code)
                    .put("boxArtPaused", s.library.boxArtPaused)
                    .put("systemOrder", s.library.systemOrder.name.lowercase(Locale.ROOT))
                    .put("manualSystemOrder", JSONArray(s.library.manualSystemOrder))
                    // Sorted: a set has no order, and the same settings should give the same file.
                    .put("hiddenSystems", JSONArray(s.library.hiddenSystems.sorted()))
                    .put("recentSize", s.library.recentSize.name),
            ).put(
                HOME_SCREEN,
                JSONObject()
                    .put("showApps", s.homeScreen.showApps)
                    .put("showClock", s.homeScreen.showClock)
                    .put("showBattery", s.homeScreen.showBattery)
                    .put("showSystemInfo", s.homeScreen.showSystemInfo ?: JSONObject.NULL),
            ).put(
                SECOND_SCREEN,
                JSONObject()
                    .put("enabled", s.secondScreen.enabled ?: JSONObject.NULL)
                    .put("inGame", s.secondScreen.inGame.name.lowercase(Locale.ROOT)),
            ).put(
                ADVANCED,
                JSONObject()
                    .put("animations", s.advanced.animations)
                    .put("sounds", s.advanced.sounds.name.lowercase(Locale.ROOT))
                    .put("usageStatistics", s.advanced.usageStatistics)
                    .put("vibration", s.advanced.vibration),
            ).put(
                UPDATES,
                JSONObject()
                    .put("checkDaily", s.updates.checkDaily),
            ).put(
                FLAGS,
                JSONObject()
                    .put("setupCompleted", s.flags.setupCompleted)
                    .put("homeRoleAsked", s.flags.homeRoleAsked)
                    .put("rescanAfterReset", s.flags.rescanAfterReset)
                    .put("folderMatchVersion", s.flags.folderMatchVersion)
                    .put("namingRulesVersion", s.flags.namingRulesVersion)
                    .put("scrapeRulesVersion", s.flags.scrapeRulesVersion)
                    .put("inGameDriftNoteShown", s.flags.inGameDriftNoteShown),
            )

    /** What [decode] read, and the keys (`group.key`) whose value it did not know and so defaulted. */
    class Decoded(
        val settings: PlumSettings,
        val version: Int,
        val unknownKeys: List<String>,
    )

    /**
     * Reads a `settings.json`. Throws [BackupFormatException] when it is not one (not an object, no
     * version); a newer [VERSION] is read as far as it is understood, and the caller decides.
     */
    fun decode(text: String): Decoded {
        val root =
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw BackupFormatException("settings.json is not a JSON object", e)
            }
        if (!root.has(KEY_VERSION)) throw BackupFormatException("settings.json has no version")
        return Reader(root).read()
    }

    private class Reader(private val root: JSONObject) {
        private val unknown = mutableListOf<String>()
        private val d = PlumSettings.Defaults

        fun read(): Decoded {
            val version = root.optInt(KEY_VERSION, 0)
            val a = group(APPEARANCE)
            val c = group(CONTROLS)
            val l = group(LIBRARY)
            val h = group(HOME_SCREEN)
            val s = group(SECOND_SCREEN)
            val v = group(ADVANCED)
            // Backups made before updates existed have no group: the switch reads as on, its default.
            val u = group(UPDATES)
            val f = group(FLAGS)
            val settings =
                PlumSettings(
                    appearance =
                        AppearanceSettings(
                            background = color(a, APPEARANCE, "background", d.appearance.background),
                            textColor = textColor(a, APPEARANCE, "textColor", d.appearance.textColor),
                            accent = color(a, APPEARANCE, "accent", d.appearance.accent, bySystem = true),
                            font = enum(a, APPEARANCE, "font", d.appearance.font),
                            textSize = optionalEnum<TextSizeSetting>(a, APPEARANCE, "textSize"),
                            textWeight = optionalEnum<TextWeightSetting>(a, APPEARANCE, "textWeight"),
                            leftEdge = optionalEnum<LeftEdgeSetting>(a, APPEARANCE, "leftEdge"),
                            gridSize = enum(a, APPEARANCE, "gridSize", d.appearance.gridSize),
                            // Older backups have no key: every system reads as a list.
                            gridSystems = strings(a, APPEARANCE, "gridSystems")?.filter { it.isNotBlank() }?.toSet() ?: d.appearance.gridSystems,
                            // Older backups have no key: no own colours kept.
                            ownColors = ownColors(a, APPEARANCE, "ownColors"),
                            // Older backups have no key: not decided, the app settles it from the colours.
                            buttonSet = string(a, APPEARANCE, "buttonSet"),
                            // Older backups have no key: the system's language.
                            language = string(a, APPEARANCE, "language"),
                        ),
                    controls =
                        ControlsSettings(
                            confirmButton = enum(c, CONTROLS, "confirmButton", d.controls.confirmButton),
                            holdToScrollFaster = bool(c, CONTROLS, "holdToScrollFaster", d.controls.holdToScrollFaster),
                            showButtonHints = bool(c, CONTROLS, "showButtonHints", d.controls.showButtonHints),
                            keyboard = enum(c, CONTROLS, "keyboard", d.controls.keyboard),
                            // Older backups have no key: the default (L2 / R2).
                            switchSystemWith = enum(c, CONTROLS, "switchSystemWith", d.controls.switchSystemWith),
                            pushRightToPlay = bool(c, CONTROLS, "pushRightToPlay", d.controls.pushRightToPlay),
                        ),
                    library =
                        LibrarySettings(
                            scanOnStartup = bool(l, LIBRARY, "scanOnStartup", d.library.scanOnStartup),
                            sortGames = enum(l, LIBRARY, "sortGames", d.library.sortGames),
                            boxArtRegion = coded(l, LIBRARY, "boxArtRegion", BoxArtRegionSetting.entries, { it.code }, d.library.boxArtRegion),
                            boxArtPaused = bool(l, LIBRARY, "boxArtPaused", d.library.boxArtPaused),
                            systemOrder = coded(l, LIBRARY, "systemOrder", SystemOrderSetting.entries, { it.name.lowercase(Locale.ROOT) }, d.library.systemOrder),
                            // Blank and repeated ids dropped, as the DataStore file's reader does.
                            manualSystemOrder = strings(l, LIBRARY, "manualSystemOrder")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: d.library.manualSystemOrder,
                            hiddenSystems = strings(l, LIBRARY, "hiddenSystems")?.filter { it.isNotBlank() }?.toSet() ?: d.library.hiddenSystems,
                            recentSize = enum(l, LIBRARY, "recentSize", d.library.recentSize),
                        ),
                    homeScreen =
                        HomeScreenSettings(
                            showApps = bool(h, HOME_SCREEN, "showApps", d.homeScreen.showApps),
                            showClock = bool(h, HOME_SCREEN, "showClock", d.homeScreen.showClock),
                            showBattery = bool(h, HOME_SCREEN, "showBattery", d.homeScreen.showBattery),
                            // `returnHereAfterPlaying` in older files is ignored: the setting no longer exists.
                            showSystemInfo = optionalBool(h, HOME_SCREEN, "showSystemInfo"),
                        ),
                    secondScreen =
                        SecondScreenSettings(
                            enabled = optionalBool(s, SECOND_SCREEN, "enabled"),
                            inGame = coded(s, SECOND_SCREEN, "inGame", InGameSetting.entries, { it.name.lowercase(Locale.ROOT) }, d.secondScreen.inGame),
                        ),
                    advanced =
                        AdvancedSettings(
                            animations = bool(v, ADVANCED, "animations", d.advanced.animations),
                            sounds = sounds(v, d.advanced.sounds),
                            usageStatistics = bool(v, ADVANCED, "usageStatistics", d.advanced.usageStatistics),
                            vibration = bool(v, ADVANCED, "vibration", d.advanced.vibration),
                        ),
                    updates = UpdateSettings(checkDaily = bool(u, UPDATES, "checkDaily", d.updates.checkDaily)),
                    flags =
                        SetupFlags(
                            setupCompleted = bool(f, FLAGS, "setupCompleted", d.flags.setupCompleted),
                            homeRoleAsked = bool(f, FLAGS, "homeRoleAsked", d.flags.homeRoleAsked),
                            rescanAfterReset = bool(f, FLAGS, "rescanAfterReset", d.flags.rescanAfterReset),
                            folderMatchVersion = int(f, FLAGS, "folderMatchVersion", d.flags.folderMatchVersion),
                            namingRulesVersion = int(f, FLAGS, "namingRulesVersion", d.flags.namingRulesVersion),
                            scrapeRulesVersion = int(f, FLAGS, "scrapeRulesVersion", d.flags.scrapeRulesVersion),
                            inGameDriftNoteShown = bool(f, FLAGS, "inGameDriftNoteShown", d.flags.inGameDriftNoteShown),
                        ),
                )
            return Decoded(settings, version, unknown.toList())
        }

        /** "off" / "quiet" / "loud"; a backup made before the levels holds the old switch (true is Loud, false is Off). */
        private fun sounds(
            advanced: JSONObject?,
            default: SoundLevel,
        ): SoundLevel {
            if (advanced?.opt("sounds") is Boolean) return SoundLevel.fromLegacySwitch(advanced.getBoolean("sounds"))
            return coded(advanced, ADVANCED, "sounds", SoundLevel.entries, { it.name.lowercase(Locale.ROOT) }, default)
        }

        private fun group(name: String): JSONObject? {
            if (!root.has(name)) return null
            return root.optJSONObject(name) ?: null.also { unknown += name }
        }

        private fun bool(
            o: JSONObject?,
            group: String,
            key: String,
            default: Boolean,
        ): Boolean = optionalBool(o, group, key) ?: default

        private fun optionalBool(
            o: JSONObject?,
            group: String,
            key: String,
        ): Boolean? {
            if (o == null || !o.has(key) || o.isNull(key)) return null
            return o.opt(key) as? Boolean ?: null.also { unknown += "$group.$key" }
        }

        private fun int(
            o: JSONObject?,
            group: String,
            key: String,
            default: Int,
        ): Int {
            if (o == null || !o.has(key)) return default
            return (o.opt(key) as? Number)?.toInt() ?: default.also { unknown += "$group.$key" }
        }

        private fun string(
            o: JSONObject?,
            group: String,
            key: String,
        ): String? {
            if (o == null || !o.has(key) || o.isNull(key)) return null
            return o.opt(key) as? String ?: null.also { unknown += "$group.$key" }
        }

        private fun strings(
            o: JSONObject?,
            group: String,
            key: String,
        ): List<String>? {
            if (o == null || !o.has(key)) return null
            val array = o.optJSONArray(key) ?: return null.also { unknown += "$group.$key" }
            return List(array.length()) { array.opt(it) as? String }.filterNotNull()
        }

        /** Null when missing or null; a value that is not three valid parts is null too, and listed. */
        private fun ownColors(
            o: JSONObject?,
            group: String,
            key: String,
        ): OwnColors? {
            if (o == null || !o.has(key) || o.isNull(key)) return null
            val own = o.optJSONObject(key)
            val background = (own?.opt("background") as? String)?.let(SettingsCodec::decodeColor)
            val textColor = (own?.opt("textColor") as? String)?.let(TextColorSetting::fromStored)
            val accent = (own?.opt("accent") as? String)?.let { SettingsCodec.decodeColor(it, bySystem = true) }
            if (background == null || textColor == null || accent == null) return null.also { unknown += "$group.$key" }
            return OwnColors(background, textColor, accent, own?.opt("buttonSet") as? String)
        }

        private fun color(
            o: JSONObject?,
            group: String,
            key: String,
            default: ColorSetting,
            bySystem: Boolean = false,
        ): ColorSetting {
            val stored = string(o, group, key) ?: return default
            return SettingsCodec.decodeColor(stored, bySystem) ?: default.also { unknown += "$group.$key" }
        }

        private fun textColor(
            o: JSONObject?,
            group: String,
            key: String,
            default: TextColorSetting,
        ): TextColorSetting {
            val stored = string(o, group, key) ?: return default
            return TextColorSetting.fromStored(stored) ?: default.also { unknown += "$group.$key" }
        }

        private inline fun <reified E : Enum<E>> enum(
            o: JSONObject?,
            group: String,
            key: String,
            default: E,
        ): E = coded(o, group, key, enumValues<E>().toList(), { it.name }, default)

        private inline fun <reified E : Enum<E>> optionalEnum(
            o: JSONObject?,
            group: String,
            key: String,
        ): E? {
            val stored = string(o, group, key) ?: return null
            return enumValues<E>().firstOrNull { it.name == stored } ?: null.also { unknown += "$group.$key" }
        }

        private fun <E> coded(
            o: JSONObject?,
            group: String,
            key: String,
            values: List<E>,
            code: (E) -> String,
            default: E,
        ): E {
            val stored = string(o, group, key) ?: return default
            return values.firstOrNull { code(it) == stored } ?: default.also { unknown += "$group.$key" }
        }
    }

    private fun ownColorsJson(own: OwnColors): JSONObject =
        JSONObject()
            .put("background", SettingsCodec.encodeColor(own.background))
            .put("textColor", own.textColor.stored)
            .put("accent", SettingsCodec.encodeColor(own.accent))
            .put("buttonSet", own.buttonSet ?: JSONObject.NULL)

    private const val KEY_VERSION = "version"
    private const val APPEARANCE = "appearance"
    private const val CONTROLS = "controls"
    private const val LIBRARY = "library"
    private const val HOME_SCREEN = "homeScreen"
    private const val SECOND_SCREEN = "secondScreen"
    private const val ADVANCED = "advanced"
    private const val UPDATES = "updates"
    private const val FLAGS = "flags"
}

/** A backup file, or one of its entries, is not what this version can read. */
class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
