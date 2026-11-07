package com.plumemu.setup

/** Test tags of the setup screen. */
internal object SetupTags {
    const val SCREEN = "setup_screen"
    const val SCROLL = "setup_scroll"
    const val TITLE = "setup_title"
    const val DESCRIPTION = "setup_description"
    const val HINTS = "setup_hints"
    const val HINT_CONFIRM = "setup_hint_confirm"
    const val HINT_BACK = "setup_hint_back"
    const val FOLDER_CARD = "setup_folder_card"
    const val MESSAGE = "setup_message"
    const val PROGRESS = "setup_progress"
    const val SCAN_PULSE = "setup_scan_pulse"
    const val SCAN_LINE = "setup_scan_line"
    const val GAME_COUNT = "setup_game_count"
    const val SYSTEM_COUNT = "setup_system_count"
    const val NAMING_LINE = "setup_naming_line"
    const val NAMING_PULSE = "setup_naming_pulse"
    const val EMULATORS = "setup_emulators"
    const val USAGE_SWITCH = "setup_usage_switch"
    const val FOUND = "setup_found"
    const val DOT = "setup_dot"
    const val BOX_ART = "setup_box_art"
    const val LOOK_TILE = "setup_look_tile"
    const val BOX_ART_COMING = "setup_box_art_coming"
    const val NOTE = "setup_note"
    const val TRAY = "setup_keyboard_tray"
    const val TYPING_HINTS = "setup_typing_hints"

    fun control(control: SetupControl): String = "setup_control_${control.name}"
}
