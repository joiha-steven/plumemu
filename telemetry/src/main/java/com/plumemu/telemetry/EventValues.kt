package com.plumemu.telemetry

/**
 * The fixed values of the events' fields, each with the exact text the server's allow-list
 * expects (docs/conventions/telemetry.md). A value outside these cannot be written, so a free text
 * never reaches a prop by mistake.
 */
public interface WireValue {
    public val wire: String
}

/** Where the statistics switch was moved. */
public enum class SwitchPlace(override val wire: String) : WireValue {
    Setup("setup"),
    Settings("settings"),
}

/** `app_open.from`. */
public enum class OpenFrom(override val wire: String) : WireValue {
    HomeKey("home_key"),
    Icon("icon"),
    Update("update"),
    Other("other"),
}

/** `app_background.input`: what the person used while the app was in front. */
public enum class InputUsed(override val wire: String) : WireValue {
    Gamepad("gamepad"),
    Touch("touch"),
    Both("both"),
    None("none"),
}

/** `setup_step.step`: the steps of the app's setup, in order. */
public enum class SetupStepId(override val wire: String) : WireValue {
    Folders("folders"),
    SecondScreen("second_screen"),
    Artwork("artwork"),
    Scan("scan"),
    Ready("ready"),
}

/** `setup_step.action`. */
public enum class SetupAction(override val wire: String) : WireValue {
    Next("next"),
    Back("back"),
    Skip("skip"),
}

/** `library_scanned.trigger`. */
public enum class ScanTrigger(override val wire: String) : WireValue {
    Setup("setup"),
    Startup("startup"),
    Manual("manual"),
    FolderAdded("folder_added"),

    /** A check on coming back to the front, or on a card put in, found changed folders. */
    ChangeCheck("change_check"),
}

/** `library_scanned.result`. */
public enum class ScanResult(override val wire: String) : WireValue {
    Ok("ok"),
    FolderLost("folder_lost"),
    Error("error"),
}

/**
 * `screen_opened.screen` and `error_shown.where`. The Settings sections are listed one by one
 * (`settings_<section>`); a new section is added here and in the server's allow-list.
 */
public enum class ScreenId(override val wire: String) : WireValue {
    Home("home"),
    System("system"),
    Search("search"),
    Favorites("favorites"),
    Recent("recent"),
    Apps("apps"),
    Details("details"),
    GameMenu("game_menu"),
    SettingsLibrary("settings_library"),
    SettingsAppearance("settings_appearance"),
    SettingsEmulators("settings_emulators"),
    SettingsControls("settings_controls"),
    SettingsHomeScreen("settings_home_screen"),
    SettingsStatistics("settings_statistics"),
    SettingsAdvanced("settings_advanced"),
    SettingsAbout("settings_about"),
    Report("report"),
    Setup("setup"),
}

/** `view_changed.grid_size`. */
public enum class GridSize(override val wire: String) : WireValue {
    Small("small"),
    Medium("medium"),
    Large("large"),
}

/** `favorite_changed.change`. */
public enum class FavoriteChange(override val wire: String) : WireValue {
    Added("added"),
    Removed("removed"),
}

/** `game_launched.from`. */
public enum class LaunchFrom(override val wire: String) : WireValue {
    List("list"),
    Grid("grid"),
    Details("details"),
    Recent("recent"),
    Favorites("favorites"),
    Search("search"),
    SecondScreen("second_screen"),

    /** A on a most-played game in Settings › Statistics. */
    Statistics("statistics"),
}

/** `game_launched.result`. */
public enum class LaunchResult(override val wire: String) : WireValue {
    Ok("ok"),
    EmulatorMissing("emulator_missing"),

    /** Nothing was chosen for the system and several are installed: the person is asked to pick one. */
    EmulatorNotChosen("emulator_not_chosen"),
    FileMissing("file_missing"),
    Error("error"),
}

/** `emulator_picked.where`. */
public enum class PickPlace(override val wire: String) : WireValue {
    Setup("setup"),
    Settings("settings"),
    PerGame("per_game"),
}

/** `controller_connected.kind`. */
public enum class ControllerKind(override val wire: String) : WireValue {
    BuiltIn("built_in"),
    Xbox("xbox"),
    PlayStation("playstation"),
    Nintendo("nintendo"),
    Other("other"),
}

/** `update_check.trigger`. */
public enum class UpdateTrigger(override val wire: String) : WireValue {
    Daily("daily"),
    Manual("manual"),
}

/** `update_check.result`. */
public enum class UpdateCheckResult(override val wire: String) : WireValue {
    None("none"),
    Available("available"),
    Error("error"),
}

/** `backup_made.kind` and `restore_done.kind`. */
public enum class BackupKind(override val wire: String) : WireValue {
    Settings("settings"),
    Everything("everything"),
}

/** `backup_made.result` and `restore_done.result`. */
public enum class OkOrError(override val wire: String) : WireValue {
    Ok("ok"),
    Error("error"),
}

/**
 * The text of an app enum's entry as it is sent (`setting_changed`, `error_shown.code`):
 * `HomeScreen` -> `home_screen`, `SHOW_CLOCK` -> `show_clock`. Only enum entries are accepted,
 * so a colour value, a title or a message cannot be passed.
 */
internal fun Enum<*>.snakeName(): String {
    if (name.all { it.isUpperCase() || it.isDigit() || it == '_' }) return name.lowercase()
    val out = StringBuilder()
    name.forEachIndexed { index, ch ->
        if (ch.isUpperCase() && index > 0 && name[index - 1] != '_') out.append('_')
        out.append(ch.lowercaseChar())
    }
    return out.toString()
}
