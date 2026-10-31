package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreui.components.LocalScanPulsePhase
import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.setup.SetupFolder
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK

/** Settings as stored for the theme a shot is drawn in: Graphite with light text, or Paper with dark text. */
@Composable
internal fun settingsForShot(): PlumSettings {
    val isPaper = PlumTheme.colors.bg == BackgroundPreset.Paper.color.toComposeColor()
    val appearance =
        if (isPaper) AppearanceSettings(background = ColorSetting.Preset("Paper"), textColor = TextColorSetting.Dark) else AppearanceSettings(background = ColorSetting.Preset("Graphite"))
    return PlumSettings(appearance = appearance, flags = SetupFlags(setupCompleted = true))
}

/** Settings at [focus], drawn with no input attached (shots and bounds). */
@Composable
internal fun SettingsAt(
    focus: SettingsFocus,
    settings: PlumSettings = settingsForShot(),
    content: SettingsContent = SettingsContent(),
    /** A second Rescan press on a running scan has just been made (its notice shows). */
    busyPress: Boolean = false,
    wifi: Boolean = false,
    /** On the charger: the bolt before the battery percentage. */
    charging: Boolean = false,
    /** The version About shows. */
    version: String = TEST_VERSION,
    /** ROM folders › Skip a sub-folder…: the folder's sub-folders as read (null: it could not be read); unset, it is still reading. */
    subfolders: Result<List<String>?>? = null,
) {
    val model =
        remember(focus, content, busyPress, subfolders) {
            val actions = if (busyPress) BusyScanActions else subfolders?.let { ListingActions(it.getOrNull()) } ?: NoActions
            SettingsModel(actions, focus).also {
                it.content = content
                if (busyPress) it.work.rescan()
                focus.folderId?.takeIf { focus.page == PanelPage.SkipSubfolder }?.let(it.work::listSubfolders)
            }
        }
    // The scan dot is pinned half way through its breath so a shot is the same every time.
    CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
        SettingsLayout(settings, model, NoTouch, version, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY }, wifi = { wifi }, charging = { charging })
    }
}

/** Answers Skip a sub-folder…'s listing at once with [names]. */
private class ListingActions(private val listing: List<String>?) : SettingsActions by NoActions {
    override fun listSubfolders(
        folderId: Long,
        onDone: (List<String>?) -> Unit,
    ) = onDone(listing)
}

private object BusyScanActions : SettingsActions by NoActions {
    override fun rescanLibrary(): Boolean = false
}

internal const val TEST_VERSION = "0.0.2-alpha"

internal object NoActions : SettingsActions {
    override fun update(transform: (PlumSettings) -> PlumSettings) = Unit

    override fun resetAllSettings() = Unit

    override fun resetLibrary() = Unit

    override fun close() = Unit

    override fun openFolderPicker(): Boolean = true

    override fun removeFolder(
        folderId: Long,
        onDone: (Boolean) -> Unit,
    ) = Unit

    override fun rescanLibrary(): Boolean = true

    override fun forgetScanResult() = Unit

    override fun chooseEmulator(
        systemId: String,
        emulatorId: String?,
        onDone: (Boolean) -> Unit,
    ) = Unit

    override fun detectEmulators(onDone: (Boolean) -> Unit) = Unit

    override fun openHomeSettings(): Boolean = true

    override fun forgetFolderAnswer() = Unit

    override fun unhideGame(
        gameId: Long,
        onDone: (Boolean) -> Unit,
    ) = onDone(true)
}

internal val NoTouch = SettingsTouch({}, {}, {}, {}, {}, {}, {})

/** The focus on [option] of its section in the options column. */
internal fun optionsAt(option: SettingsOption): SettingsFocus {
    val section = SettingsSection.entries.first { option in it.options }
    return SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to section.options.indexOf(option)))
}

/** The focus inside [option]'s panel, on [page] item [index]. */
internal fun panelAt(
    option: SettingsOption,
    page: PanelPage,
    index: Int,
): SettingsFocus = optionsAt(option).copy(column = SettingsColumn.Panel, page = page, panelIndex = index)

/** The board's library (RP5-S1-settings): two folders, seven systems, PlayStation on DuckStation by default. */
internal object ShotContent {
    private val duck = EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)
    private val swan = EmulatorEntry("retroarch-swanstation", "SwanStation", isLegalRisk = false)
    private val beetle = EmulatorEntry("retroarch-mednafen-psx", "Beetle PSX", isLegalRisk = false)

    private fun system(
        id: String,
        name: String,
        emulator: String,
    ) = EmulatorEntry(id, emulator, isLegalRisk = false).let { SystemEmulators(id, name, automatic = it, chosen = null, installed = listOf(it)) }

    val library =
        SettingsContent(
            folders =
                listOf(
                    SetupFolder(1, "content://com.android.externalstorage.documents/tree/primary%3AROMs", "ROMs", 1031, 14, isCounted = true),
                    SetupFolder(2, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AROMs", "ROMs", 253, 4, isCounted = true),
                ),
            gameCount = 1284,
            systems =
                listOf(
                    system("snes", "Super Nintendo", "Snes9x"),
                    system("gba", "Game Boy Advance", "mGBA"),
                    SystemEmulators("psx", "PlayStation", automatic = duck, chosen = null, installed = listOf(duck, swan, beetle)),
                    system("psp", "PSP", "PPSSPP"),
                    system("nds", "Nintendo DS", "melonDS"),
                    system("gc", "GameCube", "Dolphin"),
                    SystemEmulators("megadrive", "Mega Drive", automatic = null, chosen = null, installed = emptyList()),
                ),
            emulatorAppCount = 9,
            isHomeApp = true,
        )

    /** Row of PlayStation in Emulators. */
    const val PSX_ROW = 2
}
